/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.servlets.resolver.internal.resource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Dictionary;

import javax.servlet.Servlet;

import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.servlets.resolver.internal.ResolverConfig;
import org.apache.sling.spi.resource.provider.ResourceProvider;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;

public class ServletMounterTest {

    private ServletMounter mounter;

    @Before public void setUp() throws Exception {
        final ResolverConfig config = Mockito.mock(ResolverConfig.class);
        Mockito.when(config.servletresolver_servletRoot()).thenReturn("0");
        Mockito.when(config.servletresolver_paths()).thenReturn(new String[] { "/"});
        Mockito.when(config.servletresolver_defaultExtensions()).thenReturn(new String[] {"html"});
        Mockito.when(config.servletresolver_cacheSize()).thenReturn(200);

        // create mock for resource resolver factory
        final ResourceResolverFactory factory = Mockito.mock(ResourceResolverFactory.class);
        Mockito.when(factory.getSearchPath()).thenReturn(Collections.singletonList("/"));

        // create mock bundle
        final Bundle bundle = Mockito.mock(Bundle.class);
        Mockito.when(bundle.getBundleId()).thenReturn(1L);

        // create mock bundle context
        final BundleContext bundleContext = Mockito.mock(BundleContext.class);
        Mockito.when(bundle.getBundleContext()).thenReturn(bundleContext);

        // create mounter
        this.mounter = new ServletMounter(bundleContext, factory, null, config);
    }


    @Test public void testCreateServiceProperties() throws Throwable {
        @SuppressWarnings("unchecked")
        final ServiceReference<Servlet> msr = Mockito.mock(ServiceReference.class);
        Mockito.when(msr.getProperty(ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES))
            .thenReturn("sample");
        Mockito.when(msr.getProperty(ServletResolverConstants.SLING_SERVLET_METHODS))
            .thenReturn("GET");

        Method createServiceProperties = ServletMounter.class.getDeclaredMethod("createServiceProperties",
                ServiceReference.class, String.class);
        createServiceProperties.setAccessible(true);

        // no ranking
        assertNull(msr.getProperty(Constants.SERVICE_RANKING));
        @SuppressWarnings("unchecked")
        final Dictionary<String, Object> p1 = (Dictionary<String, Object>) createServiceProperties.invoke(mounter, msr, "/a");
        assertEquals(2, p1.size());
        assertNull(p1.get(Constants.SERVICE_RANKING));
        assertEquals("/a", p1.get(ResourceProvider.PROPERTY_ROOT));
        assertNotNull(p1.get(Constants.SERVICE_DESCRIPTION));

        // illegal type of ranking
        Object nonIntValue = "Some Non Integer Value";
        Mockito.when(msr.getProperty(Constants.SERVICE_RANKING))
            .thenReturn(nonIntValue);
        assertEquals(nonIntValue, msr.getProperty(Constants.SERVICE_RANKING));
        @SuppressWarnings("unchecked")
        final Dictionary<String, Object> p2 = (Dictionary<String, Object>) createServiceProperties.invoke(mounter, msr, "/b");
        assertEquals(2, p2.size());
        assertNull(p2.get(Constants.SERVICE_RANKING));
        assertEquals("/b", p2.get(ResourceProvider.PROPERTY_ROOT));
        assertNotNull(p2.get(Constants.SERVICE_DESCRIPTION));

        // illegal type of ranking
        Object intValue = Integer.valueOf(123);
        Mockito.when(msr.getProperty(Constants.SERVICE_RANKING))
            .thenReturn(intValue);
        assertEquals(intValue, msr.getProperty(Constants.SERVICE_RANKING));
        @SuppressWarnings("unchecked")
        final Dictionary<String, Object> p3 = (Dictionary<String, Object>) createServiceProperties.invoke(mounter, msr, "/c");
        assertEquals(3, p3.size());
        assertEquals(intValue, p3.get(Constants.SERVICE_RANKING));
        assertEquals("/c", p3.get(ResourceProvider.PROPERTY_ROOT));
        assertNotNull(p3.get(Constants.SERVICE_DESCRIPTION));
    }
}
