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

import static org.jboss.as.server.loaders.Utils.getResourceName;

import io.undertow.UndertowLogger;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.resource.Resource;
import io.undertow.util.DateUtils;
import io.undertow.util.ETag;
import io.undertow.util.MimeMappings;
import org.jboss.as.server.loaders.ResourceLoader;
import org.xnio.IoUtils;
import org.xnio.Pooled;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class ModulesResource implements Resource {

    static final String OVERLAY_PREFIX = "META-INF/resources/";

    private final ResourceLoader loader;
    private final org.jboss.modules.Resource res;
    private final File resFile;
    private final File loaderFile;
    private final String path;
    private final boolean isOverlay;

    public ModulesResource(final ResourceLoader loader, final org.jboss.modules.Resource res, final String path, final boolean isOverlay) {
        this.loader = loader;
        this.res = res;
        this.resFile = res != null ? new File(res.getURL().getFile()) : null;
        this.loaderFile = new File(loader.getRootURL().getFile());
        this.path = path;
        this.isOverlay = isOverlay;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public Date getLastModified() {
        return resFile != null ? new Date(resFile.lastModified()) : null;
    }

    @Override
    public String getLastModifiedString() {
        final Date lastModified = getLastModified();
        if (lastModified == null) {
            return null;
        }
        return DateUtils.toDateString(lastModified);
    }

    @Override
    public ETag getETag() {
        return null;
    }

    @Override
    public String getName() {
        if (path == null || path.equals("") || path.equals("/")) return path;
        return getResourceName(path);
    }

    @Override
    public boolean isDirectory() {
        return res == null;
    }

    @Override
    public List<Resource> list() {
        if (res != null) return Collections.emptyList();
        final List<Resource> resources = new ArrayList<>();
        final String prefix = isOverlay ? OVERLAY_PREFIX : "";
        final Iterator<org.jboss.modules.Resource> children = loader.iterateResources(prefix + path, false);
        org.jboss.modules.Resource child;
        String childPath;
        while (children.hasNext()) {
            child = children.next();
            childPath = isOverlay ? child.getName().substring(OVERLAY_PREFIX.length()) : child.getName();
            resources.add(new ModulesResource(loader, child, childPath, isOverlay));
        }
        return resources;
    }

    @Override
    public String getContentType(final MimeMappings mimeMappings) {
        if (res == null) return null;
        final String fileName = getResourceName(path);
        int index = fileName.lastIndexOf('.');
        if (index != -1 && index != fileName.length() - 1) {
            return mimeMappings.getMimeType(fileName.substring(index + 1));
        }
        return null;
    }

    @Override
    public void serve(final Sender sender, final HttpServerExchange exchange, final IoCallback callback) {
        abstract class BaseFileTask implements Runnable {
            protected volatile ReadableByteChannel readChannel;

            protected boolean openChannel() {
                try {
                    readChannel = Channels.newChannel(res.openStream());
                } catch (FileNotFoundException e) {
                    exchange.setResponseCode(404);
                    callback.onException(exchange, sender, e);
                    return false;
                } catch (IOException e) {
                    exchange.setResponseCode(500);
                    callback.onException(exchange, sender, e);
                    return false;
                }
                return true;
            }
        }

        class ServerTask extends BaseFileTask implements IoCallback {

            private Pooled<ByteBuffer> pooled;

            @Override
            public void run() {
                if (readChannel == null) {
                    if (!openChannel()) {
                        return;
                    }
                    pooled = exchange.getConnection().getBufferPool().allocate();
                }
                if (pooled != null) {
                    ByteBuffer buffer = pooled.getResource();
                    try {
                        buffer.clear();
                        int res = readChannel.read(buffer);
                        if (res == -1) {
                            //we are done
                            pooled.free();
                            IoUtils.safeClose(readChannel);
                            callback.onComplete(exchange, sender);
                            return;
                        }
                        buffer.flip();
                        sender.send(buffer, this);
                    } catch (IOException e) {
                        onException(exchange, sender, e);
                    }
                }

            }

            @Override
            public void onComplete(final HttpServerExchange exchange, final Sender sender) {
                if (exchange.isInIoThread()) {
                    exchange.dispatch(this);
                } else {
                    run();
                }
            }

            @Override
            public void onException(final HttpServerExchange exchange, final Sender sender, final IOException exception) {
                UndertowLogger.REQUEST_IO_LOGGER.ioException(exception);
                if (pooled != null) {
                    pooled.free();
                    pooled = null;
                }
                IoUtils.safeClose(readChannel);
                if (!exchange.isResponseStarted()) {
                    exchange.setResponseCode(500);
                }
                callback.onException(exchange, sender, exception);
            }
        }

        BaseFileTask task = new ServerTask();
        if (exchange.isInIoThread()) {
            exchange.dispatch(task);
        } else {
            task.run();
        }
    }

    @Override
    public Long getContentLength() {
        return res.getSize();
    }

    @Override
    public String getCacheKey() {
        return path;
    }

    @Override
    public File getFile() {
        return resFile;
    }

    @Override
    public File getResourceManagerRoot() {
        return loaderFile;
    }

    @Override
    public URL getUrl() {
        return res != null ? res.getURL() : null;
    }

    public Path getResourceManagerRootPath() {
        File rootPath = getResourceManagerRoot();
        if (rootPath == null) return null;
        return getResourceManagerRoot().toPath();
    }

    public Path getFilePath() {
        if(getFile() == null) {
            return null;
        }
        return getFile().toPath();
    }

}
