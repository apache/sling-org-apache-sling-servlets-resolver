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

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.servlets.OptingServlet;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.commons.testing.osgi.MockServiceReference;
import org.apache.sling.commons.testing.sling.MockResource;
import org.apache.sling.commons.testing.sling.MockResourceResolver;
import org.apache.sling.commons.testing.sling.MockSlingHttpServletRequest;
import org.apache.sling.servlets.resolver.internal.resolution.ResolutionCache;
import org.apache.sling.servlets.resolver.internal.resource.MockServletResource;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;

public class SlingServletResolverTest {

    private Servlet servlet;

    private SlingServletResolver servletResolver;

    public static final String SERVLET_PATH = "/mock";

    public static final String SERVLET_NAME = "TestServlet";

    private static final String SERVLET_EXTENSION = "html";

    private MockResourceResolver mockResourceResolver;

    @Before public void setUp() throws Exception {
        final ResolverConfig config = Mockito.mock(ResolverConfig.class);
        Mockito.when(config.servletresolver_servletRoot()).thenReturn("0");
        Mockito.when(config.servletresolver_paths()).thenReturn(new String[] { "/"});
        Mockito.when(config.servletresolver_defaultExtensions()).thenReturn(new String[] {"html"});
        Mockito.when(config.servletresolver_cacheSize()).thenReturn(200);

        mockResourceResolver = new MockResourceResolver() {
            @Override
            public void close() {
                // nothing to do;
            }

            @Override
            public <AdapterType> AdapterType adaptTo(Class<AdapterType> type) {
                return null;
            }

            @Override
            public ResourceResolver clone(Map<String, Object> authenticationInfo)
                    throws LoginException {
                throw new LoginException("MockResourceResolver can't be cloned - excepted for this test!");
            }

            @Override
            public void refresh() {
                // nothing to do
            }
        };
        mockResourceResolver.setSearchPath("/");

        final ResourceResolverFactory factory = new ResourceResolverFactory() {

            @Override
            public ResourceResolver getAdministrativeResourceResolver(
                    Map<String, Object> authenticationInfo)
                    throws LoginException {
                return mockResourceResolver;
            }

            @Override
            public ResourceResolver getResourceResolver(
                    Map<String, Object> authenticationInfo)
                    throws LoginException {
                return mockResourceResolver;
            }

            @Override
            public ResourceResolver getServiceResourceResolver(Map<String, Object> authenticationInfo)
                    throws LoginException {
                return mockResourceResolver;
            }

            @Override
            public ResourceResolver getThreadResourceResolver() {
                // TODO Auto-generated method stub
                return null;
            }

            @Override
            public List<String> getSearchPath() {
                return Collections.singletonList("/");
            }
        };

        servlet = new MockSlingRequestHandlerServlet();
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

        MockServiceReference serviceReference = new MockServiceReference(bundle);
        serviceReference.setProperty(Constants.SERVICE_ID, 1L);
        serviceReference.setProperty(ServletResolverConstants.SLING_SERVLET_NAME,
            SERVLET_NAME);
        serviceReference.setProperty(
                ServletResolverConstants.SLING_SERVLET_PATHS, SERVLET_PATH);
        serviceReference.setProperty(
            ServletResolverConstants.SLING_SERVLET_EXTENSIONS,
            SERVLET_EXTENSION);

        servletResolver.activate(config);

        String path = "/"
            + MockSlingHttpServletRequest.RESOURCE_TYPE
            + "/"
            + ResourceUtil.getName(MockSlingHttpServletRequest.RESOURCE_TYPE)
            + ".servlet";
        MockServletResource res = new MockServletResource(mockResourceResolver,
            servlet, path);
        mockResourceResolver.addResource(res);

        MockResource parent = new MockResource(mockResourceResolver,
            ResourceUtil.getParent(res.getPath()), "nt:folder");
        mockResourceResolver.addResource(parent);

        List<Resource> childRes = new ArrayList<>();
        childRes.add(res);
        mockResourceResolver.addChildren(parent, childRes);
    }

    protected String getRequestWorkspaceName() {
        return "fromRequest";
    }

    @Test public void testAcceptsRequest() {
        MockSlingHttpServletRequest secureRequest = new MockSlingHttpServletRequest(
            SERVLET_PATH, null, SERVLET_EXTENSION, null, null);
        secureRequest.setResourceResolver(mockResourceResolver);
        secureRequest.setSecure(true);
        Servlet result = servletResolver.resolveServlet(secureRequest);
        assertEquals("Did not resolve to correct servlet", servlet, result);
    }

    @Test public void testIgnoreRequest() {
        MockSlingHttpServletRequest insecureRequest = new MockSlingHttpServletRequest(
            SERVLET_PATH, null, SERVLET_EXTENSION, null, null);
        insecureRequest.setResourceResolver(mockResourceResolver);
        insecureRequest.setSecure(false);
        Servlet result = servletResolver.resolveServlet(insecureRequest);
        assertTrue("Did not ignore unwanted request",
            result.getClass() != MockSlingRequestHandlerServlet.class);
    }

    /**
     * This sample servlet will only handle secure requests.
     *
     * @see org.apache.sling.api.servlets.OptingServlet#accepts
     */
    @SuppressWarnings("serial")
    private static class MockSlingRequestHandlerServlet extends HttpServlet
            implements OptingServlet {

        @Override
        public boolean accepts(SlingHttpServletRequest request) {
            return request.isSecure();
        }
    }

}
