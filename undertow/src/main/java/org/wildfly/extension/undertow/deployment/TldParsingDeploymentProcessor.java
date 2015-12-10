/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.wildfly.extension.undertow.deployment;

import static org.wildfly.loaders.deployment.Utils.getResourceName;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.jboss.as.ee.structure.DeploymentType;
import org.jboss.as.ee.structure.DeploymentTypeMarker;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.as.web.common.WarMetaData;
import org.jboss.metadata.parser.jsp.TldMetaDataParser;
import org.jboss.metadata.parser.util.NoopXMLResolver;
import org.jboss.metadata.web.jboss.JBossWebMetaData;
import org.jboss.metadata.web.spec.JspConfigMetaData;
import org.jboss.metadata.web.spec.ListenerMetaData;
import org.jboss.metadata.web.spec.TaglibMetaData;
import org.jboss.metadata.web.spec.TldMetaData;
import org.jboss.modules.Resource;
import org.wildfly.extension.undertow.logging.UndertowLogger;
import org.wildfly.loaders.deployment.ResourceLoader;

/**
 * @author Remy Maucherat
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class TldParsingDeploymentProcessor implements DeploymentUnitProcessor {

    private static final String JAR_EXTENSION = ".jar";
    private static final String TLD = ".tld";
    private static final String META_INF = "META-INF";
    private static final String ROOT = "";
    private static final String WEB_INF = "WEB-INF";
    private static final String WEB_INF_CLASSES = "WEB-INF/classes";
    private static final String WEB_INF_LIB = "WEB-INF/lib";
    private static final String IMPLICIT_TLD = "implicit.tld";

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        if (!DeploymentTypeMarker.isType(DeploymentType.WAR, deploymentUnit)) {
            return; // Skip non web deployments
        }
        final WarMetaData warMetaData = deploymentUnit.getAttachment(WarMetaData.ATTACHMENT_KEY);
        if (warMetaData == null || warMetaData.getMergedJBossWebMetaData() == null) {
            return;
        }

        TldsMetaData tldsMetaData = deploymentUnit.getAttachment(TldsMetaData.ATTACHMENT_KEY);
        if (tldsMetaData == null) {
            tldsMetaData = new TldsMetaData();
            deploymentUnit.putAttachment(TldsMetaData.ATTACHMENT_KEY, tldsMetaData);
        }

        final List<TldMetaData> uniqueTlds = new ArrayList<>();
        final Map<String, TldMetaData> tlds = new HashMap<>();
        tldsMetaData.setTlds(tlds);

        final ResourceLoader deploymentLoader = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT).getLoader();
        final List<ResourceRoot> resourceRoots = deploymentUnit.getAttachmentList(Attachments.RESOURCE_ROOTS);
        final JspConfigMetaData merged = warMetaData.getMergedJBossWebMetaData().getJspConfig();

        if (merged != null && merged.getTaglibs() != null) {
            for (final TaglibMetaData tld : merged.getTaglibs()) {
                boolean found = findTld(tld, deploymentLoader, tlds, uniqueTlds, ROOT, WEB_INF, META_INF);
                if (!found) {
                    for (final ResourceRoot subResource : resourceRoots) {
                        found = findTld(tld, subResource.getLoader(), tlds, uniqueTlds, ROOT, META_INF);
                        if (found) break;
                    }
                }
                if (!found) {
                    UndertowLogger.ROOT_LOGGER.tldNotFound(tld.getTaglibLocation());
                }
            }
        }

        // TLDs are located in WEB-INF or any subdir (except the top level "classes" and "lib")
        // and in JARs from WEB-INF/lib, in META-INF or any subdir
        processTlds(deploymentLoader, WEB_INF, tlds, uniqueTlds, WEB_INF_CLASSES, WEB_INF_LIB);
        ResourceLoader loader;
        for (final ResourceRoot resourceRoot : resourceRoots) {
            loader = resourceRoot.getLoader();
            if (loader.getRootName().toLowerCase(Locale.ENGLISH).endsWith(JAR_EXTENSION)) {
                processTlds(loader, META_INF, tlds, uniqueTlds);
            }
        }

        JBossWebMetaData mergedMd = warMetaData.getMergedJBossWebMetaData();
        if (mergedMd.getListeners() == null) {
            mergedMd.setListeners(new ArrayList<>());
        }

        final ArrayList<TldMetaData> allTlds = new ArrayList<>(uniqueTlds);
        allTlds.addAll(tldsMetaData.getSharedTlds(deploymentUnit));


        for (final TldMetaData tld : allTlds) {
            if (tld.getListeners() != null) {
                for (ListenerMetaData l : tld.getListeners()) {
                    mergedMd.getListeners().add(l);
                }
            }
        }
    }


    @Override
    public void undeploy(final DeploymentUnit context) {
    }

    private boolean findTld(final TaglibMetaData tld, final ResourceLoader loader, final Map<String, TldMetaData> tlds, final List<TldMetaData> uniqueTlds, final String... paths) throws DeploymentUnitProcessingException {
        boolean found = false;
        Resource resource;
        ResourceLoader parentLoader;
        for (final String path : paths) {
            resource = loader.getResource(path + "/" + tld.getTaglibLocation());
            if (resource != null) {
                final TldMetaData value = parseTLD(resource);
                value.setUri(tld.getTaglibUri());
                String key = "/" + loader.getRootName() + "/" + resource.getName();
                parentLoader = loader.getParent();
                while (parentLoader != null) {
                    key = "/" + parentLoader.getRootName() + key;
                    parentLoader = parentLoader.getParent();
                }
                uniqueTlds.add(value);
                if (!tlds.containsKey(key)) {
                    tlds.put(key, value);
                }
                if (!tlds.containsKey(tld.getTaglibUri())) {
                    tlds.put(tld.getTaglibUri(), value);
                }
                found = true;
                break;
            }
        }
        return found;
    }

    private void processTlds(final ResourceLoader loader, final String path, final Map<String, TldMetaData> tlds, final List<TldMetaData> uniqueTlds, final String... blackList) throws DeploymentUnitProcessingException {
        final Iterator<Resource> candidates = loader.iterateResources(path, true);
        Resource candidate;
        boolean blacklisted;
        ResourceLoader parentLoader;
        while (candidates.hasNext()) {
            candidate = candidates.next();
            if (!getResourceName(candidate.getName()).toLowerCase(Locale.ENGLISH).endsWith(TLD)) continue;
            blacklisted = false;
            if (blackList != null) {
                for (final String forbidden : blackList) {
                    if (candidate.getName().startsWith(forbidden)) {
                        blacklisted = true;
                        break;
                    }
                }
            }
            if (blacklisted) continue;
            final TldMetaData value = parseTLD(candidate);
            String key = "/" + loader.getRootName() + "/" + candidate.getName();
            parentLoader = loader.getParent();
            while (parentLoader != null) {
                key = "/" + parentLoader.getRootName() + key;
                parentLoader = parentLoader.getParent();
            }
            uniqueTlds.add(value);
            if (!tlds.containsKey(key)) {
                tlds.put(key, value);
            }
        }
    }

    private TldMetaData parseTLD(final Resource tld)
            throws DeploymentUnitProcessingException {
        if (IMPLICIT_TLD.equals(tld.getName())) {
            // Implicit TLDs are different from regular TLDs
            return new TldMetaData();
        }
        InputStream is = null;
        try {
            is = tld.openStream();
            final XMLInputFactory inputFactory = XMLInputFactory.newInstance();
            inputFactory.setXMLResolver(NoopXMLResolver.create());
            XMLStreamReader xmlReader = inputFactory.createXMLStreamReader(is);
            return TldMetaDataParser.parse(xmlReader);
        } catch (XMLStreamException e) {
            throw new DeploymentUnitProcessingException(UndertowLogger.ROOT_LOGGER.failToParseXMLDescriptor(tld.getName(), e.getLocation().getLineNumber(),
                    e.getLocation().getColumnNumber()), e);
        } catch (IOException e) {
            throw new DeploymentUnitProcessingException(UndertowLogger.ROOT_LOGGER.failToParseXMLDescriptor(tld.getName()), e);
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                // Ignore
            }
        }
    }

}
