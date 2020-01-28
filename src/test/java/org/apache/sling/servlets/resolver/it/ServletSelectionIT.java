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
package org.apache.sling.servlets.resolver.it;

import static org.junit.Assert.assertTrue;

import org.apache.sling.servlethelpers.MockSlingHttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class ServletSelectionIT extends ServletResolverTestSupport {

    @Before
    public void setupTestServlets() throws Exception {

        new TestServlet("FooPathServlet")
        .with(P_PATHS, "/foo")
        .register(bundleContext);

        new TestServlet("AllExceptPathsIgnored")
        .with(P_PATHS, "/allprops")
        .with(P_RESOURCE_TYPES, "allresource")
        .with(P_METHODS, "allmethod")
        .with(P_EXTENSIONS, "allext")
        .with(P_SELECTORS, "allsel")
        .register(bundleContext);

        new TestServlet("ExtServlet")
        .with(P_RESOURCE_TYPES, RT_DEFAULT)
        .with(P_METHODS, M_GET)
        .with(P_EXTENSIONS, "testext")
        .register(bundleContext);

        new TestServlet("ExtPostServlet")
        .with(P_RESOURCE_TYPES, RT_DEFAULT)
        .with(P_METHODS, M_POST)
        .with(P_EXTENSIONS, "testext")
        .register(bundleContext);

        new TestServlet("ExtSelServlet")
        .with(P_RESOURCE_TYPES, RT_DEFAULT)
        .with(P_METHODS, M_GET)
        .with(P_EXTENSIONS, "testext")
        .with(P_SELECTORS, "testsel")
        .register(bundleContext);
    }

    @Test
    public void testDefaultJsonServlet() throws Exception {
        final MockSlingHttpServletResponse response = executeRequest("/.json", 200);
        final String content = response.getOutputAsString();
        final String [] expected = {
            "jcr:primaryType\":\"rep:root",
            "jcr:mixinTypes\":[\"rep:AccessControllable\"]"
        };
        for(final String s : expected) {
            assertTrue("Expecting in output: " + s + ", got " + content, content.contains(s));
        }

    }

    @Test
    public void testFooPathServlet() throws Exception {
        assertTestServlet("/foo", "FooPathServlet");
    }

    @Test
    public void testFooPathServletWithSelectorAndExtension() throws Exception {
        assertTestServlet("/foo.someExtension", "FooPathServlet");
        assertTestServlet("/foo.someSelector.someExtension", "FooPathServlet");
        assertTestServlet("/foo.anotherSelector.someSelector.someExtension", "FooPathServlet");
    }

    @Test
    public void testFooPathServletWithPathSuffix() throws Exception {
        executeRequest("/foo/path/suffix", 404);
        assertTestServlet("/foo.someExtensions/path/suffix", "FooPathServlet");
        assertTestServlet("/foo.someSelector.someExtension/path/suffix", "FooPathServlet");
    }

    @Test
    public void testExtServlet() throws Exception {
        assertTestServlet("/.testext", "ExtServlet");
    }

    @Test
    public void testExtSelServlet() throws Exception {
        assertTestServlet("/.testsel.testext", "ExtSelServlet");
    }

    @Test
    public void testNoServletForExtension() throws Exception {
        executeRequest("/.yapas", 404);
    }

    @Test
    public void testExtPostServlet() throws Exception {
        assertTestServlet(M_POST, "/.testext", "ExtPostServlet");
    }

    @Test
    public void testAllExceptPathsIgnored() throws Exception {
        assertTestServlet("/allprops", "AllExceptPathsIgnored");
        assertTestServlet("/allprops.zero", "AllExceptPathsIgnored");
        assertTestServlet("/allprops.one.two", "AllExceptPathsIgnored");
        assertTestServlet(M_POST, "/allprops", "AllExceptPathsIgnored");
        assertTestServlet(M_POST, "/allprops.ext", "AllExceptPathsIgnored");
        assertTestServlet(M_POST, "/allprops.three.four", "AllExceptPathsIgnored");
        assertTestServlet("/allprops.five.six/suffix", "AllExceptPathsIgnored");
        assertTestServlet(M_POST, "/allprops.seven.eight/suffix", "AllExceptPathsIgnored");
        assertTestServlet(M_POST, "/allprops.nine/suffix", "AllExceptPathsIgnored");
    }
}