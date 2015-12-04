/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2008-2009, Red Hat Middleware LLC, and individual contributors
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

package org.jboss.as.connector.annotations.repository.jandex;

import static org.junit.Assert.fail;

import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.jboss.jca.common.annotations.Annotations;
import org.jboss.jca.common.api.validator.ValidateException;
import org.jboss.jca.common.spi.annotations.repository.AnnotationRepository;
import org.jboss.modules.Resource;
import org.wildfly.loaders.ResourceLoader;
import org.wildfly.loaders.ResourceLoaders;

import java.io.Closeable;
import java.io.File;
import java.io.InputStream;
import java.util.Iterator;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test cases for the annotations handling
 *
 * @author <a href="mailto:jesper.pedersen@jboss.org">Jesper Pedersen</a>
 * @author <a href="mailto:jeff.zhang@jboss.org">Jeff Zhang</a>
 * @version $Revision: $
 */
public class AnnotationsTestCase {
    /**
     * Annotations
     */
    private Annotations annotations;

    /**
     * Process: Null arguemnts
     *
     * @throws Throwable throwable exception
     */
    @Test(expected = ValidateException.class)
    public void testProcessNullArguments() throws Throwable {
        annotations.process(null, null, null);
    }

    /**
     * Process: Null arguemnt for annotation repository
     *
     * @throws Throwable throwable exception
     */
    @Test(expected = ValidateException.class)
    public void testProcessNullAnnotationRepository() throws Throwable {
        annotations.process(null, null, null);
    }

    /**
     * Process: Connector -- verification of the processConnector method
     *
     * @throws Throwable throwable exception
     */
    @Test
    public void testProcessConnector() throws Throwable {
        try {
            File file = getFile("/ra16inoutanno.rar");
            final ResourceLoader loader = ResourceLoaders.newResourceLoader(file);
            final Indexer indexer = new Indexer();
            final Iterator<Resource> resources = loader.iterateResources("", true);
            Resource resource;
            while (resources.hasNext()) {
                resource = resources.next();
                if (resource.getName().endsWith(".class")) {
                    InputStream inputStream = null;
                    try {
                        inputStream = resource.openStream();
                        indexer.index(inputStream);
                    } finally {
                        safeClose(inputStream);
                    }
                }
            }
            final Index index = indexer.complete();
            AnnotationRepository ar = new JandexAnnotationRepositoryImpl(index, Thread.currentThread().getContextClassLoader());

            annotations.process(ar, null, Thread.currentThread().getContextClassLoader());
        } catch (Throwable t) {
            t.printStackTrace();
            fail(t.getMessage());
        }
    }

    /**
     * Process: Connector -- verification of the processConnector method
     *
     * @throws Throwable throwable exception
     */
    @Test
    public void testProcessConnectorFail() throws Throwable {
        try {
            File file = getFile("/rafail2connector.rar");
            final ResourceLoader loader = ResourceLoaders.newResourceLoader(file);
            final Indexer indexer = new Indexer();
            final Iterator<Resource> resources = loader.iterateResources("", true);
            Resource resource;
            while (resources.hasNext()) {
                resource = resources.next();
                if (resource.getName().endsWith(".class")) {
                    InputStream inputStream = null;
                    try {
                        inputStream = resource.openStream();
                        indexer.index(inputStream);
                    } finally {
                        safeClose(inputStream);
                    }
                }
            }
            final Index index = indexer.complete();
            AnnotationRepository ar = new JandexAnnotationRepositoryImpl(index, Thread.currentThread().getContextClassLoader());

            annotations.process(ar, null, Thread.currentThread().getContextClassLoader());

            fail("Success");
        } catch (Throwable t) {
            // Ok
        }
    }

    /**
     * Process: ConnectionDefinitions -- verification of the
     * processConnectionDefinitions method
     *
     * @throws Throwable throwable exception
     */
    @Test
    public void testProcessConnectionDefinitions() throws Throwable {
        try {
            File file = getFile("/ra16annoconndefs.rar");
            final ResourceLoader loader = ResourceLoaders.newResourceLoader(file);
            final Indexer indexer = new Indexer();
            final Iterator<Resource> resources = loader.iterateResources("", true);
            Resource resource;
            while (resources.hasNext()) {
                resource = resources.next();
                if (resource.getName().endsWith(".class")) {
                    InputStream inputStream = null;
                    try {
                        inputStream = resource.openStream();
                        indexer.index(inputStream);
                    } finally {
                        safeClose(inputStream);
                    }
                }
            }
            final Index index = indexer.complete();
            AnnotationRepository ar = new JandexAnnotationRepositoryImpl(index, Thread.currentThread().getContextClassLoader());
            annotations.process(ar, null, Thread.currentThread().getContextClassLoader());
        } catch (Throwable t) {
            t.printStackTrace();
            fail(t.getMessage());
        }
    }

    /**
     * Process: ConnectionDefinition -- verification of the
     * processConnectionDefinition method
     *
     * @throws Throwable throwable exception
     */
    @Test
    public void testProcessConnectionDefinition() throws Throwable {
        try {
            File file = getFile("/ra16annoconndef.rar");
            final ResourceLoader loader = ResourceLoaders.newResourceLoader(file);
            final Indexer indexer = new Indexer();
            final Iterator<Resource> resources = loader.iterateResources("", true);
            Resource resource;
            while (resources.hasNext()) {
                resource = resources.next();
                if (resource.getName().endsWith(".class")) {
                    InputStream inputStream = null;
                    try {
                        inputStream = resource.openStream();
                        indexer.index(inputStream);
                    } finally {
                        safeClose(inputStream);
                    }
                }
            }
            final Index index = indexer.complete();
            AnnotationRepository ar = new JandexAnnotationRepositoryImpl(index, Thread.currentThread().getContextClassLoader());
            annotations.process(ar, null, Thread.currentThread().getContextClassLoader());
        } catch (Throwable t) {
            fail(t.getMessage());
        }
    }

