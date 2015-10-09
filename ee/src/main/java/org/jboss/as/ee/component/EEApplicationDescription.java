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

package org.jboss.as.ee.component;

import static org.jboss.modules.PathUtils.canonicalize;
import static org.jboss.modules.PathUtils.relativize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.server.loaders.ResourceLoader;

/**
 * @author John Bailey
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class EEApplicationDescription {
    //these are only written to by a single top level processor, so do not need to be synchronized
    private final Map<String, List<ViewInformation>> componentsByViewName = new HashMap<String, List<ViewInformation>>();
    private final Map<String, List<Description>> componentsByName = new HashMap<String, List<Description>>();

    //this must be synchronized for writing
    private final Map<String, List<MessageDestinationMapping>> messageDestinationJndiMapping = new HashMap<String, List<MessageDestinationMapping>>();

    /**
     * Add a component to this application.
     *
     * @param description    the component description
     * @param loader
     */
    public void addComponent(final ComponentDescription description, final ResourceLoader loader) {
        for (final ViewDescription viewDescription : description.getViews()) {
            List<ViewInformation> viewComponents = componentsByViewName.get(viewDescription.getViewClassName());
            if (viewComponents == null) {
                viewComponents = new ArrayList<>(1);
                componentsByViewName.put(viewDescription.getViewClassName(), viewComponents);
            }
            viewComponents.add(new ViewInformation(viewDescription, loader, description.getComponentName()));
        }
        List<Description> components = componentsByName.get(description.getComponentName());
        if (components == null) {
            componentsByName.put(description.getComponentName(), components = new ArrayList<Description>(1));
        }
        components.add(new Description(description, loader));
    }

    /**
     * Add a message destination to the application
     *
     * @param name           The message destination name
     * @param resolvedName   The resolved JNDI name
     * @param loader         The deployment loader
     */
    public void addMessageDestination(final String name, final String resolvedName, final ResourceLoader loader) {
        List<MessageDestinationMapping> components = messageDestinationJndiMapping.get(name);
        if (components == null) {
            messageDestinationJndiMapping.put(name, components = new ArrayList<>(1));
        }
        components.add(new MessageDestinationMapping(resolvedName, loader));
    }

    /**
     * Get all views that have the given type in the application
     *
     * @param viewType The view type
     * @return All views of the given type
     */
    public Set<ViewDescription> getComponentsForViewName(final String viewType, final ResourceLoader loader) {
        final List<ViewInformation> info = componentsByViewName.get(viewType);

        if (info == null) {
            return Collections.<ViewDescription>emptySet();
        }
        final Set<ViewDescription> ret = new HashSet<ViewDescription>();
        final Set<ViewDescription> currentDep = new HashSet<ViewDescription>();
        for (ViewInformation i : info) {
            if (loader == i.loader) {
                currentDep.add(i.viewDescription);
            }
            ret.add(i.viewDescription);
        }
        if(!currentDep.isEmpty()) {
            return currentDep;
        }
        return ret;
    }

    /**
     * Get all components in the application that have the given name
     *
     * @param componentName  The name of the component
     * @param loader         The loader of the component doing the lookup
     * @return A set of all views for the given component name and type
     */
    public Set<ComponentDescription> getComponents(final String componentName, final ResourceLoader loader) {
        if (componentName.contains("#")) {
            final String[] parts = componentName.split("#");
            String path = parts[0];
            path = relativize(canonicalize(path));
            path = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
            final String name = parts[1];
            final List<Description> info = componentsByName.get(name);
            if (info == null) {
                return Collections.emptySet();
            }
            final Set<ComponentDescription> ret = new HashSet<ComponentDescription>();
            for (Description i : info) {
                //now we need to check the path
                if (path.equals(i.loader.getPath())) {
                    ret.add(i.componentDescription);
                }
            }
            return ret;
        } else {
            final List<Description> info = componentsByName.get(componentName);
            if (info == null) {
                return Collections.emptySet();
            }
            final Set<ComponentDescription> all = new HashSet<ComponentDescription>();
            final Set<ComponentDescription> thisDeployment = new HashSet<ComponentDescription>();
            for (Description i : info) {
                all.add(i.componentDescription);
                if (i.loader == loader) {
                    thisDeployment.add(i.componentDescription);
                }
            }
            //if there are multiple e
            if (all.size() > 1) {
                return thisDeployment;
            }
            return all;
        }
    }

    /**
     * Get all views in the application that have the given name and view type
     *
     * @param componentName  The name of the component
     * @param viewName       The view type
     * @param loader         The loader of the component doing the lookup
     * @return A set of all views for the given component name and type
     */
    public Set<ViewDescription> getComponents(final String componentName, final String viewName, final ResourceLoader loader) {
        final List<ViewInformation> info = componentsByViewName.get(viewName);
        if (info == null) {
            return Collections.<ViewDescription>emptySet();
        }
        if (componentName.contains("#")) {
            final String[] parts = componentName.split("#");
            String path = parts[0];
            path = relativize(canonicalize(path));
            path = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
            final String name = parts[1];
            final Set<ViewDescription> ret = new HashSet<ViewDescription>();
            for (ViewInformation i : info) {
                if (i.beanName.equals(name)) {
                    //now we need to check the path
                    if (path.equals(i.loader.getPath())) {
                        ret.add(i.viewDescription);
                    }
                }
            }
            return ret;
        } else {
            final Set<ViewDescription> all = new HashSet<ViewDescription>();
            final Set<ViewDescription> thisDeployment = new HashSet<ViewDescription>();
            for (ViewInformation i : info) {
                if (i.beanName.equals(componentName)) {
                    all.add(i.viewDescription);
                    if (i.loader == loader) {
                        thisDeployment.add(i.viewDescription);
                    }
                }
            }
            if (all.size() > 1) {
                return thisDeployment;
            }
            return all;
        }
    }

    /**
     * Resolves a message destination name into a JNDI name
     */
    public Set<String> resolveMessageDestination(final String messageDestName, final ResourceLoader loader) {

        if (messageDestName.contains("#")) {
            final String[] parts = messageDestName.split("#");
            String path = parts[0];
            path = relativize(canonicalize(path));
            path = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
            final String name = parts[1];
            final Set<String> ret = new HashSet<String>();
            final List<MessageDestinationMapping> data = messageDestinationJndiMapping.get(name);
            if (data != null) {
                for (final MessageDestinationMapping i : data) {
                    //now we need to check the path
                    if (path.equals(i.loader.getPath())) {
                        ret.add(i.jndiName);
                    }
                }
            }
            return ret;
        } else {
            final Set<String> all = new HashSet<String>();
            final Set<String> thisDeployment = new HashSet<String>();
            final List<MessageDestinationMapping> data = messageDestinationJndiMapping.get(messageDestName);
            if (data != null) {
                for (final MessageDestinationMapping i : data) {
                    all.add(i.jndiName);
                    if (i.loader == loader) {
                        thisDeployment.add(i.jndiName);
                    }
                }
            }
            if (all.size() > 1) {
                return thisDeployment;
            }
            return all;
        }
    }

    private static class ViewInformation {
        private final ViewDescription viewDescription;
        private final ResourceLoader loader;
        private final String beanName;

        public ViewInformation(final ViewDescription viewDescription, final ResourceLoader loader, final String beanName) {
            this.viewDescription = viewDescription;
            this.loader = loader;
            this.beanName = beanName;
        }
    }

    private static class Description {
        private final ComponentDescription componentDescription;
        private final ResourceLoader loader;

        public Description(final ComponentDescription componentDescription, final ResourceLoader loader) {
            this.componentDescription = componentDescription;
            this.loader = loader;
        }
    }

    private static final class MessageDestinationMapping {
        private final String jndiName;
        private final ResourceLoader loader;

        public MessageDestinationMapping(final String jndiName, final ResourceLoader loader) {
            this.jndiName = jndiName;
            this.loader = loader;
        }
    }

}
