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

package org.jboss.as.connector.deployers.ra.processors;

import static org.jboss.as.connector.logging.ConnectorLogger.ROOT_LOGGER;
import static org.wildfly.loaders.Utils.getResourceName;

import java.io.File;
import java.util.Iterator;
import java.util.Locale;

import org.jboss.as.connector.logging.ConnectorLogger;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.modules.Resource;
import org.wildfly.loaders.ResourceLoader;

/**
 * Load native libraries for .rar deployments
 *
 * @author <a href="mailto:jesper.pedersen@jboss.org">Jesper Pedersen</a>
 * @author <a href="mailto:ropalka@jboss.org">Richard Opalka</a>
 */
public class RaNativeProcessor implements DeploymentUnitProcessor {

    /**
     * Construct a new instance.
     */
    public RaNativeProcessor() {
    }

    /**
     * Process a deployment for standard ra deployment files. Will parse the xml
     * file and attach a configuration discovered during processing.
     * @param phaseContext the deployment unit context
     * @throws DeploymentUnitProcessingException
     */
    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        process(phaseContext.getDeploymentUnit().getAttachment(Attachments.DEPLOYMENT_ROOT).getLoader());
    }

    public static void process(ResourceLoader loader) throws DeploymentUnitProcessingException {
        final String deploymentRootName = loader.getRootName().toLowerCase(Locale.ENGLISH);
        if (!deploymentRootName.endsWith(RaStructureProcessor.RAR_EXTENSION)) {
            return;
        }
        try {
            final Iterator<Resource> resources = loader.iterateResources("", true);
            Resource resource;
            String nativeFileAbsPath;
            while (resources.hasNext()) {
                resource = resources.next();
                if (isNativeLibrary(resource)) {
                    ROOT_LOGGER.tracef("Processing library: %s", resource.getName());
                    // precondition RAR archives are exploded
                    nativeFileAbsPath = resource.getURL().getFile();
                    try {
                        System.load(new File(nativeFileAbsPath).getAbsolutePath());
                        ROOT_LOGGER.debugf("Loaded library: %s", nativeFileAbsPath);
                    } catch (Throwable t) {
                        ROOT_LOGGER.debugf("Unable to load library: %s", nativeFileAbsPath);
                    }
                }
            }
        } catch (Exception e) {
            throw ConnectorLogger.ROOT_LOGGER.failedToLoadNativeLibraries(e);
        }
    }

    public void undeploy(final DeploymentUnit context) {
    }

    private static boolean isNativeLibrary(final Resource resource) {
        final String resourceName = getResourceName(resource.getName()).toLowerCase(Locale.ENGLISH);
        return resourceName.endsWith(".a") || resourceName.endsWith(".so") || resourceName.endsWith(".dll");
    }

}
