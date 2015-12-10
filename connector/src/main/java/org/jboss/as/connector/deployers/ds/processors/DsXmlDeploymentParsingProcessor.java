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

package org.jboss.as.connector.deployers.ds.processors;

import java.io.Closeable;
import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.jboss.as.connector.deployers.Util;
import org.jboss.as.connector.deployers.ds.DsXmlParser;
import org.jboss.as.connector.logging.ConnectorLogger;
import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.AttachmentList;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.jca.common.api.metadata.ds.DataSource;
import org.jboss.jca.common.api.metadata.ds.DataSources;
import org.jboss.metadata.property.PropertyReplacer;
import org.jboss.metadata.property.PropertyResolver;
import org.jboss.modules.Resource;
import org.wildfly.loaders.deployment.ResourceLoader;

/**
 * Picks up -ds.xml deployments
 *
 * @author <a href="mailto:jesper.pedersen@jboss.org">Jesper Pedersen</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class DsXmlDeploymentParsingProcessor implements DeploymentUnitProcessor {

    static final AttachmentKey<AttachmentList<DataSources>> DATA_SOURCES_ATTACHMENT_KEY = AttachmentKey.createList(DataSources.class);
    private static final String DATA_SOURCE_SUFFIX = "-ds.xml";
    private static final String[] LOCATIONS = {"WEB-INF", "META-INF"};

    /**
     * Construct a new instance.
     */
    public DsXmlDeploymentParsingProcessor() {
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
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        boolean resolveProperties = Util.shouldResolveJBoss(deploymentUnit);
        final PropertyResolver propertyResolver = deploymentUnit.getAttachment(org.jboss.as.ee.metadata.property.Attachments.FINAL_PROPERTY_RESOLVER);
        final PropertyReplacer propertyReplacer = deploymentUnit.getAttachment(org.jboss.as.ee.metadata.property.Attachments.FINAL_PROPERTY_REPLACER);

        final Set<Resource> resources = dataSources(deploymentUnit);
        boolean loggedDeprication = false;
        for (Resource r : resources) {
            InputStream xmlStream = null;
            try {
                xmlStream = r.openStream();
                DsXmlParser parser = new DsXmlParser(propertyResolver, propertyReplacer);
                parser.setSystemPropertiesResolved(resolveProperties);
                DataSources dataSources = parser.parse(xmlStream);

                if (dataSources != null) {
                    if (!loggedDeprication) {
                        loggedDeprication = true;
                        ConnectorLogger.ROOT_LOGGER.deprecated();
                    }
                    for (DataSource ds : dataSources.getDataSource()) {
                        if (ds.getDriver() == null) {
                            throw ConnectorLogger.ROOT_LOGGER.FailedDeployDriverNotSpecified(ds.getJndiName());
                        }
                    }
                    deploymentUnit.addToAttachmentList(DATA_SOURCES_ATTACHMENT_KEY, dataSources);
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
                ConnectorLogger.ROOT_LOGGER.trace("Failed to close resource", e);
            }
        }
    }

    private Set<Resource> dataSources(final DeploymentUnit deploymentUnit) {
        final ResourceLoader loader = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT).getLoader();
        final String deploymentRootName = loader.getRootName().toLowerCase(Locale.ENGLISH);

        if (deploymentRootName.endsWith(DATA_SOURCE_SUFFIX)) {
            return Collections.singleton(loader.getResource(""));
        }
        final Set<Resource> ret = new HashSet<>();
        for (final String location : LOCATIONS) {
            final Iterator<Resource> resources = loader.iterateResources(location, false);
            Resource resource;
            while (resources.hasNext()) {
                resource = resources.next();
                if (resource.getName().endsWith(DATA_SOURCE_SUFFIX)) {
                    ret.add(resource);
                }
            }
        }
        return ret;
    }

    public void undeploy(final DeploymentUnit context) {
    }

}
