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
package org.jboss.as.naming.service;

import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.as.naming.ServiceBasedNamingStore;
import org.jboss.as.naming.context.external.ExternalContexts;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A binder service for external contexts.
 * @author Eduardo Martins
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class ExternalContextBinderService extends BinderService {

    private final Supplier<ExternalContexts> externalContextsSupplier;

    public ExternalContextBinderService(final String name, final Object source,
                                        final Consumer<ManagedReferenceFactory> managedReferenceFactoryConsumer,
                                        final Supplier<ManagedReferenceFactory> managedReferenceFactorySupplier,
                                        final Supplier<ServiceBasedNamingStore> namingStoreSupplier,
                                        final Supplier<ExternalContexts> externalContextsSupplier) {
        super(name, source, managedReferenceFactoryConsumer, managedReferenceFactorySupplier, namingStoreSupplier);
        this.externalContextsSupplier = externalContextsSupplier;
    }

    @Override
    public synchronized void start(final StartContext context) throws StartException {
        super.start(context);
        externalContextsSupplier.get().addExternalContext(context.getController().getName());
    }

    @Override
    public synchronized void stop(final StopContext context) {
        externalContextsSupplier.get().removeExternalContext(context.getController().getName());
        super.stop(context);
    }

}
