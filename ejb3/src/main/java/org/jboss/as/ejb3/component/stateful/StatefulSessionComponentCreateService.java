/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.ejb3.component.stateful;

import org.jboss.as.ee.component.BasicComponent;
import org.jboss.as.ee.component.ComponentConfiguration;
import org.jboss.as.ejb3.cache.CacheFactory;
import org.jboss.as.ejb3.component.DefaultAccessTimeoutService;
import org.jboss.as.ejb3.component.InvokeMethodOnTargetInterceptor;
import org.jboss.as.ejb3.component.interceptors.CurrentInvocationContextInterceptor;
import org.jboss.as.ejb3.component.session.SessionBeanComponentCreateService;
import org.jboss.as.ejb3.deployment.ApplicationExceptions;
import org.jboss.ejb.client.SessionID;
import org.jboss.invocation.ContextClassLoaderInterceptor;
import org.jboss.invocation.ImmediateInterceptorFactory;
import org.jboss.invocation.InterceptorFactory;
import org.jboss.invocation.Interceptors;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.function.Supplier;

import org.jboss.as.ejb3.cache.CacheFactoryBuilder;
import org.jboss.modules.ModuleLoader;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.clustering.ejb.BeanContext;
import org.wildfly.clustering.ejb.Time;

/**
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class StatefulSessionComponentCreateService extends SessionBeanComponentCreateService {

    private final InterceptorFactory afterBegin;
    private final Method afterBeginMethod;
    private final InterceptorFactory afterCompletion;
    private final Method afterCompletionMethod;
    private final InterceptorFactory beforeCompletion;
    private final Method beforeCompletionMethod;
    private final InterceptorFactory prePassivate;
    private final InterceptorFactory postActivate;
    private final StatefulTimeoutInfo statefulTimeout;
    private final ClassLoader loader;
    private final InterceptorFactory ejb2XRemoveMethod;
    @SuppressWarnings("rawtypes")
    private final Set<Object> serializableInterceptorContextKeys;
    private final boolean passivationCapable;
    private final ModuleLoader moduleLoader;
    private final ServiceName deploymentUnitServiceName;
    private final ServiceName componentServiceName;
    private volatile Supplier<CacheFactory> cacheFactory;
    private volatile Supplier<DefaultAccessTimeoutService> defaultAccessTimeoutService;
    private volatile Supplier<CacheFactoryBuilder> cacheFactoryBuilder;

    /**
     * Construct a new instance.
     *
     * @param componentConfiguration the component configuration
     */
    public StatefulSessionComponentCreateService(final ComponentConfiguration componentConfiguration, final ApplicationExceptions ejbJarConfiguration) {
        super(componentConfiguration, ejbJarConfiguration);

        final StatefulComponentDescription componentDescription = (StatefulComponentDescription) componentConfiguration.getComponentDescription();
        final ClassLoader classLoader = componentConfiguration.getModuleClassLoader();
        final InterceptorFactory tcclInterceptorFactory = new ImmediateInterceptorFactory(new ContextClassLoaderInterceptor(classLoader));
        final InterceptorFactory namespaceContextInterceptorFactory = componentConfiguration.getNamespaceContextInterceptorFactory();

        this.afterBeginMethod = componentDescription.getAfterBegin();
        this.afterBegin = (this.afterBeginMethod != null) ? Interceptors.getChainedInterceptorFactory(tcclInterceptorFactory, namespaceContextInterceptorFactory, CurrentInvocationContextInterceptor.FACTORY, invokeMethodOnTarget(this.afterBeginMethod)) : null;
        this.afterCompletionMethod = componentDescription.getAfterCompletion();
        this.afterCompletion = (this.afterCompletionMethod != null) ? Interceptors.getChainedInterceptorFactory(tcclInterceptorFactory, namespaceContextInterceptorFactory, CurrentInvocationContextInterceptor.FACTORY, invokeMethodOnTarget(this.afterCompletionMethod)) : null;
        this.beforeCompletionMethod = componentDescription.getBeforeCompletion();
        this.beforeCompletion = (this.beforeCompletionMethod != null) ? Interceptors.getChainedInterceptorFactory(tcclInterceptorFactory, namespaceContextInterceptorFactory, CurrentInvocationContextInterceptor.FACTORY, invokeMethodOnTarget(this.beforeCompletionMethod)) : null;
        this.prePassivate = Interceptors.getChainedInterceptorFactory(componentConfiguration.getPrePassivateInterceptors());
        this.postActivate = Interceptors.getChainedInterceptorFactory(componentConfiguration.getPostActivateInterceptors());
        //the interceptor chain for EJB e.x remove methods
        this.ejb2XRemoveMethod = Interceptors.getChainedInterceptorFactory(StatefulSessionSynchronizationInterceptor.factory(componentDescription.getTransactionManagementType()), new ImmediateInterceptorFactory(new StatefulRemoveInterceptor(false)), Interceptors.getTerminalInterceptorFactory());
        this.serializableInterceptorContextKeys = componentConfiguration.getInterceptorContextKeys();
        this.passivationCapable = componentDescription.isPassivationApplicable();
        this.deploymentUnitServiceName = componentDescription.getDeploymentUnitServiceName();
        this.componentServiceName = componentDescription.getServiceName();
    }

    private static InterceptorFactory invokeMethodOnTarget(final Method method) {
        method.setAccessible(true);
        return InvokeMethodOnTargetInterceptor.factory(method);
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        this.cacheFactoryBuilder.get().build(context.getChildTarget(), this.componentServiceName.append("cache"), this, this.statefulTimeout).install();
    }

    @Override
    public void stop(StopContext context) {
        super.stop(context);
    }

    @Override
    protected BasicComponent createComponent() {
        return new StatefulSessionComponent(this);
    }

    public InterceptorFactory getAfterBegin() {
        return afterBegin;
    }

    public InterceptorFactory getAfterCompletion() {
        return afterCompletion;
    }

    public InterceptorFactory getBeforeCompletion() {
        return beforeCompletion;
    }

    public InterceptorFactory getPrePassivate() {
        return this.prePassivate;
    }

    public InterceptorFactory getPostActivate() {
        return this.postActivate;
    }

    public Method getAfterBeginMethod() {
        return afterBeginMethod;
    }

    public Method getAfterCompletionMethod() {
        return afterCompletionMethod;
    }

    public Method getBeforeCompletionMethod() {
        return beforeCompletionMethod;
    }

    DefaultAccessTimeoutService getDefaultAccessTimeoutService() {
        final Supplier<DefaultAccessTimeoutService> defaultAccessTimeoutService = this.defaultAccessTimeoutService;
        return defaultAccessTimeoutService != null ? defaultAccessTimeoutService.get() : null;
    }

    void setDefaultAccessTimeoutSupplier(final Supplier<DefaultAccessTimeoutService> defaultAccessTimeoutService) {
        this.defaultAccessTimeoutService = defaultAccessTimeoutService;
    }

    InterceptorFactory getEjb2XRemoveMethod() {
        return ejb2XRemoveMethod;
    }

    Set<Object> getSerializableInterceptorContextKeys() {
        return serializableInterceptorContextKeys;
    }

    void setCacheFactoryBuilderSupplier(final Supplier<CacheFactoryBuilder> cacheFactoryBuilder) {
        this.cacheFactoryBuilder = cacheFactoryBuilder;
    }

    void setCacheFactorySupplier(final Supplier<CacheFactory> cacheFactory) {
        this.cacheFactory = cacheFactory;
    }

    Supplier<CacheFactory> getCacheFactorySupplier() {
        return this.cacheFactory;
    }
}
