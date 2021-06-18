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

import static org.apache.sling.testing.paxexam.SlingOptions.slingServlets;
import static org.apache.sling.testing.paxexam.SlingOptions.versionResolver;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.vmOption;
import static org.ops4j.pax.exam.CoreOptions.when;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;

import java.lang.reflect.Method;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.engine.SlingRequestProcessor;
import org.apache.sling.servlethelpers.MockSlingHttpServletRequest;
import org.apache.sling.servlethelpers.MockSlingHttpServletResponse;
import org.apache.sling.testing.paxexam.TestSupport;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.options.extra.VMOption;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

public class ServletResolverTestSupport extends TestSupport {

    @Inject
    private ResourceResolverFactory resourceResolverFactory;

    @Inject
    protected BundleContext bundleContext;

    @SuppressWarnings("unused")
    @Inject
    private SlingRequestProcessor slingRequestProcessor;

    public static final String P_PATHS = "sling.servlet.paths";
    public static final String P_RESOURCE_TYPES = "sling.servlet.resourceTypes";
    public static final String P_METHODS = "sling.servlet.methods";
    public static final String P_EXTENSIONS = "sling.servlet.extensions";
    public static final String P_SELECTORS = "sling.servlet.selectors";
    public static final String P_STRICT_PATHS = "sling.servlet.paths.strict";
    public static final String RT_DEFAULT = "sling/servlet/default";
    public static final String M_GET = "GET";
    public static final String M_POST = "POST";

    @Configuration
    public Option[] configuration() {
        final String debugPort = System.getProperty("debugPort");
        VMOption debugOption = null;
        if (debugPort != null && !debugPort.isEmpty()) {
            debugOption = vmOption(String.format("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=%s", debugPort));
        }

        final String vmOpt = System.getProperty("pax.vm.options");
        VMOption vmOption = null;
        if (vmOpt != null && !vmOpt.isEmpty()) {
            vmOption = vmOption(vmOpt);
        }

        final String jacocoOpt = System.getProperty("jacoco.command");
        VMOption jacocoCommand = null;
        if (jacocoOpt != null && !jacocoOpt.isEmpty()) {
            jacocoCommand = vmOption(jacocoOpt);
        }

        
        final int httpPort = findFreePort();
        versionResolver.setVersionFromProject("org.apache.sling", "org.apache.sling.api");
        versionResolver.setVersionFromProject("org.apache.sling", "org.apache.sling.resourceresolver");
        versionResolver.setVersionFromProject("org.apache.sling", "org.apache.sling.scripting.core");
        versionResolver.setVersionFromProject("org.apache.sling", "org.apache.sling.commons.johnzon");
        versionResolver.setVersion("org.apache.sling", "org.apache.sling.engine", "2.7.2");
        return options(
            composite(
                when(debugOption != null).useOptions(debugOption),
                when(vmOption != null).useOptions(vmOption),
                when(jacocoCommand != null).useOptions(jacocoCommand),
                baseConfiguration(),
                slingServlets(),
                mavenBundle().groupId("org.apache.felix").artifactId("org.apache.felix.converter").version("1.0.12"), // new Sling API dependency
                testBundle("bundle.filename"),
                mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.scripting.spi").versionAsInProject(),
                mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.servlet-helpers").versionAsInProject(),
                junitBundles(),
                newConfiguration("org.apache.felix.http")
                    .put("org.osgi.service.http.port", httpPort)
                    .asOption(),
                newConfiguration("org.apache.sling.jcr.resource.internal.JcrResourceResolverFactoryImpl")
                    .put("resource.resolver.required.providernames", "")
                    .asOption(),
                buildBundleWithBnd(
                    TestResourceProvider.class,
                    TestServiceUserValidator.class
                ),
                newConfiguration("org.apache.sling.jcr.base.internal.LoginAdminWhitelist")
                    .put("whitelist.bundles.regexp", "^PAXEXAM.*$")
                    .asOption()
            ).remove(
                mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.servlets.resolver").version(versionResolver) // remove bundle from slingQuickstartOakTar, added via testBundle in current version
            )
        );
    }

    protected MockSlingHttpServletResponse executeRequest(final String path, final int expectedStatus) throws Exception {
        return executeRequest("GET", path, expectedStatus);
    }

    protected MockSlingHttpServletResponse executeRequest(final String method, final String path, final int expectedStatus) throws Exception {
        final ResourceResolver resourceResolver = resourceResolverFactory.getAdministrativeResourceResolver(null);
        assertNotNull("Expecting ResourceResolver", resourceResolver);
        final MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resourceResolver) {
            @Override
            public String getMethod() {
                return method;
            }
        };
        request.setPathInfo(path);
        final MockSlingHttpServletResponse response = createMockSlingHttpServletResponse();

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

        if (expectedStatus > 0) {
            assertEquals("Expected status " + expectedStatus + " for " + method
                + " at " + path, expectedStatus, response.getStatus());
        }

        return response;
    }

    protected MockSlingHttpServletResponse createMockSlingHttpServletResponse() {
        return new MockSlingHttpServletResponse();
    }

    protected void assertTestServlet(final String path, int expectedStatus) throws Exception {
        assertTestServlet(M_GET, path, expectedStatus);
    }

    protected void assertTestServlet(final String method, final String path, int expectedStatus) throws Exception {
        executeRequest(method, path, expectedStatus);
    }

    protected void assertTestServlet(String path, String servletName) throws Exception {
        assertTestServlet(M_GET, path, servletName);
    }

    protected void assertTestServlet(final String method, final String path, final String servletName) throws Exception {
        final String output = executeRequest(method, path, TestServlet.IM_A_TEAPOT).getOutputAsString();
        final String expected = TestServlet.SERVED_BY_PREFIX + servletName;
        assertTrue("Expecting output to contain " + expected + ", got " + output, output.contains(expected));
    }

}
