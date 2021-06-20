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
package org.apache.sling.servlets.resolver.internal;

import java.lang.reflect.Field;
import java.util.Map;

import javax.servlet.Servlet;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.wrappers.ResourceResolverWrapper;
import org.apache.sling.servlets.resolver.internal.resolution.ResolutionCache;
import org.apache.sling.servlets.resolver.internal.resource.MockServletResource;
import org.apache.sling.testing.resourceresolver.DefaultMockResourceFactory;
import org.apache.sling.testing.resourceresolver.MockResourceResolverFactory;
import org.apache.sling.testing.resourceresolver.MockResourceResolverFactoryOptions;
import org.junit.Before;
import org.mockito.Mockito;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

public abstract class SlingServletResolverTestBase {

    /**
     * Custom factory that will create a MockServletResource for
     * any resource whose path ends with .servlet and fallback
     * to the default factory otherwise.
     */
    private static final class ServletMockResourceFactory extends DefaultMockResourceFactory {
        @Override
        public Resource newMockResource(String path, Map<String, Object> properties,
                ResourceResolver resolver) {
            if (path.endsWith(".servlet")) {
                Servlet servlet = (Servlet)properties.get(MockServletResource.PROP_SERVLET);
                return new MockServletResource(resolver, servlet, path);
            }
            return super.newMockResource(path, properties, resolver);
        }
    }

    protected SlingServletResolver servletResolver;

    protected ResourceResolver mockResourceResolver;

    @Before public void setUp() throws Exception {
        final ResolverConfig config = Mockito.mock(ResolverConfig.class);
        Mockito.when(config.servletresolver_servletRoot()).thenReturn("0");
        Mockito.when(config.servletresolver_paths()).thenReturn(new String[] { "/"});
        Mockito.when(config.servletresolver_defaultExtensions()).thenReturn(new String[] {"html"});
        Mockito.when(config.servletresolver_cacheSize()).thenReturn(200);

        MockResourceResolverFactoryOptions options = new MockResourceResolverFactoryOptions()
            .setSearchPaths(new String[] {"/"})
            .setMockResourceFactory(new ServletMockResourceFactory());

        MockResourceResolverFactory factory = new MockResourceResolverFactory(options);
        mockResourceResolver = new ResourceResolverWrapper(factory.getResourceResolver(null));

        servletResolver = new SlingServletResolver();

        Class<?> resolverClass = servletResolver.getClass();

        // set resource resolver factory
        final Field resolverField = resolverClass.getDeclaredField("resourceResolverFactory");
        resolverField.setAccessible(true);
        resolverField.set(servletResolver, factory);

        // set cache
        final Field cacheField = resolverClass.getDeclaredField("resolutionCache");
        cacheField.setAccessible(true);
        cacheField.set(servletResolver, new ResolutionCache());

        final Bundle bundle = Mockito.mock(Bundle.class);
        Mockito.when(bundle.getBundleId()).thenReturn(1L);

        final BundleContext bundleContext = Mockito.mock(BundleContext.class);
        Mockito.when(bundle.getBundleContext()).thenReturn(bundleContext);

        defineTestServlets(bundle);
        servletResolver.activate(bundleContext, config);

    }

    protected abstract void defineTestServlets(Bundle bundle);

    protected String getRequestWorkspaceName() {
        return "fromRequest";
    }

}
