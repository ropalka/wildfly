/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.jboss.as.appclient.deployment;

import static org.jboss.modules.PathUtils.canonicalize;
import static org.jboss.modules.PathUtils.relativize;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.jboss.as.appclient.logging.AppClientLogger;
import org.jboss.as.ee.structure.DeploymentType;
import org.jboss.as.ee.structure.DeploymentTypeMarker;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.SubDeploymentMarker;
import org.jboss.as.server.deployment.module.ModuleRootMarker;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.as.server.loaders.ResourceLoader;
import org.jboss.as.server.loaders.ResourceLoaders;
import org.jboss.modules.Resource;

/**
 * Processor that marks a sub-deployment as an application client based on the parameters passed on the command line
 *
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class ApplicationClientStructureProcessor implements DeploymentUnitProcessor {

    private static final String EAR_EXTENSION = ".ear";
    private static final String JAR_EXTENSION = ".jar";
    private final String deployment;

    public ApplicationClientStructureProcessor(final String deployment) {
        final String canonPath = relativize(canonicalize(deployment));
        this.deployment = canonPath.endsWith("/") ? canonPath.substring(canonPath.length() - 1) : canonPath;
    }

    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final ResourceRoot root = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT);
        String deploymentUnitName = deploymentUnit.getName().toLowerCase(Locale.ENGLISH);
        if (deploymentUnitName.endsWith(EAR_EXTENSION)) {
            final Map<String, ResourceRoot> existing = new HashMap<>();
            for (final ResourceRoot additional : deploymentUnit.getAttachmentList(Attachments.RESOURCE_ROOTS)) {
                existing.put(additional.getLoader().getPath(), additional);
            }

            final Resource appClientRoot = root.getLoader().getResource(deployment);
            if (appClientRoot != null) {
                if (existing.containsKey(appClientRoot.getName())) {
                    final ResourceRoot existingRoot = existing.get(appClientRoot);
                    SubDeploymentMarker.mark(existingRoot);
                    ModuleRootMarker.mark(existingRoot);
                } else {
                    ResourceLoader loader;
                    try {
                        loader = ResourceLoaders.newResourceLoader(appClientRoot.getName(), root.getLoader(), deployment);
                    } catch (IOException e) {
                        throw AppClientLogger.ROOT_LOGGER.unableToReadAppclientResource(deployment, e);
                    }
                    final ResourceRoot childResource = new ResourceRoot(loader, null, null);
                    ModuleRootMarker.mark(childResource);
                    SubDeploymentMarker.mark(childResource);
                    deploymentUnit.addToAttachmentList(Attachments.RESOURCE_ROOTS, childResource);
                }

            } else {
                throw AppClientLogger.ROOT_LOGGER.cannotFindAppClient(deployment);
            }
        } else if (deploymentUnit.getParent() != null && deploymentUnitName.endsWith(JAR_EXTENSION)) {
            if (root.getLoader().getPath().equals(deployment)) {
                DeploymentTypeMarker.setType(DeploymentType.APPLICATION_CLIENT, deploymentUnit);
            }

        }
    }

    @Override
    public void undeploy(final DeploymentUnit context) {
    }

}
