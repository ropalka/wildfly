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

package org.jboss.as.ejb3.deployment.processors.security;

import javax.security.jacc.PolicyConfiguration;

import org.jboss.as.ejb3.deployment.EjbSecurityDeployer;
import org.jboss.as.security.deployment.AbstractSecurityDeployer;
import org.jboss.as.security.deployment.SecurityAttachments;
import org.jboss.as.security.service.JaccService;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A {@code DeploymentUnitProcessor} for JACC policies.
 *
 * @author Marcus Moyses
 * @author Anil Saldhana
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class JaccEjbDeploymentProcessor implements DeploymentUnitProcessor {

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        boolean securityEnabled = deploymentUnit.hasAttachment(SecurityAttachments.SECURITY_ENABLED);
        if(!securityEnabled) {
            return;
        }
        final AbstractSecurityDeployer<?> deployer = new EjbSecurityDeployer();
        final DeploymentUnit parentDU = deploymentUnit.getParent();
        // EJBs maybe included directly in war deployment
        final ServiceName jaccServiceName = getJaccServiceName(deploymentUnit);
        final ServiceTarget serviceTarget = phaseContext.getServiceTarget();
        final ServiceBuilder<?> sb = serviceTarget.addService(jaccServiceName);
        final Consumer<PolicyConfiguration> pcConsumer = sb.provides(jaccServiceName);
        final Supplier<PolicyConfiguration> parentpcSupplier = parentDU != null ? sb.requires(parentDU.getServiceName().append(JaccService.SERVICE_NAME)) : null;
        final JaccService<?> service = deployer.deploy(pcConsumer, parentpcSupplier, deploymentUnit);
        sb.setInstance(service);
        sb.install();
    }

    @Override
    public void undeploy(DeploymentUnit deploymentUnit) {
        AbstractSecurityDeployer<?> deployer = new EjbSecurityDeployer();
        deployer.undeploy(deploymentUnit);

        // EJBs maybe included directly in war deployment
        ServiceName jaccServiceName = getJaccServiceName(deploymentUnit);
        ServiceRegistry registry = deploymentUnit.getServiceRegistry();
        if (registry != null) {
            ServiceController<?> serviceController = registry.getService(jaccServiceName);
            if (serviceController != null) {
                serviceController.setMode(ServiceController.Mode.REMOVE);
            }
        }
    }

    private ServiceName getJaccServiceName(DeploymentUnit deploymentUnit){
        final DeploymentUnit parentDU = deploymentUnit.getParent();
        // EJBs maybe included directly in war deployment
        ServiceName jaccServiceName = deploymentUnit.getServiceName().append(JaccService.SERVICE_NAME).append("ejb");
        //Qualify the service name properly with parent DU
        if(parentDU != null) {
            jaccServiceName = jaccServiceName.append(parentDU.getName());
        }
        return jaccServiceName;
    }
}