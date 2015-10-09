/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

import static org.jboss.modules.PathUtils.canonicalize;
import static org.jboss.modules.PathUtils.relativize;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import io.undertow.server.handlers.resource.Resource;
import io.undertow.server.handlers.resource.ResourceChangeListener;
import io.undertow.server.handlers.resource.ResourceManager;
import org.jboss.as.server.loaders.ResourceLoader;

/**
 * Resource manager that deals with overlays
 *
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class ServletResourceManager implements ResourceManager {

    private final ResourceLoader loader;
    private final Collection<ResourceLoader> overlays;

    public ServletResourceManager(final ResourceLoader loader, final Collection<ResourceLoader> overlays) throws IOException {
        this.loader = loader;
        this.overlays = overlays;
    }

    @Override
    public boolean isResourceChangeListenerSupported() {
        return false;
    }

    @Override
    public void registerResourceChangeListener(final ResourceChangeListener listener) {
        // does nothing
    }

    @Override
    public void removeResourceChangeListener(final ResourceChangeListener listener) {
        // does nothing
    }

    @Override
    public void close() throws IOException {
        // does nothing
    }

    @Override
    public Resource getResource(final String path) throws IOException {
        final String normalizedPath = relativize(canonicalize(path));
        final org.jboss.modules.Resource res = loader.getResource(normalizedPath);
        if (res != null) {
            return new ServletResource(this, new ModulesResource(loader, res, normalizedPath, false));
        } else if (loader.getPaths().contains(normalizedPath)) {
            return new ServletResource(this, new ModulesResource(loader, null, normalizedPath, false));
        }
        if (overlays != null) {
            final String overlayPath = ModulesResource.OVERLAY_PREFIX + normalizedPath;
            for (final ResourceLoader overlay : overlays) {
                org.jboss.modules.Resource child = overlay.getResource(overlayPath);
                if (child != null) {
                    return new ServletResource(this, new ModulesResource(overlay, child, normalizedPath, true));
                } else if (overlay.getPaths().contains(overlayPath)) {
                    return new ServletResource(this, new ModulesResource(overlay, null, normalizedPath, true));
                }
            }
        }
        return null;
    }


    /**
     * Lists all children of a particular path, taking overlays into account
     * @param path The path
     * @return The list of children
     */
    final List<Resource> list(final String path) {
        final String normalizedPath = relativize(canonicalize(path));
        final List<Resource> ret = new ArrayList<>();
        // process loader
        Iterator<org.jboss.modules.Resource> resources = loader.iterateResources(normalizedPath, false);
        org.jboss.modules.Resource res;
        while (resources.hasNext()) {
            res = resources.next();
            ret.add(new ServletResource(this, new ModulesResource(loader, res, res.getName(), false)));
        }
        Iterator<String> subPaths = loader.iteratePaths(normalizedPath, false);
        String subPath;
        while (subPaths.hasNext()) {
            subPath = subPaths.next();
            ret.add(new ServletResource(this, new ModulesResource(loader, null, subPath, false)));
        }
        // process overlays
        String resPath;
        if (overlays != null) {
            final String overlayPath = ModulesResource.OVERLAY_PREFIX + normalizedPath;
            for (final ResourceLoader overlay : overlays) {
                resources = overlay.iterateResources(overlayPath, false);
                while (resources.hasNext()) {
                    res = resources.next();
                    resPath = res.getName().substring(ModulesResource.OVERLAY_PREFIX.length());
                    ret.add(new ServletResource(this, new ModulesResource(overlay, res, resPath, true)));
                }
                subPaths = overlay.iteratePaths(overlayPath, false);
                while (subPaths.hasNext()) {
                    subPath = subPaths.next();
                    resPath = subPath.substring(ModulesResource.OVERLAY_PREFIX.length());
                    ret.add(new ServletResource(this, new ModulesResource(overlay, null, resPath, true)));
                }
            }
        }
        return ret;
    }

}
