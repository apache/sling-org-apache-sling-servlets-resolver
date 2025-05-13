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

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.builder.Builders;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.servlets.resolver.internal.SlingServletResolverTestBase;
import org.apache.sling.servlets.resolver.internal.helper.HelperTestBase;
import org.apache.sling.servlets.resolver.internal.resource.MockServletResource;
import org.apache.sling.servlets.resolver.internal.resource.ServletResource;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.Bundle;

import static org.junit.Assert.fail;

public class ServletHidingTest extends SlingServletResolverTestBase {

    private static final String SERVLET_EXTENSION = "html";
    private static final String RESOURCE_TYPE =
            ServletHidingTest.class.getSimpleName() + "/" + UUID.randomUUID().toString();

    protected static class TestServlet extends HttpServlet {}
    ;

    private void setServletHidingFilter(Predicate<String> predicate) throws Exception {
        final Field predicateField = servletResolver.getClass().getDeclaredField("resourceHidingPredicate");
        predicateField.setAccessible(true);
        predicateField.set(servletResolver, predicate);
    }

    @Override
    protected void defineTestServlets(Bundle bundle) {
        final TestServlet testServlet = new TestServlet();
        String path = "/" + RESOURCE_TYPE + "/" + ResourceUtil.getName(RESOURCE_TYPE) + ".servlet";
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

    private void assertResolvesToTestServlet(String info, boolean expectMatch) {
        final Resource resource = Mockito.mock(Resource.class);
        Mockito.when(resource.getResourceType()).thenReturn(RESOURCE_TYPE);
        Mockito.when(resource.getPath()).thenReturn("/" + RESOURCE_TYPE);

        final SlingHttpServletRequest request = Builders.newRequestBuilder(resource)
                .withExtension(SERVLET_EXTENSION)
                .build();
        final Servlet s = servletResolver.resolveServlet(request);
        if (expectMatch != s.getClass().equals(TestServlet.class)) {
            if (expectMatch) {
                fail(info + ": expected to resolve to our test servlet, got "
                        + s.getClass().getName());
            } else {
                fail(info + ": didn't expect to resolve to our test servlet");
            }
        }
    }

    @Test
    public void testHideAndSeek() throws Exception {
        final AtomicBoolean hide = new AtomicBoolean();
        final Predicate<String> pred = (ignoredPath) -> hide.get();

        // No filtering
        setServletHidingFilter(null);
        assertResolvesToTestServlet("before hiding", true);

        // Filter with our predicate
        setServletHidingFilter(pred);
        hide.set(true);
        assertResolvesToTestServlet("hidden by our Predicate", false);
        hide.set(false);
        assertResolvesToTestServlet("Predicate active but returns false", true);

        // Back to no filtering, (paranoid) check that it's really gone
        setServletHidingFilter(null);
        hide.set(false);
        assertResolvesToTestServlet("No Predicate set, hide=false", true);
        hide.set(true);
        assertResolvesToTestServlet("No Predicate set, hide=true", true);
    }
}
