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
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Set;

import javax.servlet.GenericServlet;
import javax.servlet.Servlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.spi.resource.provider.ResolveContext;
import org.apache.sling.spi.resource.provider.ResourceContext;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;

public class ServletResourceProviderCreateTest {

    private static final Servlet TEST_SERVLET = new GenericServlet() {
        private static final long serialVersionUID = 1L;

        @Override
        public void service(ServletRequest req, ServletResponse res) {
        }
    };

    private static final String ROOT = "/apps/";

    private static final String RES_TYPE = "sling:sample";

    private static final String RES_TYPE_PATH = ResourceUtil.resourceTypeToPath(RES_TYPE);

    private ServletResourceProviderFactory factory = new ServletResourceProviderFactory(
        ROOT, Collections.singletonList("/apps/"));

    @Test public void testCreateMethodsDefault() {
        @SuppressWarnings("unchecked")
        final ServiceReference<Servlet> msr = Mockito.mock(ServiceReference.class);
        Mockito.when(msr.getProperty(Constants.SERVICE_ID))
           .thenReturn(1L);
        Mockito.when(msr.getProperty(ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES))
           .thenReturn(RES_TYPE);

        final ServletResourceProvider srp = factory.create(msr, TEST_SERVLET);
        final Set<String> paths = srp.getServletPaths();
        assertEquals(2, paths.size());
        assertTrue(paths.contains(ROOT + RES_TYPE_PATH + "/" + HttpConstants.METHOD_GET
            + ServletResourceProviderFactory.SERVLET_PATH_EXTENSION));
        assertTrue(paths.contains(ROOT + RES_TYPE_PATH + "/" + HttpConstants.METHOD_HEAD
            + ServletResourceProviderFactory.SERVLET_PATH_EXTENSION));
    }

    @Test public void testCreateMethodsSingle() {
        @SuppressWarnings("unchecked")
        final ServiceReference<Servlet> msr = Mockito.mock(ServiceReference.class);
        Mockito.when(msr.getProperty(Constants.SERVICE_ID))
           .thenReturn(1L);
        Mockito.when(msr.getProperty(ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES))
           .thenReturn(RES_TYPE);
        Mockito.when(msr.getProperty(ServletResolverConstants.SLING_SERVLET_METHODS))
            .thenReturn("GET");

        final ServletResourceProvider srp = factory.create(msr, TEST_SERVLET);
        final Set<String> paths = srp.getServletPaths();
        assertEquals(1, paths.size());
        assertTrue(paths.contains(ROOT + RES_TYPE_PATH + "/" + HttpConstants.METHOD_GET
            + ServletResourceProviderFactory.SERVLET_PATH_EXTENSION));
    }

    @Test public void testCreateMethodsMultiple() {
        @SuppressWarnings("unchecked")
        final ServiceReference<Servlet> msr = Mockito.mock(ServiceReference.class);
        Mockito.when(msr.getProperty(Constants.SERVICE_ID))
           .thenReturn(1L);
        Mockito.when(msr.getProperty(ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES))
           .thenReturn(RES_TYPE);
        Mockito.when(msr.getProperty(ServletResolverConstants.SLING_SERVLET_METHODS))
            .thenReturn(new String[] { "GET", "POST", "PUT" });

        final ServletResourceProvider srp = factory.create(msr, TEST_SERVLET);
        final Set<String> paths = srp.getServletPaths();
        assertEquals(3, paths.size());
        assertTrue(paths.contains(ROOT + RES_TYPE_PATH + "/" + HttpConstants.METHOD_GET
            + ServletResourceProviderFactory.SERVLET_PATH_EXTENSION));
        assertTrue(paths.contains(ROOT + RES_TYPE_PATH + "/" + HttpConstants.METHOD_POST
            + ServletResourceProviderFactory.SERVLET_PATH_EXTENSION));
        assertTrue(paths.contains(ROOT + RES_TYPE_PATH + "/" + HttpConstants.METHOD_PUT
            + ServletResourceProviderFactory.SERVLET_PATH_EXTENSION));
    }

    @Test public void testCreateMethodsAll() {
        @SuppressWarnings("unchecked")
        final ServiceReference<Servlet> msr = Mockito.mock(ServiceReference.class);
        Mockito.when(msr.getProperty(Constants.SERVICE_ID))
           .thenReturn(1L);
        Mockito.when(msr.getProperty(ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES))
           .thenReturn(RES_TYPE);
        Mockito.when(msr.getProperty(ServletResolverConstants.SLING_SERVLET_METHODS))
            .thenReturn("*");

        final ServletResourceProvider srp = factory.create(msr, TEST_SERVLET);
        final Set<String> paths = srp.getServletPaths();
        assertEquals(1, paths.size());
        assertTrue(paths.contains(ROOT + RES_TYPE_PATH
            + ServletResourceProviderFactory.SERVLET_PATH_EXTENSION));
    }

