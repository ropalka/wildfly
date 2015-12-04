/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat Middleware LLC, and individual contributors
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

package org.jboss.as.pojo;

import java.io.Closeable;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import org.jboss.as.pojo.descriptor.KernelDeploymentXmlDescriptor;
import org.jboss.as.pojo.descriptor.KernelDeploymentXmlDescriptorParser;
import org.jboss.as.pojo.descriptor.LegacyKernelDeploymentXmlDescriptorParser;
import org.jboss.as.pojo.logging.PojoLogger;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.modules.Resource;
import org.jboss.staxmapper.XMLMapper;
import org.wildfly.loaders.ResourceLoader;

/**
 * DeploymentUnitProcessor responsible for parsing a jboss-beans.xml
 * descriptor and attaching the corresponding KernelDeploymentXmlDescriptor.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class KernelDeploymentParsingProcessor implements DeploymentUnitProcessor {

    private static final String JBOSS_BEANS_EXTENSION = "jboss-beans.xml";
    private static final XMLMapper xmlMapper = XMLMapper.Factory.create();
    private static final XMLInputFactory inputFactory = XMLInputFactory.newInstance();

    static {
        final KernelDeploymentXmlDescriptorParser parser = new KernelDeploymentXmlDescriptorParser();
        xmlMapper.registerRootElement(new QName(KernelDeploymentXmlDescriptorParser.NAMESPACE, "deployment"), parser);
        // old MC parser -- just a warning / info atm
        final LegacyKernelDeploymentXmlDescriptorParser legacy = new LegacyKernelDeploymentXmlDescriptorParser();
        xmlMapper.registerRootElement(new QName(LegacyKernelDeploymentXmlDescriptorParser.MC_NAMESPACE_1_0, "deployment"), legacy);
        xmlMapper.registerRootElement(new QName(LegacyKernelDeploymentXmlDescriptorParser.MC_NAMESPACE_2_0, "deployment"), legacy);
    }

    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();
        if (unit.hasAttachment(Attachments.OSGI_MANIFEST)) {
            return;
        }
        final ResourceLoader loader = unit.getAttachment(Attachments.DEPLOYMENT_ROOT).getLoader();
        parseJBossBeansDescriptors(unit, loader);
        final List<ResourceRoot> resourceRoots = unit.getAttachmentList(Attachments.RESOURCE_ROOTS);
        for (final ResourceRoot root : resourceRoots) {
            parseJBossBeansDescriptors(unit, root.getLoader());
        }
    }

    @Override
    public void undeploy(final DeploymentUnit context) {
    }

    private static void parseJBossBeansDescriptors(final DeploymentUnit unit, final ResourceLoader loader) throws DeploymentUnitProcessingException {
        final Collection<Resource> beans;
        if (loader.getRootName().endsWith(JBOSS_BEANS_EXTENSION)) {
            beans = Collections.singleton(loader.getResource(""));
        } else {
            beans = new ArrayList<>();
            collectJBossBeansDescriptors(loader, "META-INF", beans);
            collectJBossBeansDescriptors(loader, "WEB-INF", beans);
            collectJBossBeansDescriptors(loader, "WEB-INF/classes/META-INF", beans);
        }
        for (final Resource beansXmlFile : beans) {
            parseJBossBeansDescriptor(unit, beansXmlFile);
        }
    }

    private static void collectJBossBeansDescriptors(final ResourceLoader loader, final String dir, final Collection<Resource> beans) {
        final Iterator<Resource> beanResources = loader.iterateResources(dir, false);
        Resource candidate;
        while (beanResources.hasNext()) {
            candidate = beanResources.next();
            if (candidate.getName().endsWith(JBOSS_BEANS_EXTENSION)) {
                beans.add(candidate);
            }
        }
    }

    private static void parseJBossBeansDescriptor(final DeploymentUnit unit, final Resource beansXmlFile) throws DeploymentUnitProcessingException {
        InputStream xmlStream = null;
        try {
            xmlStream = beansXmlFile.openStream();
            final XMLStreamReader reader = inputFactory.createXMLStreamReader(xmlStream);
            final ParseResult<KernelDeploymentXmlDescriptor> result = new ParseResult<KernelDeploymentXmlDescriptor>();
            xmlMapper.parseDocument(result, reader);
            final KernelDeploymentXmlDescriptor xmlDescriptor = result.getResult();
            if (xmlDescriptor != null)
                unit.addToAttachmentList(KernelDeploymentXmlDescriptor.ATTACHMENT_KEY, xmlDescriptor);
            else
                throw PojoLogger.ROOT_LOGGER.failedToParse(beansXmlFile.getName());
        } catch (DeploymentUnitProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw PojoLogger.ROOT_LOGGER.parsingException(beansXmlFile.getName(), e);
        } finally {
            safeClose(xmlStream);
        }
    }

    private static void safeClose(final Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception e) {
                PojoLogger.ROOT_LOGGER.trace("Failed to close resource", e);
            }
        }
    }

}
