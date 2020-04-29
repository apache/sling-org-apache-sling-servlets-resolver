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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.hooks.service.ListenerHook;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BundledHooksTest {
    @Test
    public void testFindHookFiltersOther() {
        BundledHooks hooks = new BundledHooks();

        BundleContext context = mock(BundleContext.class);
        Bundle bundle = mock(Bundle.class);
        ServiceReference<?> ref = mock(ServiceReference.class);

        when(context.getBundle()).thenReturn(bundle);
        when(bundle.getSymbolicName()).thenReturn("org.apache.sling.foo.bar");
        when(ref.getProperty(BundledHooks.class.getName())).thenReturn("true");
        List<ServiceReference<?>> services = new ArrayList<>();

        services.add(ref);
        hooks.find(context, null, null, true, services);

        Assert.assertTrue(services.isEmpty());
    }

    @Test
    public void testFindHookDoesNotFilterResolver() {
        BundledHooks hooks = new BundledHooks();

        BundleContext context = mock(BundleContext.class);
        Bundle bundle = mock(Bundle.class);
        ServiceReference<?> ref = mock(ServiceReference.class);

        when(context.getBundle()).thenReturn(bundle);
        when(bundle.getSymbolicName()).thenReturn("org.apache.sling.servlets.resolver");
        when(ref.getProperty(BundledHooks.class.getName())).thenReturn("true");
        List<ServiceReference<?>> services = new ArrayList<>();

        services.add(ref);
        hooks.find(context, null, null, true, services);

        Assert.assertEquals(1, services.size());
    }

    @Test
    public void testEventHookFiltersOther() {
        BundledHooks hooks = new BundledHooks();

        ServiceEvent event = mock(ServiceEvent.class);

        BundleContext context = mock(BundleContext.class);
        Bundle bundle = mock(Bundle.class);
        ServiceReference ref = mock(ServiceReference.class);

        when(event.getServiceReference()).thenReturn(ref);

        when(context.getBundle()).thenReturn(bundle);
        when(bundle.getSymbolicName()).thenReturn("org.apache.sling.foo.bar");
        when(ref.getProperty(BundledHooks.class.getName())).thenReturn("true");

        ListenerHook.ListenerInfo info = mock(ListenerHook.ListenerInfo.class);
        when(info.getBundleContext()).thenReturn(context);
        Map<BundleContext, Collection<ListenerHook.ListenerInfo>> listeners = new HashMap<>();
        listeners.put(context, new ArrayList<>(Arrays.asList(info)));

        hooks.event(event, listeners);

        Assert.assertTrue(listeners.isEmpty());
    }

    @Test
    public void testEventHookDoesNotFilterResolver() {
        BundledHooks hooks = new BundledHooks();

        ServiceEvent event = mock(ServiceEvent.class);

        BundleContext context = mock(BundleContext.class);
        Bundle bundle = mock(Bundle.class);
        ServiceReference ref = mock(ServiceReference.class);

        when(event.getServiceReference()).thenReturn(ref);

        when(context.getBundle()).thenReturn(bundle);
        when(bundle.getSymbolicName()).thenReturn("org.apache.sling.servlets.resolver");
        when(ref.getProperty(BundledHooks.class.getName())).thenReturn("true");

        ListenerHook.ListenerInfo info = mock(ListenerHook.ListenerInfo.class);
        when(info.getBundleContext()).thenReturn(context);
        Map<BundleContext, Collection<ListenerHook.ListenerInfo>> listeners = new HashMap<>();
        listeners.put(context, new ArrayList<>(Arrays.asList(info)));

        hooks.event(event, listeners);

        Assert.assertEquals(1, listeners.size());

        Assert.assertEquals(1, listeners.get(context).size());
    }
}
