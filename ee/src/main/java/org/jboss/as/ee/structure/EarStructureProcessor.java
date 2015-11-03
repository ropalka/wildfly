/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

package org.jboss.as.ee.structure;

import static org.jboss.as.server.loaders.Utils.getChildArchives;
import static org.jboss.as.server.loaders.Utils.getResourceName;
import static org.jboss.as.server.loaders.Utils.resourceOrPathExists;
import static org.jboss.modules.PathUtils.canonicalize;
import static org.jboss.modules.PathUtils.relativize;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jboss.as.ee.logging.EeLogger;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.SubDeploymentMarker;
import org.jboss.as.server.deployment.SubExplodedDeploymentMarker;
import org.jboss.as.server.deployment.module.ModuleRootMarker;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.as.server.loaders.ResourceLoader;
import org.jboss.as.server.loaders.ResourceLoaders;
import org.jboss.metadata.ear.spec.EarMetaData;
import org.jboss.metadata.ear.spec.ModuleMetaData;
import org.jboss.metadata.ear.spec.ModuleMetaData.ModuleType;
import org.jboss.modules.Resource;

/**
 * Deployment processor responsible for detecting EAR deployments and putting setting up the basic structure.
 *
 * @author John Bailey
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class EarStructureProcessor implements DeploymentUnitProcessor {

    private static final String JAR_EXTENSION = ".jar";
    private static final String WAR_EXTENSION = ".war";
    private static final String SAR_EXTENSION = ".sar";
    private static final String RAR_EXTENSION = ".rar";
    private static final String[] SUBDEPLOYMENT_EXTENSIONS = { JAR_EXTENSION, WAR_EXTENSION, SAR_EXTENSION, RAR_EXTENSION };
    private static final String DEFAULT_LIB_DIR = "lib";

    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        if (!DeploymentTypeMarker.isType(DeploymentType.EAR, deploymentUnit)) {
            return;
        }

        final ResourceRoot deploymentRoot = phaseContext.getDeploymentUnit().getAttachment(Attachments.DEPLOYMENT_ROOT);
        final ResourceLoader loader = deploymentRoot.getLoader();

        //  Make sure we don't index or add this as a module root
        deploymentRoot.putAttachment(Attachments.INDEX_RESOURCE_ROOT, false);
        ModuleRootMarker.mark(deploymentRoot, false);

        String libDirName = DEFAULT_LIB_DIR;
        //its possible that the ear metadata could come for jboss-app.xml
        final boolean appXmlPresent = loader.getResource("META-INF/application.xml") != null;
        final EarMetaData earMetaData = deploymentUnit.getAttachment(org.jboss.as.ee.structure.Attachments.EAR_METADATA);
        if (earMetaData != null) {
            final String xmlLibDirName = earMetaData.getLibraryDirectory();
            if (xmlLibDirName != null) {
                if (xmlLibDirName.length() == 1 && xmlLibDirName.charAt(0) == '/') {
                    throw EeLogger.ROOT_LOGGER.rootAsLibraryDirectory();
                }
                if (!xmlLibDirName.isEmpty()) {
                    libDirName = relativize(canonicalize(xmlLibDirName));
                    if (libDirName.endsWith("/")) libDirName = libDirName.substring(0, libDirName.length() - 1);
                }
            }
        }

        // Process all the children
        try {
            // process the lib directory
            if (loader.getPaths().contains(libDirName)) {
                final Collection<String> libArchives = getChildArchives(loader, libDirName, false, SUBDEPLOYMENT_EXTENSIONS);
                for (final String child : libArchives) {
                    final ResourceLoader childLoader = ResourceLoaders.newResourceLoader(getResourceName(child), deploymentRoot.getLoader(), child);
                    final ResourceRoot childResource = new ResourceRoot(childLoader, null, null);
                    if (getResourceName(child).toLowerCase(Locale.ENGLISH).endsWith(JAR_EXTENSION)) {
                        ModuleRootMarker.mark(childResource);
                        deploymentUnit.addToAttachmentList(Attachments.RESOURCE_ROOTS, childResource);
                    }
                }
            }
            // scan the ear looking for wars and jars
            final Collection<String> childArchives = getChildArchives(loader, true, SUBDEPLOYMENT_EXTENSIONS);

            // if there is no application.xml then look in the ear root for modules
            if (!appXmlPresent) {
                for (final String child : childArchives) {
                    if (child.startsWith(libDirName + "/")) continue;
                    final boolean isWarFile = getResourceName(child).toLowerCase(Locale.ENGLISH).endsWith(WAR_EXTENSION);
                    final boolean isRarFile = getResourceName(child).toLowerCase(Locale.ENGLISH).endsWith(RAR_EXTENSION);
                    this.createResourceRoot(deploymentRoot, deploymentUnit, child, isWarFile || isRarFile);
                }
            } else {
                final Set<String> subDeploymentFiles = new HashSet<>();
                // otherwise read from application.xml
                for (final ModuleMetaData module : earMetaData.getModules()) {
                    final String childArchiveName = module.getFileName();
                    if (module.getFileName().endsWith(".xml")) {
                        throw EeLogger.ROOT_LOGGER.unsupportedModuleType(childArchiveName);
                    }

                    if (!resourceOrPathExists(loader, childArchiveName)) {
                        throw EeLogger.ROOT_LOGGER.cannotProcessEarModule(loader.getRootName(), childArchiveName);
                    }

                    if (childArchiveName.startsWith(libDirName)) {
                        throw EeLogger.ROOT_LOGGER.earModuleChildOfLibraryDirectory(libDirName, module.getFileName());
                    }

                    // maintain this in a collection of subdeployment virtual files, to be used later
                    subDeploymentFiles.add(childArchiveName);

                    final boolean webArchive = module.getType() == ModuleType.Web;
                    final ResourceRoot childResource = this.createResourceRoot(deploymentRoot, deploymentUnit, childArchiveName, true);
                    childResource.putAttachment(org.jboss.as.ee.structure.Attachments.MODULE_META_DATA, module);

                    if (!webArchive) {
                        ModuleRootMarker.mark(childResource);
                    }

                    final String alternativeDD = module.getAlternativeDD();
                    if (alternativeDD != null && alternativeDD.trim().length() > 0) {
                        final Resource alternateDeploymentDescriptor = deploymentRoot.getLoader().getResource(alternativeDD);
                        if (alternateDeploymentDescriptor == null) {
                            throw EeLogger.ROOT_LOGGER.alternateDeploymentDescriptor(alternativeDD, childArchiveName);
                        }
                        switch (module.getType()) {
                            case Client:
                                childResource.putAttachment(org.jboss.as.ee.structure.Attachments.ALTERNATE_CLIENT_DEPLOYMENT_DESCRIPTOR, alternateDeploymentDescriptor);
                                break;
                            case Connector:
                                childResource.putAttachment(org.jboss.as.ee.structure.Attachments.ALTERNATE_CONNECTOR_DEPLOYMENT_DESCRIPTOR, alternateDeploymentDescriptor);
                                break;
                            case Ejb:
                                childResource.putAttachment(org.jboss.as.ee.structure.Attachments.ALTERNATE_EJB_DEPLOYMENT_DESCRIPTOR, alternateDeploymentDescriptor);
                                break;
                            case Web:
                                childResource.putAttachment(org.jboss.as.ee.structure.Attachments.ALTERNATE_WEB_DEPLOYMENT_DESCRIPTOR, alternateDeploymentDescriptor);
                                break;
                            case Service:
                                throw EeLogger.ROOT_LOGGER.unsupportedModuleType(module.getFileName());

                        }
                    }
                }
                // now check the rest of the archive for any other jar/sar files
                for (final String child : childArchives) {
                    if (child.startsWith(libDirName + "/")) continue;
                    if (subDeploymentFiles.contains(child)) continue;
                    final String fileName = getResourceName(child).toLowerCase(Locale.ENGLISH);
                    if (fileName.endsWith(SAR_EXTENSION) || fileName.endsWith(JAR_EXTENSION)) {
                        this.createResourceRoot(deploymentRoot, deploymentUnit, child, false);
                    }
                }
            }

        } catch (IOException e) {
            throw EeLogger.ROOT_LOGGER.failedToProcessChild(e, loader.getRootName());
        }
    }

    /**
     * Creates a {@link ResourceRoot} for the passed {@link String file} and adds it to the list of {@link ResourceRoot}s
     * in the {@link DeploymentUnit deploymentUnit}
     *
     * @param deploymentRoot      The deployment resource root
     * @param deploymentUnit      The deployment unit
     * @param file                The file for which the resource root will be created
     * @param markAsSubDeployment If this is true, then the {@link ResourceRoot} that is created will be marked as a subdeployment
     *                            through a call to {@link SubDeploymentMarker#mark(org.jboss.as.server.deployment.module.ResourceRoot)}
     * @return Returns the created {@link ResourceRoot}
     * @throws IOException
     */
    private ResourceRoot createResourceRoot(final ResourceRoot deploymentRoot, final DeploymentUnit deploymentUnit, final String file, final boolean markAsSubDeployment) throws IOException {
        final ResourceLoader loader = ResourceLoaders.newResourceLoader(getResourceName(file), deploymentRoot.getLoader(), file);
        final ResourceRoot resourceRoot = new ResourceRoot(loader, null, null);
        deploymentUnit.addToAttachmentList(Attachments.RESOURCE_ROOTS, resourceRoot);
        if (markAsSubDeployment) {
            SubDeploymentMarker.mark(resourceRoot);
        }
        final boolean war = getResourceName(file).toLowerCase(Locale.ENGLISH).endsWith(WAR_EXTENSION);
        if (war) {
            resourceRoot.putAttachment(Attachments.INDEX_RESOURCE_ROOT, false);
            SubExplodedDeploymentMarker.mark(resourceRoot);
        }
        return resourceRoot;
    }

    public static void safeClose(final Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception e) {
                EeLogger.ROOT_LOGGER.trace("Failed to close resource", e);
            }
        }
    }

    public void undeploy(DeploymentUnit context) {
        final List<ResourceRoot> children = context.removeAttachment(Attachments.RESOURCE_ROOTS);
        if (children != null) {
            for (ResourceRoot childRoot : children) {
                safeClose(childRoot.getMountHandle());
            }
        }
    }
}
