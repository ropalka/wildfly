/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.wildfly.extension.messaging.activemq.deployment;

import java.io.Closeable;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.jboss.as.ee.structure.JBossDescriptorPropertyReplacement;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.modules.Resource;
import org.jboss.staxmapper.XMLMapper;
import org.wildfly.extension.messaging.activemq.logging.MessagingLogger;
import org.wildfly.loaders.ResourceLoader;

/**
 * Processor that handles the messaging subsystems deployable XML
 *
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class MessagingXmlParsingDeploymentUnitProcessor implements DeploymentUnitProcessor {

    private static final XMLInputFactory INPUT_FACTORY = XMLInputFactory.newInstance();
    private static final String[] LOCATIONS = {"WEB-INF", "META-INF"};
    private static final String JMS_XML_EXTENSION = "-jms.xml";
    private static final QName ROOT_1_0 = new QName(Namespace.MESSAGING_DEPLOYMENT_1_0.getUriString(), "messaging-deployment");
    private static final QName ROOT_NO_NAMESPACE = new QName("messaging-deployment");

    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final Set<Resource> files = messageDestinations(deploymentUnit);
        final XMLMapper mapper = XMLMapper.Factory.create();
        final MessagingDeploymentParser_1_0 messagingDeploymentParser_1_0 = new MessagingDeploymentParser_1_0(JBossDescriptorPropertyReplacement.propertyReplacer(deploymentUnit));
        mapper.registerRootElement(ROOT_1_0, messagingDeploymentParser_1_0);
        mapper.registerRootElement(ROOT_NO_NAMESPACE, messagingDeploymentParser_1_0);

        for (final Resource file : files) {
            InputStream xmlStream = null;
            try {
                xmlStream = file.openStream();
                try {

                    final XMLInputFactory inputFactory = INPUT_FACTORY;
                    setIfSupported(inputFactory, XMLInputFactory.IS_VALIDATING, Boolean.FALSE);
                    setIfSupported(inputFactory, XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
                    final XMLStreamReader streamReader = inputFactory.createXMLStreamReader(xmlStream);
                    final ParseResult result = new ParseResult();
                    try {
                        mapper.parseDocument(result, streamReader);
                        deploymentUnit.addToAttachmentList(MessagingAttachments.PARSE_RESULT, result);
                    } finally {
                        safeClose(streamReader, file.getName());
                    }
                } catch (XMLStreamException e) {
                    throw MessagingLogger.ROOT_LOGGER.couldNotParseDeployment(file.getName(), e);
                }
            } catch (Exception e) {
                throw new DeploymentUnitProcessingException(e.getMessage(), e);
            } finally {
                safeClose(xmlStream);
            }
        }
    }

    static void safeClose(final Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception e) {
                MessagingLogger.ROOT_LOGGER.trace("Failed to close resource", e);
            }
        }
    }

    private void setIfSupported(final XMLInputFactory inputFactory, final String property, final Object value) {
        if (inputFactory.isPropertySupported(property)) {
            inputFactory.setProperty(property, value);
        }
    }

    @Override
    public void undeploy(final DeploymentUnit context) {
    }

    private Set<Resource> messageDestinations(final DeploymentUnit deploymentUnit) {
        final ResourceLoader loader = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT).getLoader();
        final String deploymentRootName = loader.getRootName().toLowerCase(Locale.ENGLISH);

        if (deploymentRootName.endsWith(JMS_XML_EXTENSION)) {
            return Collections.singleton(loader.getResource(""));
        }
        final Set<Resource> ret = new HashSet<>();
        for (String location : LOCATIONS) {
            final Iterator<Resource> loc = loader.iterateResources(location, false);
            Resource resource;
            while (loc.hasNext()) {
                resource = loc.next();
                if (resource.getName().endsWith(JMS_XML_EXTENSION)) {
                    ret.add(resource);
                }
            }
        }
        return ret;
    }

    private static void safeClose(final XMLStreamReader closeable, final String file) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (XMLStreamException e) {
                MessagingLogger.ROOT_LOGGER.couldNotCloseFile(file, e);
            }
        }
    }
}
