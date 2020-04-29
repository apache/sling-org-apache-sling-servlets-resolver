/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 ~ Licensed to the Apache Software Foundation (ASF) under one
 ~ or more contributor license agreements.  See the NOTICE file
 ~ distributed with this work for additional information
 ~ regarding copyright ownership.  The ASF licenses this file
 ~ to you under the Apache License, Version 2.0 (the
 ~ "License"); you may not use this file except in compliance
 ~ with the License.  You may obtain a copy of the License at
 ~
 ~   http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing,
 ~ software distributed under the License is distributed on an
 ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 ~ KIND, either express or implied.  See the License for the
 ~ specific language governing permissions and limitations
 ~ under the License.
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
package org.apache.sling.servlets.resolver.bundle.tracker.internal;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.hooks.service.EventListenerHook;
import org.osgi.framework.hooks.service.FindHook;
import org.osgi.framework.hooks.service.ListenerHook;
import org.osgi.service.component.annotations.Component;

@Component
public class BundledHooks implements FindHook, EventListenerHook {
    @Override
    public void find(BundleContext context, String name, String filter, boolean allServices, Collection<ServiceReference<?>> references) {
        if (!context.getBundle().getSymbolicName().equals("org.apache.sling.servlets.resolver")) {
            for (Iterator<ServiceReference<?>> iter = references.iterator(); iter.hasNext();) {
                if (iter.next().getProperty(BundledHooks.class.getName()) != null) {
                    iter.remove();
                }
            }
        }
    }


    @Override
    public void event(ServiceEvent event, Map<BundleContext, Collection<ListenerHook.ListenerInfo>> listeners) {
        if (event.getServiceReference().getProperty(BundledHooks.class.getName()) != null) {
            for (Iterator<Map.Entry<BundleContext, Collection<ListenerHook.ListenerInfo>>> entries = listeners.entrySet().iterator(); entries.hasNext();) {
                if (!entries.next().getKey().getBundle().getSymbolicName().equals("org.apache.sling.servlets.resolver")) {
                    entries.remove();
                }
            }
        }
    }
}
