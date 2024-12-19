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

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;

import java.util.HashMap;
import java.util.Map;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.servlets.OptingServlet;
import org.apache.sling.commons.testing.sling.MockSlingHttpServletRequest;
import org.apache.sling.servlets.resolver.internal.helper.HelperTestBase;
import org.apache.sling.servlets.resolver.internal.resource.MockServletResource;
import org.apache.sling.servlets.resolver.internal.resource.ServletResource;
import org.junit.Test;
import org.osgi.framework.Bundle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.fail;

public class SecureRequestsOptingServletTest extends SlingServletResolverTestBase {

    protected static final String SERVLET_PATH = "/mock";
    protected static final String SERVLET_NAME = "TestServlet";
    protected static final String SERVLET_EXTENSION = "html";
    private Servlet testServlet;

    protected void defineTestServlets(Bundle bundle) {
        testServlet = new SecureRequestsOptingServlet();

        String path = "/"
                + MockSlingHttpServletRequest.RESOURCE_TYPE
                + "/"
                + ResourceUtil.getName(MockSlingHttpServletRequest.RESOURCE_TYPE)
                + ".servlet";
        Map<String, Object> props = new HashMap<>();
        props.put(ResourceResolver.PROPERTY_RESOURCE_TYPE, path);
        props.put("sling:resourceSuperType", ServletResource.DEFAULT_RESOURCE_SUPER_TYPE);
        props.put(MockServletResource.PROP_SERVLET, testServlet);
        HelperTestBase.addOrReplaceResource(mockResourceResolver, path, props);
        try {
            // commit so the resource is visible to the script resource resolver
            //  that is created later and can't see the temporary resources in
            //  this resource resolver
            mockResourceResolver.commit();
        } catch (PersistenceException e) {
            fail("Did not expect a persistence exception: " + e.getMessage());
        }
    }

    @Test
    public void testAcceptsSecureRequest() {
        MockSlingHttpServletRequest secureRequest =
                new MockSlingHttpServletRequest(SERVLET_PATH, null, SERVLET_EXTENSION, null, null);
        secureRequest.setResourceResolver(mockResourceResolver);
        secureRequest.setSecure(true);
        Servlet result = servletResolver.resolveServlet(secureRequest);
        assertEquals("Expecting our test servlet", testServlet, result);
    }

    @Test
    public void testIgnoreInsecureRequest() {
        MockSlingHttpServletRequest insecureRequest =
                new MockSlingHttpServletRequest(SERVLET_PATH, null, SERVLET_EXTENSION, null, null);
        insecureRequest.setResourceResolver(mockResourceResolver);
        insecureRequest.setSecure(false);
        Servlet result = servletResolver.resolveServlet(insecureRequest);
        assertNotSame(
                "Expecting a different servlet than our own", result.getClass(), SecureRequestsOptingServlet.class);
    }

    @SuppressWarnings("serial")
    private static class SecureRequestsOptingServlet extends HttpServlet implements OptingServlet {

        @Override
        public boolean accepts(SlingHttpServletRequest request) {
            return request.isSecure();
        }
    }
}
