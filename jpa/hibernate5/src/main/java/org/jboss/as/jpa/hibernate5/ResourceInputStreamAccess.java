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

import java.io.IOException;
import java.io.InputStream;

import org.hibernate.boot.archive.spi.ArchiveException;
import org.hibernate.boot.archive.spi.InputStreamAccess;
import org.jboss.modules.Resource;

/**
 * InputStreamAccess provides Hibernate with lazy, on-demand access to InputStreams for the various
 * types of resources found during archive scanning.
 *
 * @author Steve Ebersole
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class ResourceInputStreamAccess implements InputStreamAccess {
    private final String name;
    private final Resource resource;

    public ResourceInputStreamAccess(String name, Resource resource) {
        this.name = name;
        this.resource = resource;
    }

    @Override
    public String getStreamName() {
        return name;
    }

    @Override
    public InputStream accessInputStream() {
        try {
            return resource.openStream();
        }
        catch (final IOException e) {
            throw new ArchiveException( "Unable to open resource based InputStream", e );
        }
    }

}
