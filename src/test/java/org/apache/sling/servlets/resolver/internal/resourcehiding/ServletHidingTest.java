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
package org.apache.sling.servlets.resolver.internal.resourcehiding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.Servlet;

import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.testing.sling.MockSlingHttpServletRequest;
import org.apache.sling.servlets.resolver.api.IgnoredResourcePredicate;
import org.apache.sling.servlets.resolver.internal.SlingServletResolverTestBase;
import org.apache.sling.servlets.resolver.internal.helper.HelperTestBase;
import org.apache.sling.servlets.resolver.internal.resource.MockServletResource;
import org.junit.Test;
import org.osgi.framework.Bundle;

public class ServletHidingTest extends SlingServletResolverTestBase {

    private static final String TEST_ID = UUID.randomUUID().toString();

    protected static class TestServlet extends SlingSafeMethodsServlet {
        private final String id;

        public TestServlet(String id) {
            this.id = id;
        }

        public String toString() {
            return id;
        }
    }

    private void setServletHidingFilter(IgnoredResourcePredicate predicate) throws Exception {
        final Field predicateField = servletResolver.getClass().getDeclaredField("ignoredResourcePredicate");
        predicateField.setAccessible(true);
        predicateField.set(servletResolver, predicate);
    }

    private void registerServlet(String id, String resourceType) {
        final String path = "/" + resourceType + "/" + ResourceUtil.getName(resourceType) + ".servlet";
        Map<String, Object> props = new HashMap<>();
        props.put(MockServletResource.PROP_SERVLET, new TestServlet(id));
        HelperTestBase.addOrReplaceResource(mockResourceResolver, path, props);
        try {
            mockResourceResolver.commit();
        } catch (PersistenceException e) {
            fail(e.toString());
        }
    }

    private Servlet resolveServlet() {
        MockSlingHttpServletRequest req = new MockSlingHttpServletRequest(
                MockSlingHttpServletRequest.RESOURCE_TYPE, null, "html", null, null);
        req.setResourceResolver(mockResourceResolver);
        return servletResolver.resolveServlet(req);
    }

    @Override
    protected void defineTestServlets(Bundle bundle) {
        registerServlet(TEST_ID, MockSlingHttpServletRequest.RESOURCE_TYPE);
    }

    private void assertResolvesToTestServletId(String info, boolean expectMatch) {
        final Servlet s = resolveServlet();
        assertNotNull("Expecting non-null Servlet", s);
        if(expectMatch) {
            assertEquals("Expecting our test servlet (" + info + ")", TEST_ID, s.toString());
        } else {
            assertNotEquals("NOT expecting our test servlet (" + info + ")", TEST_ID, s.toString());
        }
    }

    @Test
    public void testHideAndSeek() throws Exception {
        final AtomicBoolean hide = new AtomicBoolean();
        final IgnoredResourcePredicate pred = r -> hide.get();

        // No filtering
        setServletHidingFilter(null);
        assertResolvesToTestServletId("before hiding", true);

        // Filter with our predicate
        setServletHidingFilter(pred);
        hide.set(true);
        assertResolvesToTestServletId("hidden by our Predicate", false);
        hide.set(false);
        assertResolvesToTestServletId("Predicate active but returns false", true);

        // Back to no filtering, (paranoid) check that it's really gone
        setServletHidingFilter(null);
        hide.set(false);
        assertResolvesToTestServletId("No Predicate set, hide=false", true);
        hide.set(true);
        assertResolvesToTestServletId("No Predicate set, hide=true", true);
    }

}
