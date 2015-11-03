/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.connector.deployers.ra.processors;

import static org.jboss.as.server.loaders.Utils.getResourceName;
import static org.jboss.as.server.loaders.Utils.getChildArchives;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.jboss.as.connector.logging.ConnectorLogger;
import org.jboss.as.server.loaders.ResourceLoader;
import org.jboss.as.server.loaders.ResourceLoaders;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ModuleRootMarker;
import org.jboss.as.server.deployment.module.ModuleSpecification;
import org.jboss.as.server.deployment.module.ResourceRoot;

/**
 * Deployment processor used to determine the structure of RAR deployments.
 *
 * @author John Bailey
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class RaStructureProcessor implements DeploymentUnitProcessor {

    static final String RAR_EXTENSION = ".rar";
    private static final String JAR_EXTENSION = ".jar";

    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final ResourceRoot resourceRoot = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT);
        if (resourceRoot == null) {
            return;
        }
        final ResourceLoader parentLoader = resourceRoot.getLoader();
        if (parentLoader == null) {
            return;
        }

        final String deploymentRootName = parentLoader.getRootName().toLowerCase(Locale.ENGLISH);
        if (!deploymentRootName.endsWith(RAR_EXTENSION)) {
            return;
        }

        final ModuleSpecification moduleSpecification = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION);
        moduleSpecification.setPublicModule(true);

        //this violates the spec, but everyone expects it to work
        ModuleRootMarker.mark(resourceRoot, true);
        try {
            final Collection<String> childArchives = getChildArchives(parentLoader, true, JAR_EXTENSION);
            String archiveName;
            for (final String archivePath : childArchives) {
                archiveName = getResourceName(archivePath);
                final ResourceLoader loader = ResourceLoaders.newResourceLoader(archiveName, resourceRoot.getLoader(), archivePath);
                // TODO: close loaders on cleanup in undeploy()
                final ResourceRoot childResource = new ResourceRoot(loader);
                ModuleRootMarker.mark(childResource);
                deploymentUnit.addToAttachmentList(Attachments.RESOURCE_ROOTS, childResource);
                resourceRoot.addToAttachmentList(Attachments.INDEX_IGNORE_PATHS, archivePath);
            }
        } catch (IOException e) {
            throw ConnectorLogger.ROOT_LOGGER.failedToProcessRaChild(e, parentLoader.getRootName());
        }
    }

    public void undeploy(DeploymentUnit context) {
        final List<ResourceRoot> childRoots = context.removeAttachment(Attachments.RESOURCE_ROOTS);
        if(childRoots != null) {
            for(ResourceRoot childRoot : childRoots) {
                safeClose(childRoot.getLoader());
            }
        }
    }

    static void safeClose(final AutoCloseable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception e) {
                ConnectorLogger.ROOT_LOGGER.trace("Failed to close resource", e);
            }
        }
    }

}
