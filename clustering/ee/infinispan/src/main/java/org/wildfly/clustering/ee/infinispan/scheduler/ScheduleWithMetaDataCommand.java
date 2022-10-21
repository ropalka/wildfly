/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021, Red Hat, Inc., and individual contributors
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

package org.wildfly.clustering.ee.infinispan.scheduler;

/**
 * Command that schedules an item, where its meta data is persisted.
 * @author Paul Ferraro
 */
public class ScheduleWithMetaDataCommand<I, M> implements ScheduleCommand<I, M> {
    private static final long serialVersionUID = 2116021502509126945L;

    private final I id;
    private final M metaData;

    public ScheduleWithMetaDataCommand(I id, M metaData) {
        this.id = id;
        this.metaData = metaData;
    }

    @Override
    public I getId() {
        return this.id;
    }

    @Override
    public M getMetaData() {
        return this.metaData;
    }

    @Override
    public String toString() {
        return String.format("%s[%s]", this.getClass().getSimpleName(), this.id);
    }
}
