/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.metrics;

import static org.jboss.as.controller.OperationContext.Stage.RUNTIME;
import static org.jboss.as.controller.OperationContext.Stage.VERIFY;
import static org.jboss.as.controller.PathAddress.EMPTY_ADDRESS;
import static org.jboss.as.server.deployment.Phase.INSTALL;
import static org.jboss.as.server.deployment.Phase.POST_MODULE_METRICS;
import static org.wildfly.extension.metrics.MetricsExtension.SUBSYSTEM_NAME;
import static org.wildfly.extension.metrics.MetricsSubsystemDefinition.METRICS_REGISTRY_RUNTIME_CAPABILITY;
import static org.wildfly.extension.metrics.MetricsSubsystemDefinition.WILDFLY_COLLECTOR;
import static org.wildfly.extension.metrics._private.MetricsLogger.LOGGER;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.wildfly.extension.metrics.deployment.DeploymentMetricProcessor;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2018 Red Hat inc.
 */
class MetricsSubsystemAdd extends AbstractBoottimeAddStepHandler {
    private static final String CAPABILITY_NAME_MICROMETER = "org.wildfly.extension.micrometer.micrometer-collector";
    private static final String CAPABILITY_NAME_OPENTELEMETRY = "org.wildfly.extension.opentelemetry";


    MetricsSubsystemAdd() {
        super(MetricsSubsystemDefinition.ATTRIBUTES);
    }

    static final MetricsSubsystemAdd INSTANCE = new MetricsSubsystemAdd();

    @Override
    protected void recordCapabilitiesAndRequirements(OperationContext operationContext, ModelNode operation, Resource resource) throws OperationFailedException {
        super.recordCapabilitiesAndRequirements(operationContext, operation, resource);

        operationContext.registerCapability(
            RuntimeCapability.Builder.of("org.wildfly.management.context", true).build()
                .fromBaseCapability(MetricsContextService.CONTEXT_NAME.substring(1))
        );
    }

    @Override
    protected void performBoottime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        super.performBoottime(context, operation, model);

        List<String> exposedSubsystems = MetricsSubsystemDefinition.EXPOSED_SUBSYSTEMS.unwrap(context, model);
        boolean exposeAnySubsystem = exposedSubsystems.remove("*");
        String prefix = MetricsSubsystemDefinition.PREFIX.resolveModelAttribute(context, model).asStringOrNull();
        boolean securityEnabled = MetricsSubsystemDefinition.SECURITY_ENABLED.resolveModelAttribute(context, model).asBoolean();

        WildFlyMetricRegistryService.install(context);
        MetricsCollectorService.install(context);
        MetricsContextService.install(context, securityEnabled);

        // If the MP Metrics module is not installed, we need to install the WF Metrics DPU and initiate a metrics
        // collection. If MP Metrics *is* installed, then we do not need to do either of those things, as that module
        // handles that instead.
        if (!context.getCapabilityServiceSupport().hasCapability(MetricsSubsystemDefinition.METRICS_SCAN_CAPABILITY)) {
            context.addStep(new AbstractDeploymentChainStep() {
                public void execute(DeploymentProcessorTarget processorTarget) {
                    processorTarget.addDeploymentProcessor(SUBSYSTEM_NAME, INSTALL, POST_MODULE_METRICS, new DeploymentMetricProcessor(exposeAnySubsystem, exposedSubsystems, prefix));
                }
            }, RUNTIME);

            // delay the registration of the metrics in the VERIFY stage so that all resources
            // created during the RUNTIME phase will have been registered in the MRR.
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext operationContext, ModelNode modelNode) {
                    List<String> otherMetrics = new ArrayList<>();
                    if (context.getCapabilityServiceSupport().hasCapability(CAPABILITY_NAME_MICROMETER)) {
                        otherMetrics.add("Micrometer");
                    }
                    if (context.getCapabilityServiceSupport().hasCapability(CAPABILITY_NAME_OPENTELEMETRY)) {
                        otherMetrics.add("OpenTelemetry Metrics");
                    }
                    if (!otherMetrics.isEmpty()) {
                        if (Boolean.parseBoolean(System.getProperty("wildfly.multiple.metrics.warn", "true"))) {
                            LOGGER.multipleMetricsSystemsEnabled(String.join(",", otherMetrics));
                        }
                    }


                    ServiceController<?> serviceController = context.getServiceRegistry(false).getService(WILDFLY_COLLECTOR);
                    MetricCollector metricCollector = MetricCollector.class.cast(serviceController.getValue());
                    ServiceController<?> wildflyRegistryController = context.getServiceRegistry(false).getService(METRICS_REGISTRY_RUNTIME_CAPABILITY.getCapabilityServiceName());
                    WildFlyMetricRegistry metricRegistry = WildFlyMetricRegistry.class.cast(wildflyRegistryController.getValue());

                    ImmutableManagementResourceRegistration rootResourceRegistration = context.getRootResourceRegistration();
                    Resource rootResource = context.readResourceFromRoot(EMPTY_ADDRESS);

                    MetricRegistration registration = new MetricRegistration(metricRegistry);

                    metricCollector.collectResourceMetrics(rootResource, rootResourceRegistration, Function.identity(),
                            exposeAnySubsystem, exposedSubsystems, prefix,
                            registration);
                }
            }, VERIFY);
        }

        LOGGER.activatingSubsystem();
    }
}
