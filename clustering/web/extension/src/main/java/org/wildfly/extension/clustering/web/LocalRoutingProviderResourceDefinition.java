/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.clustering.web;

import java.util.function.UnaryOperator;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.dmr.ModelNode;
import org.wildfly.extension.clustering.web.routing.LocalRoutingProvider;
import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.capability.CapabilityServiceInstaller;

/**
 * Definition of the /subsystem=distributable-web/routing=local resource.
 * @author Paul Ferraro
 */
public class LocalRoutingProviderResourceDefinition extends RoutingProviderResourceDefinition {

    static final PathElement PATH = pathElement("local");

    LocalRoutingProviderResourceDefinition() {
        super(PATH, UnaryOperator.identity());
    }

    @Override
    public ResourceServiceInstaller configure(OperationContext context, ModelNode model) throws OperationFailedException {
        return CapabilityServiceInstaller.builder(ROUTING_PROVIDER, new LocalRoutingProvider()).build();
    }
}
