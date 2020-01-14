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

import org.apache.sling.testing.paxexam.TestSupport;
import org.junit.Before;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;

import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.wrappedBundle;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;
import static org.apache.sling.testing.paxexam.SlingOptions.slingQuickstartOakTar;
import static org.apache.sling.testing.paxexam.SlingOptions.versionResolver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.servlethelpers.MockSlingHttpServletRequest;
import org.apache.sling.servlethelpers.MockSlingHttpServletResponse;
import org.ops4j.pax.exam.options.CompositeOption;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

public class ServletResolverTestSupport extends TestSupport {
    @Inject
    private ResourceResolverFactory resourceResolverFactory;

    @Inject
    protected BundleContext bundleContext;

    protected final int httpPort = findFreePort();

    private final static int STARTUP_WAIT_SECONDS = 30;

    @Configuration
    public Option[] configuration() {
        return remove(
            options(
                baseConfiguration(),
                slingQuickstartOakTar(workingDirectory(), httpPort),
                mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.api").versionAsInProject(),
                mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.resourceresolver").version("1.6.16"), // compatible with API 2.22.0
                mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.scripting.core").version("2.1.1-SNAPSHOT"), // compatible with API 2.22.0 - TODO replace with release when available
                mavenBundle().groupId("org.apache.felix").artifactId("org.apache.felix.converter").version("1.0.12"), // new Sling API dependency
                testBundle("bundle.filename"),
                wrappedBundle(mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.servlet-helpers").versionAsInProject()),
                junitBundles(),
                newConfiguration("org.apache.sling.jcr.base.internal.LoginAdminWhitelist")
                    .put("whitelist.bundles.regexp", "^PAXEXAM.*$")
                    .asOption()
            ),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.api").version(versionResolver),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.resourceresolver").version(versionResolver),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.scripting.core").version(versionResolver),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.servlets.resolver").version(versionResolver) // remove bundle from slingQuickstartOakTar, added via testBundle in current version
        );
    }

    /** Injecting the appropriate services to wait for would be more elegant but this is very reliable.. */
    @Before
    public void waitForSling() throws Exception {
        final int expectedStatus = 200;
        final List<Integer> statuses = new ArrayList<>();
        final String path = "/.json";
        final long endTime = System.currentTimeMillis() + STARTUP_WAIT_SECONDS * 1000;

        while(System.currentTimeMillis() < endTime) {
            final int status = executeRequest(path, -1).getStatus();
            statuses.add(status);
            if(status == expectedStatus) {
                return;
            }
            Thread.sleep(250);
        }

        fail("Did not get a " + expectedStatus + " status at " + path + " got " + statuses);
    }


    protected MockSlingHttpServletResponse executeRequest(final String path, final int expectedStatus) throws Exception {
        final ResourceResolver resourceResolver = resourceResolverFactory.getAdministrativeResourceResolver(null);
        assertNotNull("Expecting ResourceResolver", resourceResolver);
        final MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resourceResolver);
        request.setPathInfo(path);
        final MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        // Get SlingRequestProcessor.processRequest method and execute request
        // This module depends on an older version of the sling.engine module and I don't want
        // to change it just for these tests, so using reflection to get the processor, as we're
        // running with a more recent version of sling.engine in the pax exam environment
        final String slingRequestProcessorClassName = "org.apache.sling.engine.SlingRequestProcessor";
        final ServiceReference<?> ref = bundleContext.getServiceReference(slingRequestProcessorClassName);
        assertNotNull("Expecting service:" + slingRequestProcessorClassName, ref);

        final Object processor = bundleContext.getService(ref);
        try {
            // void processRequest(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse resource, ResourceResolver resourceResolver)
            final Method processMethod = processor.getClass().getMethod(
                "processRequest", 
                HttpServletRequest.class, HttpServletResponse.class, ResourceResolver.class);
            assertNotNull("Expecting processRequest method", processMethod);
            processMethod.invoke(processor, request, response, resourceResolver);
        } finally {
            bundleContext.ungetService(ref);
        }

        if(expectedStatus > 0) {
            assertEquals("Expected status " + expectedStatus + " at " + path, expectedStatus, response.getStatus());
        }

        return response;
    }

    protected void assertTestServlet(final String path, final String servletName) throws Exception {
        final String output = executeRequest(path, 200).getOutputAsString();
        final String expected = TestServlet.SERVED_BY_PREFIX + servletName;
        assertTrue("Expecting output to contain " + expected + ", got " + output, output.contains(expected));
    }

    // move below helpers for deep removal to Pax Exam

    private static List<Option> expand(final Option[] options) {
        final List<Option> expanded = new ArrayList<>();
        if (options != null) {
            for (final Option option : options) {
                if (option != null) {
                    if (option instanceof CompositeOption) {
                        expanded.addAll(Arrays.asList(((CompositeOption) option).getOptions()));
                    } else {
                        expanded.add(option);
                    }
                }
            }
        }
        return expanded;
    }

    private static Option[] remove(final Option[] options, final Option... removables) {
        final List<Option> expanded = expand(options);
        for (final Option removable : removables) {
            if (removable instanceof CompositeOption) {
                expanded.removeAll(Arrays.asList(((CompositeOption) removable).getOptions()));
            } else {
                expanded.removeAll(Collections.singleton(removable));
            }
        }
        return expanded.toArray(new Option[0]);
    }

}
