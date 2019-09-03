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

package org.jboss.as.security.deployment;

import javax.security.jacc.PolicyConfiguration;

import org.jboss.as.ee.structure.DeploymentType;
import org.jboss.as.ee.structure.DeploymentTypeMarker;
import org.jboss.as.security.service.JaccService;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A {@code DeploymentUnitProcessor} for JACC policies.
 *
 * @author <a href="mailto:mmoyses@redhat.com">Marcus Moyses</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class JaccEarDeploymentProcessor implements DeploymentUnitProcessor {

    /**
     * {@inheritDoc}
     */
    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        if (DeploymentTypeMarker.isType(DeploymentType.EAR, deploymentUnit)) {
            final AbstractSecurityDeployer<?> deployer = new EarSecurityDeployer();
            final ServiceName jaccServiceName = deploymentUnit.getServiceName().append(JaccService.SERVICE_NAME);
            final ServiceName parentJaccSN = deploymentUnit.getParent() != null ? deploymentUnit.getParent().getServiceName().append(JaccService.SERVICE_NAME) : null;
            final ServiceTarget serviceTarget = phaseContext.getServiceTarget();
            final ServiceBuilder<?> sb = serviceTarget.addService(jaccServiceName);
            final Consumer<PolicyConfiguration> pcConsumer = sb.provides(jaccServiceName);
            final Supplier<PolicyConfiguration> parentpcSupplier = parentJaccSN != null ? sb.requires(parentJaccSN) : null;
            final JaccService<?> service = deployer.deploy(pcConsumer, parentpcSupplier, deploymentUnit);
            sb.setInstance(service);
            sb.install();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void undeploy(DeploymentUnit context) {
        if (DeploymentTypeMarker.isType(DeploymentType.EAR, context)) {
            AbstractSecurityDeployer<?> deployer = new EarSecurityDeployer();
            deployer.undeploy(context);
        }
    }

}
