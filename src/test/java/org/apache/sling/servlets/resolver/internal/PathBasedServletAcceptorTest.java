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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.servlets.resolver.internal.resource.SlingServletConfig;
import org.apache.sling.servlets.resolver.it.ServletResolverTestSupport;
import org.junit.Test;
import org.osgi.framework.ServiceReference;

public class PathBasedServletAcceptorTest {

    public static final String [] STRING_ARRAY = new String[0];

    private final PathBasedServletAcceptor acceptor = new PathBasedServletAcceptor();

    private class TestCase {
        private Dictionary<String, Object> serviceProperties = new Hashtable<>();
        private String extension;
        private List<String> selectors = new ArrayList<>();
        private String method;

        TestCase() {
            serviceProperties.put(ServletResolverTestSupport.P_STRICT_PATHS, true);
        }

        TestCase withServiceProperty(String key, Object value) {
            serviceProperties.put(key, value);
            return this;
        }

        TestCase withServiceProperty(String key, Object ... values) {
            serviceProperties.put(key, values);
            return this;
        }

        TestCase withExtension(String ext) {
            extension = ext;
            return this;
        }

        TestCase withSelector(String sel) {
            selectors.add(sel);
            return this;
        }

        TestCase withSelectors(String ... sels) {
            selectors.addAll(Arrays.asList(sels));
            return this;
        }

        TestCase withMethod(String m) {
            method = m;
            return this;
        }

        void assertAccept(boolean expected) {

            // Stub the ServiceReference with our service properties
            final ServiceReference<Servlet> reference = mock(ServiceReference.class);
            final String [] keys = Collections.list(serviceProperties.keys()).toArray(STRING_ARRAY);
            when(reference.getPropertyKeys()).thenReturn(keys);
            for(String key: keys) {
                when(reference.getProperty(key)).thenReturn(serviceProperties.get(key));
            }

            // Wire the Servlet to our ServiceReference
            final ServletContext sc = mock(ServletContext.class);
            final SlingServletConfig ssc = new SlingServletConfig(sc, reference, "42");
            final Servlet servlet = mock(Servlet.class);
            when(servlet.getServletConfig()).thenReturn(ssc);

            // Setup the request values
            final RequestPathInfo rpi = mock(RequestPathInfo.class);
            when(rpi.getExtension()).thenReturn(extension);
            when(rpi.getSelectors()).thenReturn(selectors.toArray(STRING_ARRAY));

            final SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
            when(request.getRequestPathInfo()).thenReturn(rpi);
            when(request.getMethod()).thenReturn(method);

            // And call the acceptor
            final boolean actual = acceptor.accept(request, servlet);
            assertEquals(expected, actual);
        }

    }

    @Test
    public void extensionNoMatch() {
        new TestCase()
        .withServiceProperty(ServletResolverTestSupport.P_EXTENSIONS, "sp")
        .withExtension("somethingElse")
        .assertAccept(false);
    }

    @Test
    public void extensionPropertyNotSet() {
        new TestCase()
        .withExtension("somethingElse")
        .assertAccept(true);
    }

    @Test
    public void selectorNoMatch() {
        new TestCase()
        .withServiceProperty(ServletResolverTestSupport.P_SELECTORS, "sel")
        .withSelector("somethingElse")
        .assertAccept(false);
    }

    @Test
    public void selectorOneMatchesOne() {
        new TestCase()
        .withServiceProperty(ServletResolverTestSupport.P_SELECTORS, "sel")
        .withSelector("sel")
        .assertAccept(true);
    }

    @Test
    public void selectorOneAmongSeveral() {
        new TestCase()
        .withServiceProperty(ServletResolverTestSupport.P_SELECTORS, "one")
        .withServiceProperty(ServletResolverTestSupport.P_SELECTORS, "two")
        .withServiceProperty(ServletResolverTestSupport.P_SELECTORS, "three")
        .withSelector("three")
        .assertAccept(true);
    }

    @Test
    public void selectorPropertyNotSet() {
        new TestCase()
        .withSelector("somethingElse")
        .assertAccept(true);
    }

    @Test
    public void methodNoMatch() {
        new TestCase()
        .withServiceProperty(ServletResolverTestSupport.P_METHODS, "meth")
        .withMethod("somethingElse")
        .assertAccept(false);
    }

    @Test
    public void methodPropertyNotSet() {
        new TestCase()
        .withMethod("somethingElse")
        .assertAccept(true);
    }

    @Test
    public void testStringStrict() {
        new TestCase()
        .withServiceProperty(ServletResolverTestSupport.P_STRICT_PATHS, "true")
        .withServiceProperty(ServletResolverTestSupport.P_METHODS, "meth")
        .withMethod("somethingElse")
        .assertAccept(false);
    }

    @Test
    public void testStringFalseStrict() {
        new TestCase()
        .withServiceProperty(ServletResolverTestSupport.P_STRICT_PATHS, "false")
        .withServiceProperty(ServletResolverTestSupport.P_METHODS, "meth")
        .withMethod("somethingElse")
        .assertAccept(true);
    }

    @Test
    public void testNoSlingServletConfig() {
        final Servlet s = mock(Servlet.class);
        when(s.getServletConfig()).thenReturn(mock(ServletConfig.class));
        assertTrue(acceptor.accept(null, s));
    }

}
