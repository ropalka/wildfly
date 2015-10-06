/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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
package org.jboss.as.jpa.hibernate4;

import static org.jboss.modules.PathUtils.canonicalize;
import static org.jboss.modules.PathUtils.relativize;

import org.hibernate.jpa.boot.archive.spi.ArchiveContext;
import org.hibernate.jpa.boot.archive.spi.ArchiveDescriptor;
import org.hibernate.jpa.boot.archive.spi.ArchiveEntry;
import org.hibernate.jpa.boot.spi.InputStreamAccess;
import org.jboss.as.server.loaders.ResourceLoader;
import org.jboss.as.server.loaders.ResourceLoaders;
import org.jboss.as.server.loaders.Utils;
import org.jboss.modules.Resource;

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
        String normalizedPath = relativize(canonicalize(path));
        normalizedPath = normalizedPath.endsWith("/") ? normalizedPath.substring(0, normalizedPath.length() - 1) : normalizedPath;
        ResourceLoader loader = ResourceLoaders.newResourceLoader(new File(archiveFile));
        if (!normalizedPath.equals("") && !normalizedPath.equals("/")) {
            loader = ResourceLoaders.newResourceLoader(normalizedPath, loader, normalizedPath);
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