    /**
     * Process: Activation -- verification of the processActivation method
     *
     * @throws Throwable throwable exception
     */
    @Test
    public void testProcessActivation() throws Throwable {
        try {
            File file = getFile("/ra16annoactiv.rar");
            final ResourceLoader loader = ResourceLoaders.newResourceLoader(file);
            final Indexer indexer = new Indexer();
            final Iterator<Resource> resources = loader.iterateResources("", true);
            Resource resource;
            while (resources.hasNext()) {
                resource = resources.next();
                if (resource.getName().endsWith(".class")) {
                    InputStream inputStream = null;
                    try {
                        inputStream = resource.openStream();
                        indexer.index(inputStream);
                    } finally {
                        safeClose(inputStream);
                    }
                }
            }
            final Index index = indexer.complete();
            AnnotationRepository ar = new JandexAnnotationRepositoryImpl(index, Thread.currentThread().getContextClassLoader());
            annotations.process(ar, null, Thread.currentThread().getContextClassLoader());
        } catch (Throwable t) {
            fail(t.getMessage());
        }
    }

    /**
     * Process: AuthenticationMechanism -- verification of the
     * processAuthenticationMechanism method
     *
     * @throws Throwable throwable exception
     */
    @Test
    public void testProcessAuthenticationMechanism() throws Throwable {
        try {
            File file = getFile("/ra16annoauthmech.rar");
            final ResourceLoader loader = ResourceLoaders.newResourceLoader(file);
            final Indexer indexer = new Indexer();
            final Iterator<Resource> resources = loader.iterateResources("", true);
            Resource resource;
            while (resources.hasNext()) {
                resource = resources.next();
                if (resource.getName().endsWith(".class")) {
                    InputStream inputStream = null;
                    try {
                        inputStream = resource.openStream();
                        indexer.index(inputStream);
                    } finally {
                        safeClose(inputStream);
                    }
                }
            }
            final Index index = indexer.complete();
            AnnotationRepository ar = new JandexAnnotationRepositoryImpl(index, Thread.currentThread().getContextClassLoader());
            annotations.process(ar, null, Thread.currentThread().getContextClassLoader());
        } catch (Throwable t) {
            fail(t.getMessage());
        }
    }

    /**
     * Process: AdministeredObject -- verification of the
     * processAdministeredObject method
     *
     * @throws Throwable throwable exception
     */
    @Test
    public void testProcessAdministeredObject() throws Throwable {
        try {
            File file = getFile("/ra16annoadminobj.rar");
            final ResourceLoader loader = ResourceLoaders.newResourceLoader(file);
            final Indexer indexer = new Indexer();
            final Iterator<Resource> resources = loader.iterateResources("", true);
            Resource resource;
            while (resources.hasNext()) {
                resource = resources.next();
                if (resource.getName().endsWith(".class")) {
                    InputStream inputStream = null;
                    try {
                        inputStream = resource.openStream();
                        indexer.index(inputStream);
                    } finally {
                        safeClose(inputStream);
                    }
                }
            }
            final Index index = indexer.complete();
            AnnotationRepository ar = new JandexAnnotationRepositoryImpl(index, Thread.currentThread().getContextClassLoader());
            annotations.process(ar, null, Thread.currentThread().getContextClassLoader());
        } catch (Throwable t) {
            fail(t.getMessage());
        }
    }

    /**
     * Process: ConfigProperty -- verification of the processConfigProperty
     * method
     *
     * @throws Throwable throwable exception
     */
    @Test
    public void testProcessConfigProperty() throws Throwable {
        try {
            File file = getFile("/ra16annoconfprop.rar");
            final ResourceLoader loader = ResourceLoaders.newResourceLoader(file);
            final Indexer indexer = new Indexer();
            final Iterator<Resource> resources = loader.iterateResources("", true);
            Resource resource;
            while (resources.hasNext()) {
                resource = resources.next();
                if (resource.getName().endsWith(".class")) {
                    InputStream inputStream = null;
                    try {
                        inputStream = resource.openStream();
                        indexer.index(inputStream);
                    } finally {
                        safeClose(inputStream);
                    }
                }
            }
            final Index index = indexer.complete();
            AnnotationRepository ar = new JandexAnnotationRepositoryImpl(index, Thread.currentThread().getContextClassLoader());
            annotations.process(ar, null, Thread.currentThread().getContextClassLoader());
        } catch (Throwable t) {
            fail(t.getMessage());
        }
    }

    /**
     * be run before the Test method.
     *
     * @throws Throwable throwable exception
     */
    @Before
    public void setup() throws Throwable {
        annotations = new Annotations();
    }

    /**
     * causes that method to be run after the Test method.
     *
     * @throws Throwable throwable exception
     */
    @After
    public void tearDown() throws Throwable {
        annotations = null;
    }

    /**
     * Lifecycle start, before the suite is executed
     *
     * @throws Throwable throwable exception
     */
    @BeforeClass
    public static void beforeClass() throws Throwable {
    }

    /**
     * Lifecycle stop, after the suite is executed
     *
     * @throws Throwable throwable exception
     */
    @AfterClass
    public static void afterClass() throws Throwable {
    }

    /**
     * Get the URL for a test archive
     *
     * @param archive The name of the test archive
     * @return The URL to the archive
     * @throws Throwable throwable exception
     */
    public File getFile(String archive) throws Throwable {
        return new File(getClass().getResource(archive).toURI());
    }

    static void safeClose(final Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignore) {
            }
        }
    }

}
