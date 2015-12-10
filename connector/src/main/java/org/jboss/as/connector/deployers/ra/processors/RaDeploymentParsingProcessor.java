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


import java.io.Closeable;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Locale;

import org.jboss.as.connector.deployers.Util;
import org.jboss.as.connector.logging.ConnectorLogger;
import org.jboss.as.connector.metadata.xmldescriptors.ConnectorXmlDescriptor;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.jca.common.api.metadata.spec.Connector;
import org.jboss.jca.common.metadata.spec.RaParser;
import org.jboss.modules.Resource;
import org.wildfly.loaders.deployment.ResourceLoader;

/**
 * DeploymentUnitProcessor responsible for parsing a standard jca xml descriptor
 * and attaching the corresponding metadata. It take care also to register this
 * metadata into IronJacamar's MetadataRepository
 *
 * @author <a href="mailto:stefano.maestri@redhat.com">Stefano Maestri</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class RaDeploymentParsingProcessor implements DeploymentUnitProcessor {

    /**
     * Construct a new instance.
     */
    public RaDeploymentParsingProcessor() {
    }

    /**
     * Process a deployment for standard ra deployment files. Will parse the xml
     * file and attach a configuration discovered during processing.
     *
     * @param phaseContext the deployment unit context
     * @throws DeploymentUnitProcessingException
     *
     */
    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final ResourceRoot deploymentRoot = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT);
        final boolean resolveProperties = Util.shouldResolveSpec(deploymentUnit);

        final ResourceLoader loader = deploymentRoot.getLoader();
        final String deploymentRootName = loader.getRootName().toLowerCase(Locale.ENGLISH);
        if (!deploymentRootName.endsWith(RaStructureProcessor.RAR_EXTENSION)) {
            return;
        }

        final Resource alternateDescriptor = deploymentRoot.getAttachment(org.jboss.as.ee.structure.Attachments.ALTERNATE_CONNECTOR_DEPLOYMENT_DESCRIPTOR);
        String prefix = "";

        if (deploymentUnit.getParent() != null) {
            prefix = deploymentUnit.getParent().getName() + "#";
        }

        String deploymentName = prefix + loader.getRootName();
        ConnectorXmlDescriptor xmlDescriptor = process(resolveProperties, loader, alternateDescriptor, deploymentName);


        phaseContext.getDeploymentUnit().putAttachment(ConnectorXmlDescriptor.ATTACHMENT_KEY, xmlDescriptor);

    }

    public static ConnectorXmlDescriptor process(final boolean resolveProperties, final ResourceLoader loader, final Resource alternateDescriptor, final String deploymentName) throws DeploymentUnitProcessingException {
        // Locate the descriptor
        final Resource serviceXmlFile;
        if (alternateDescriptor != null) {
            serviceXmlFile = alternateDescriptor;
        } else {
            serviceXmlFile = loader.getResource("/META-INF/ra.xml");
        }
        InputStream xmlStream = null;
        Connector result = null;
        try {
            if (serviceXmlFile != null) {
                xmlStream = serviceXmlFile.openStream();
                RaParser raParser = new RaParser();
                raParser.setSystemPropertiesResolved(resolveProperties);
                result = raParser.parse(xmlStream);
                if (result == null)
                    throw ConnectorLogger.ROOT_LOGGER.failedToParseServiceXml(serviceXmlFile.getName());
            }
            File root = loader.getRoot();
            URL url = root.toURI().toURL();
            return new ConnectorXmlDescriptor(result, root, url, deploymentName);

        } catch (Exception e) {
            throw ConnectorLogger.ROOT_LOGGER.failedToParseServiceXml(e, serviceXmlFile != null ? serviceXmlFile.getName() : null);
        } finally {
            safeClose(xmlStream);
        }
    }

    public void undeploy(final DeploymentUnit context) {
    }

    static void safeClose(final Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception e) {
                ConnectorLogger.ROOT_LOGGER.trace("Failed to close resource", e);
            }
        }
    }

}
