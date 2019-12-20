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

import java.util.ArrayList;
import java.util.List;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.servlets.OptingServlet;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.commons.testing.osgi.MockServiceReference;
import org.apache.sling.commons.testing.sling.MockResource;
import org.apache.sling.commons.testing.sling.MockResourceResolver;
import org.apache.sling.commons.testing.sling.MockSlingHttpServletRequest;
import org.apache.sling.servlets.resolver.internal.resource.MockServletResource;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;

public class SecureRequestsOptingServletTest extends SlingServletResolverTestBase {

    protected  static final String SERVLET_PATH = "/mock";
    protected  static final String SERVLET_NAME = "TestServlet";
    protected static final String SERVLET_EXTENSION = "html";
    private Servlet testServlet;

    protected void defineTestServlets(Bundle bundle) {
        testServlet = new SecureRequestsOptingServlet();

        MockServiceReference ref = new MockServiceReference(bundle);
        ref.setProperty(Constants.SERVICE_ID, 1L);
        ref.setProperty(ServletResolverConstants.SLING_SERVLET_NAME, SERVLET_NAME);
        ref.setProperty(ServletResolverConstants.SLING_SERVLET_PATHS, SERVLET_PATH);
        ref.setProperty(ServletResolverConstants.SLING_SERVLET_EXTENSIONS, SERVLET_EXTENSION);

        String path = "/"
            + MockSlingHttpServletRequest.RESOURCE_TYPE
            + "/"
            + ResourceUtil.getName(MockSlingHttpServletRequest.RESOURCE_TYPE)
            + ".servlet";
        MockServletResource res = new MockServletResource(mockResourceResolver,
            testServlet, path);
            mockResourceResolver.addResource(res);

        MockResource parent = new MockResource(mockResourceResolver,
            ResourceUtil.getParent(res.getPath()), "nt:folder");
            mockResourceResolver.addResource(parent);

        List<Resource> childRes = new ArrayList<>();
        childRes.add(res);
        mockResourceResolver.addChildren(parent, childRes);
    }

    @Test public void testAcceptsSecureRequest() {
        MockSlingHttpServletRequest secureRequest = new MockSlingHttpServletRequest(
            SERVLET_PATH, null, SERVLET_EXTENSION, null, null);
        secureRequest.setResourceResolver(mockResourceResolver);
        secureRequest.setSecure(true);
        Servlet result = servletResolver.resolveServlet(secureRequest);
        assertEquals("Expecting our test servlet", testServlet, result);
    }

    @Test public void testIgnoreInsecureRequest() {
        MockSlingHttpServletRequest insecureRequest = new MockSlingHttpServletRequest(
            SERVLET_PATH, null, SERVLET_EXTENSION, null, null);
        insecureRequest.setResourceResolver(mockResourceResolver);
        insecureRequest.setSecure(false);
        Servlet result = servletResolver.resolveServlet(insecureRequest);
        assertTrue("Expecting a different servlet than our own",
            result.getClass() != SecureRequestsOptingServlet.class);
    }

    @SuppressWarnings("serial")
    private static class SecureRequestsOptingServlet extends HttpServlet
            implements OptingServlet {

        @Override
        public boolean accepts(SlingHttpServletRequest request) {
            return request.isSecure();
        }
    }
}
