/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.jboss.as.webservices.util;

import static org.wildfly.loaders.deployment.Utils.getResourceName;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import org.jboss.as.webservices.logging.WSLogger;
import org.jboss.modules.Resource;
import org.jboss.wsf.spi.deployment.UnifiedVirtualFile;
import org.wildfly.loaders.deployment.ResourceLoader;

/**
 * A ResourceLoader and Resource adaptor.
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class VirtualFileAdaptor implements UnifiedVirtualFile {

    private static final long serialVersionUID = -1;

    private final transient ResourceLoader loader;
    private final transient Resource resource;

    public VirtualFileAdaptor(final ResourceLoader loader, final Resource resource) {
        this.loader = loader;
        this.resource = resource;
    }

    private UnifiedVirtualFile findChild(final String child, final boolean throwExceptionIfNotFound) throws IOException {
        if (loader == null) throw new UnsupportedOperationException();
        final Resource childResource = loader.getResource(child);
        if (childResource == null) {
            if (throwExceptionIfNotFound) {
                throw WSLogger.ROOT_LOGGER.missingChild(child, loader.getRootName());
            } else {
                WSLogger.ROOT_LOGGER.tracef("Child '%s' not found for ResourceLoader: %s", child, loader.getRootName());
                return null;
            }
        }
        return new VirtualFileAdaptor(null, childResource);
    }

    public UnifiedVirtualFile findChild(final String child) throws IOException {
        return findChild(child, true);
    }

    public UnifiedVirtualFile findChildFailSafe(final String child) {
        try {
            return findChild(child, false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public URL toURL() {
        if (resource == null) throw new UnsupportedOperationException();
        return resource.getURL();
    }

    public List<UnifiedVirtualFile> getChildren() throws IOException {
        throw new UnsupportedOperationException();
    }

    public String getName() {
        if (resource == null) throw new UnsupportedOperationException();
        return getResourceName(resource.getName());
    }
}
