/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.jboss.as.ee.beanvalidation;

import java.util.function.Supplier;

import javax.validation.ValidatorFactory;

import org.jboss.as.ee.component.InjectionSource;
import org.jboss.as.ee.component.deployers.EEResourceReferenceProcessor;
import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.as.naming.ValueManagedReferenceFactory;
import org.jboss.as.naming.service.ManagedReferenceFactorySupplier;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.value.ImmediateValue;

/**
 * Handled resource injections for the Validator Factory
 *
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class BeanValidationFactoryResourceReferenceProcessor implements EEResourceReferenceProcessor {

    public static final BeanValidationFactoryResourceReferenceProcessor INSTANCE = new BeanValidationFactoryResourceReferenceProcessor();

    @Override
    public String getResourceReferenceType() {
        return ValidatorFactory.class.getName();
    }

    @Override
    public InjectionSource getResourceReferenceBindingSource() {
        return ValidatorFactoryInjectionSource.INSTANCE;
    }

    private static final class ValidatorFactoryInjectionSource extends InjectionSource {

        public static final ValidatorFactoryInjectionSource INSTANCE = new ValidatorFactoryInjectionSource();

        @Override
        public Supplier<ManagedReferenceFactory> getResourceValue(final ResolutionContext resolutionContext, final ServiceBuilder<?> serviceBuilder, final DeploymentPhaseContext phaseContext) {
            final ClassLoader classLoader = phaseContext.getDeploymentUnit().getAttachment(Attachments.MODULE).getClassLoader();
            return new ManagedReferenceFactorySupplier(new ValueManagedReferenceFactory(new ImmediateValue<Object>(new LazyValidatorFactory(classLoader))));
        }
    }
}
