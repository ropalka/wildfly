/*
 * JBoss, Home of Professional Open Source
 * Copyright 2013, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.jpa.hibernate5;

import static org.wildfly.loaders.deployment.Utils.normalizePath;

import org.hibernate.boot.archive.spi.ArchiveContext;
import org.hibernate.boot.archive.spi.ArchiveDescriptor;
import org.hibernate.boot.archive.spi.ArchiveEntry;
import org.hibernate.boot.archive.spi.InputStreamAccess;
import org.jboss.modules.Resource;
import org.wildfly.loaders.deployment.ResourceLoader;
import org.wildfly.loaders.deployment.ResourceLoaders;
import org.wildfly.loaders.deployment.Utils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Iterator;

/**
 * Representation of an archive using ResourceLoader API.
 *
 * @author Steve Ebersole
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class ResourceLoaderArchiveDescriptor implements ArchiveDescriptor {

    private final ResourceLoader loader;

    public ResourceLoaderArchiveDescriptor(final URL archiveRoot, String entryBase) throws IOException {
        entryBase = entryBase == null ? "" : entryBase;
        String archiveURL = archiveRoot.toString();
        archiveURL = archiveURL.startsWith("jar:") ? archiveURL.substring(4) : archiveURL;
        archiveURL = archiveURL.startsWith("file:") ? archiveURL.substring(5) : archiveURL;
        int exclamationIndex = archiveURL.indexOf("!");
        final String archiveFile = exclamationIndex > -1 ? archiveURL.substring(0, exclamationIndex) : archiveURL;
        final String path = (exclamationIndex > -1 ? archiveURL.substring(exclamationIndex + 1) : "") + "/" + entryBase;
        final String normalizedPath = "".equals(path) ? "" : normalizePath(path);
        ResourceLoader loader = ResourceLoaders.newResourceLoader(new File(archiveFile), false);
        if (!normalizedPath.equals("")) {
            loader = ResourceLoaders.newResourceLoader(normalizedPath, loader, normalizedPath, false);
        }
        this.loader = loader;
    }

    @Override
    public void visitArchive(final ArchiveContext archiveContext) {
        final Iterator<Resource> resources = loader.iterateResources("", true);
        Resource resource;
        while (resources.hasNext()) {
            resource = resources.next();
            final String relativeName = resource.getName();
            final String name = Utils.getResourceName(relativeName);
            final InputStreamAccess inputStreamAccess = new ResourceInputStreamAccess( name, resource );
            final ArchiveEntry entry = new ArchiveEntry() {
                @Override
                public String getName() {
                    return name;
                }

                @Override
                public String getNameWithinArchive() {
                    return relativeName;
                }

                @Override
                public InputStreamAccess getStreamAccess() {
                    return inputStreamAccess;
                }
            };
            archiveContext.obtainArchiveEntryHandler( entry ).handleEntry( entry, archiveContext );
        }
    }

}
