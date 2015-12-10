/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.extension.undertow.deployment;

import static org.wildfly.loaders.deployment.Utils.getChildArchives;
import static org.wildfly.loaders.deployment.Utils.getResourceName;

import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.undertow.util.FileUtils;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.ee.structure.DeploymentType;
import org.jboss.as.ee.structure.DeploymentTypeMarker;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.PrivateSubDeploymentMarker;
import org.jboss.as.server.deployment.module.FilterSpecification;
import org.jboss.as.server.deployment.module.ModuleRootMarker;
import org.jboss.as.server.deployment.module.ModuleSpecification;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.as.web.common.SharedTldsMetaDataBuilder;
import org.jboss.as.web.common.WarMetaData;
import org.jboss.modules.filter.PathFilters;
import org.jboss.modules.security.ImmediatePermissionFactory;
import org.jboss.modules.Resource;
import org.wildfly.extension.undertow.logging.UndertowLogger;
import org.wildfly.loaders.deployment.ResourceLoader;
import org.wildfly.loaders.deployment.ResourceLoaders;

/**
 * Create and mount classpath entries in the .war deployment.
 *
 * @author Emanuel Muckenhuber
 * @author Thomas.Diesler@jboss.com
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class WarStructureDeploymentProcessor implements DeploymentUnitProcessor {

    private static final String TEMP_DIR = "jboss.server.temp.dir";
    private static final String WEB_INF_LIB = "WEB-INF/lib";
    private static final String WEB_INF_CLASSES = "WEB-INF/classes";
    private static final String WEB_INF_EXTERNAL_MOUNTS = "WEB-INF/undertow-external-mounts.conf";
    private static final String JAR_EXTENSION = ".jar";

    private final SharedTldsMetaDataBuilder sharedTldsMetaData;

    public WarStructureDeploymentProcessor(final SharedTldsMetaDataBuilder sharedTldsMetaData) {
        this.sharedTldsMetaData = sharedTldsMetaData;
    }

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        if (!DeploymentTypeMarker.isType(DeploymentType.WAR, deploymentUnit)) {
            return; // Skip non web deployments
        }

        final ResourceRoot deploymentResourceRoot = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT);
        final ResourceLoader loader = deploymentResourceRoot.getLoader();

        // set the child first behaviour
        final ModuleSpecification moduleSpecification = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION);
        if (moduleSpecification == null) {
            return;
        }
        moduleSpecification.setPrivateModule(true);

        // other sub deployments should not have access to classes in the war module
        PrivateSubDeploymentMarker.mark(deploymentUnit);

        // OSGi WebApp deployments (WAB) may use the deployment root if they don't use WEB-INF/classes already
        if (!deploymentUnit.hasAttachment(Attachments.OSGI_MANIFEST) || loader.getPaths().contains(WEB_INF_CLASSES)) {
            // we do not want to index the resource root, only WEB-INF/classes and WEB-INF/lib
            deploymentResourceRoot.putAttachment(Attachments.INDEX_RESOURCE_ROOT, false);

            // Make sure the root does not end up in the module, only META-INF
            deploymentResourceRoot.getExportFilters().add(new FilterSpecification(PathFilters.getMetaInfFilter(), true));
            deploymentResourceRoot.getExportFilters().add(new FilterSpecification(PathFilters.getMetaInfSubdirectoriesFilter(), true));
            deploymentResourceRoot.getExportFilters().add(new FilterSpecification(PathFilters.acceptAll(), false));
            ModuleRootMarker.mark(deploymentResourceRoot, true);
        }

        try {
            // add standard resource roots, this should eventually replace ClassPathEntry
            final List<ResourceRoot> resourceRoots = createResourceRoots(deploymentResourceRoot);
            for (ResourceRoot root : resourceRoots) {
                deploymentUnit.addToAttachmentList(Attachments.RESOURCE_ROOTS, root);
            }
        } catch (Exception e) {
            throw new DeploymentUnitProcessingException(e);
        }
        // Add the war metadata
        final WarMetaData warMetaData = new WarMetaData();
        deploymentUnit.putAttachment(WarMetaData.ATTACHMENT_KEY, warMetaData);

        String deploymentName;
        if(deploymentUnit.getParent() == null) {
            deploymentName = deploymentUnit.getName();
        } else {
            deploymentName = deploymentUnit.getParent().getName() + "." + deploymentUnit.getName();
        }

        PathManager pathManager = deploymentUnit.getAttachment(Attachments.PATH_MANAGER);

        File tempDir = new File(pathManager.getPathEntry(TEMP_DIR).resolvePath(), deploymentName);
        tempDir.mkdirs();
        warMetaData.setTempDir(tempDir);

        moduleSpecification.addPermissionFactory(new ImmediatePermissionFactory(new FilePermission(tempDir.getAbsolutePath() + File.separatorChar + "-", "read,write,delete")));

        // Add the shared TLDs metadata
        final TldsMetaData tldsMetaData = new TldsMetaData();
        tldsMetaData.setSharedTlds(sharedTldsMetaData);
        deploymentUnit.putAttachment(TldsMetaData.ATTACHMENT_KEY, tldsMetaData);

        processExternalMounts(deploymentUnit, loader);
    }

    private void processExternalMounts(final DeploymentUnit deploymentUnit, final ResourceLoader loader) throws DeploymentUnitProcessingException {
        Resource mounts = loader.getResource(WEB_INF_EXTERNAL_MOUNTS);
        if (mounts == null) {
            return;
        }
        try (InputStream data = mounts.openStream()) {
            String contents = FileUtils.readFile(data);
            String[] lines = contents.split("\n");
            for(String line : lines) {
                String trimmed = line;
                int commentIndex = trimmed.indexOf("#");
                if(commentIndex > -1) {
                    trimmed = trimmed.substring(0, commentIndex);
                }
                trimmed = trimmed.trim();
                if(trimmed.isEmpty()) {
                    continue;
                }
                File path = new File(trimmed);
                if(path.exists()) {
                    deploymentUnit.addToAttachmentList(UndertowAttachments.EXTERNAL_RESOURCES, path);
                } else {
                    throw UndertowLogger.ROOT_LOGGER.couldNotFindExternalPath(path);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void undeploy(final DeploymentUnit context) {
    }

    /**
     * Create the resource roots for a .war deployment
     *
     * @param resourceRoot the deployment resource root
     * @return the resource roots
     * @throws java.io.IOException for any error
     */
    private List<ResourceRoot> createResourceRoots(final ResourceRoot resourceRoot) throws IOException, DeploymentUnitProcessingException {
        final ResourceLoader rootLoader = resourceRoot.getLoader();
        final List<ResourceRoot> entries = new ArrayList<>();
        // WEB-INF classes
        if (rootLoader.getPaths().contains(WEB_INF_CLASSES)) {
            final ResourceLoader loader = ResourceLoaders.newResourceLoader(getResourceName(WEB_INF_CLASSES), resourceRoot.getLoader(), WEB_INF_CLASSES, true);
            final ResourceRoot webInfClassesRoot = new ResourceRoot(loader);
            ModuleRootMarker.mark(webInfClassesRoot);
            entries.add(webInfClassesRoot);
        }
        // WEB-INF lib
        if (rootLoader.getPaths().contains(WEB_INF_LIB)) {
            final Collection<String> archives = getChildArchives(rootLoader, WEB_INF_LIB, false, JAR_EXTENSION);
            for (final String archive : archives) {
                try {
                    final ResourceLoader loader = ResourceLoaders.newResourceLoader(getResourceName(archive), resourceRoot.getLoader(), archive, true);
                    final ResourceRoot webInfArchiveRoot = new ResourceRoot(loader);
                    ModuleRootMarker.mark(webInfArchiveRoot);
                    entries.add(webInfArchiveRoot);
                } catch (IOException e) {
                    throw new DeploymentUnitProcessingException(UndertowLogger.ROOT_LOGGER.failToProcessWebInfLib(archive), e);
                }
            }
        }
        return entries;
    }

}
