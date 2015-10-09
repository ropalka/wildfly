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
package org.jboss.as.ee.structure;

import java.util.List;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.SubDeploymentMarker;
import org.jboss.as.server.deployment.module.ModuleRootMarker;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;

/**
 * Processor that only runs for ear deployments where no application.xml is provided. It examines jars in the ear to determine
 * which are EJB sub-deployments.
 * <p/>
 * TODO: Move this to the EJB subsystem.
 *
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class EjbJarDeploymentProcessor implements DeploymentUnitProcessor {

    private static final DotName STATELESS = DotName.createSimple("javax.ejb.Stateless");
    private static final DotName STATEFUL = DotName.createSimple("javax.ejb.Stateful");
    private static final DotName MESSAGE_DRIVEN = DotName.createSimple("javax.ejb.MessageDriven");
    private static final DotName SINGLETON = DotName.createSimple("javax.ejb.Singleton");
    private static final String EJB_JAR_XML = "META-INF/ejb-jar.xml";
    private static final String APPLICATION_XML = "META-INF/application.xml";

    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final ResourceRoot deploymentRoot = deploymentUnit.getAttachment(org.jboss.as.server.deployment.Attachments.DEPLOYMENT_ROOT);
        if (!DeploymentTypeMarker.isType(DeploymentType.EAR, deploymentUnit)) {
            return;
        }
        //we don't check for the metadata attachment
        //cause this could come from a jboss-app.xml instead
        if (deploymentRoot.getLoader().getResource(APPLICATION_XML) != null) {
            //if we have an application.xml we don't scan
            return;
        }
        // TODO: deal with application clients, we need the manifest information
        final List<ResourceRoot> potentialSubDeployments = deploymentUnit.getAttachmentList(Attachments.RESOURCE_ROOTS);
        for (final ResourceRoot resourceRoot : potentialSubDeployments) {
            if (ModuleRootMarker.isModuleRoot(resourceRoot)) {
                // module roots cannot be ejb jars
                continue;
            }
            if (resourceRoot.getLoader().getResource(EJB_JAR_XML) != null) {
                SubDeploymentMarker.mark(resourceRoot);
                ModuleRootMarker.mark(resourceRoot);
            } else {
                final Index index = resourceRoot.getAttachment(Attachments.ANNOTATION_INDEX);
                if (containsEjbAnnotations(index)) {
                    //this is an EJB deployment
                    SubDeploymentMarker.mark(resourceRoot);
                    ModuleRootMarker.mark(resourceRoot);
                }
            }
        }
    }

    @Override
    public void undeploy(final DeploymentUnit context) {
    }

    private static boolean containsEjbAnnotations(final Index index) {
        if (index == null) return false;
        if (!index.getAnnotations(STATEFUL).isEmpty()) return true;
        if (!index.getAnnotations(STATELESS).isEmpty()) return true;
        if (!index.getAnnotations(MESSAGE_DRIVEN).isEmpty()) return true;
        if (!index.getAnnotations(SINGLETON).isEmpty()) return true;
        return false;
    }

}