    @Test public void testCreateSelectorsExtensions() {
        @SuppressWarnings("unchecked")
        final ServiceReference<Servlet> msr = Mockito.mock(ServiceReference.class);
        Mockito.when(msr.getProperty(Constants.SERVICE_ID))
           .thenReturn(1L);
        Mockito.when(msr.getProperty(ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES))
           .thenReturn(RES_TYPE);
        Mockito.when(msr.getProperty(ServletResolverConstants.SLING_SERVLET_METHODS))
            .thenReturn(new String[] { HttpConstants.METHOD_GET });
        Mockito.when(msr.getProperty(ServletResolverConstants.SLING_SERVLET_SELECTORS))
            .thenReturn(new String[] { "ext" });
        Mockito.when(msr.getProperty(ServletResolverConstants.SLING_SERVLET_EXTENSIONS))
            .thenReturn(new String[] { "json" });

        final ServletResourceProvider srp = factory.create(msr, TEST_SERVLET);
        final Set<String> paths = srp.getServletPaths();
        assertEquals(1, paths.size());
        assertTrue(paths.contains(ROOT + RES_TYPE_PATH + "/ext.json."
            + HttpConstants.METHOD_GET
            + ServletResourceProviderFactory.SERVLET_PATH_EXTENSION));
    }

    @Test public void testCreateMethodsExtensions() {
        @SuppressWarnings("unchecked")
        final ServiceReference<Servlet> msr = Mockito.mock(ServiceReference.class);
        Mockito.when(msr.getProperty(Constants.SERVICE_ID))
           .thenReturn(1L);
        Mockito.when(msr.getProperty(ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES))
           .thenReturn(RES_TYPE);
        Mockito.when(msr.getProperty(ServletResolverConstants.SLING_SERVLET_METHODS))
            .thenReturn(new String[] { HttpConstants.METHOD_GET, HttpConstants.METHOD_POST });
        Mockito.when(msr.getProperty(ServletResolverConstants.SLING_SERVLET_EXTENSIONS))
            .thenReturn(new String[] { "json", "html" });

        final ServletResourceProvider srp = factory.create(msr, TEST_SERVLET);
        final Set<String> paths = srp.getServletPaths();
        assertEquals(4, paths.size());
        assertTrue(paths.contains(ROOT + RES_TYPE_PATH + "/json."
            + HttpConstants.METHOD_GET
            + ServletResourceProviderFactory.SERVLET_PATH_EXTENSION));
        assertTrue(paths.contains(ROOT + RES_TYPE_PATH + "/html."
            + HttpConstants.METHOD_GET
            + ServletResourceProviderFactory.SERVLET_PATH_EXTENSION));
        assertTrue(paths.contains(ROOT + RES_TYPE_PATH + "/json."
            + HttpConstants.METHOD_POST
            + ServletResourceProviderFactory.SERVLET_PATH_EXTENSION));
        assertTrue(paths.contains(ROOT + RES_TYPE_PATH + "/html."
            + HttpConstants.METHOD_POST
            + ServletResourceProviderFactory.SERVLET_PATH_EXTENSION));
    }

    @Test
    public void testCreateWithResourceSuperType() {
        @SuppressWarnings("unchecked")
        final ServiceReference<Servlet> msr = Mockito.mock(ServiceReference.class);
        Mockito.when(msr.getProperty(Constants.SERVICE_ID)).thenReturn(1L);
        Mockito.when(msr.getProperty(ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES)).thenReturn(RES_TYPE);
        Mockito.when(msr.getProperty(ServletResolverConstants.SLING_SERVLET_EXTENSIONS)).thenReturn(new String[] {"html"});
        Mockito.when(msr.getProperty(ServletResolverConstants.SLING_SERVLET_RESOURCE_SUPER_TYPE)).thenReturn(new String[] {"this/is/a" +
                "/test", "resource/two"});
        final ServletResourceProvider srp = factory.create(msr, TEST_SERVLET);
        final Set<String> paths = srp.getServletPaths();
        assertEquals(2, paths.size());
        assertTrue(paths.contains(ROOT + RES_TYPE_PATH));
        assertTrue(paths.contains(ROOT + RES_TYPE_PATH + "/html" + ServletResourceProviderFactory.SERVLET_PATH_EXTENSION));
        @SuppressWarnings("unchecked")
        Resource superTypeMarkingResource = srp.getResource(Mockito.mock(ResolveContext.class), "/apps/sling/sample",
                Mockito.mock(ResourceContext.class), Mockito.mock(Resource.class));
        assertNotNull(superTypeMarkingResource);
        assertEquals("this/is/a/test", superTypeMarkingResource.getResourceSuperType());
        assertNull(superTypeMarkingResource.adaptTo(Servlet.class));

        @SuppressWarnings("unchecked")
        Resource servletResource = srp.getResource(Mockito.mock(ResolveContext.class), "/apps/sling/sample/html.servlet",
                Mockito.mock(ResourceContext.class), Mockito.mock(Resource.class));
        assertNotNull(servletResource);
        assertEquals("this/is/a/test", servletResource.getResourceSuperType());
        assertEquals(TEST_SERVLET, servletResource.adaptTo(Servlet.class));
    }

}
