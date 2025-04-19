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

import javax.inject.Inject;

import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.request.builder.Builders;
import org.apache.sling.api.request.builder.SlingJakartaHttpServletResponseResult;
import org.apache.sling.api.resource.AbstractResource;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.engine.SlingRequestProcessor;
import org.apache.sling.testing.paxexam.TestSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.options.extra.VMOption;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import static org.apache.sling.testing.paxexam.SlingOptions.eventadmin;
import static org.apache.sling.testing.paxexam.SlingOptions.scr;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.vmOption;
import static org.ops4j.pax.exam.CoreOptions.when;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.factoryConfiguration;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;

public class ServletResolverTestSupport extends TestSupport {

    @Inject
    protected ResourceResolverFactory resourceResolverFactory;

    @Inject
    protected BundleContext bundleContext;

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
            debugOption =
                    vmOption(String.format("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=%s", debugPort));
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
        return options(composite(
                when(debugOption != null).useOptions(debugOption),
                when(vmOption != null).useOptions(vmOption),
                when(jacocoCommand != null).useOptions(jacocoCommand),
                // update pax logging for SLF4J 2
                mavenBundle()
                        .groupId("org.ops4j.pax.logging")
                        .artifactId("pax-logging-api")
                        .version("2.3.0"),
                baseConfiguration(),
                mavenBundle()
                        .groupId("org.apache.felix")
                        .artifactId("org.apache.felix.http.servlet-api")
                        .version("6.1.0"),
                mavenBundle()
                        .groupId("org.apache.felix")
                        .artifactId("org.apache.felix.http.jetty12")
                        .version("1.0.26"),
                scr(),
                eventadmin(),
                mavenBundle()
                        .groupId("org.osgi")
                        .artifactId("org.osgi.util.converter")
                        .version("1.0.9"),
                mavenBundle()
                        .groupId("org.apache.sling")
                        .artifactId("org.apache.sling.commons.johnzon")
                        .versionAsInProject(),
                mavenBundle().groupId("commons-io").artifactId("commons-io").versionAsInProject(),
                mavenBundle()
                        .groupId("commons-codec")
                        .artifactId("commons-codec")
                        .version("1.15"),
                mavenBundle()
                        .groupId("org.apache.commons")
                        .artifactId("commons-lang3")
                        .versionAsInProject(),
                mavenBundle()
                        .groupId("org.apache.commons")
                        .artifactId("commons-collections4")
                        .version("4.4"),
                mavenBundle()
                        .groupId("org.apache.sling")
                        .artifactId("org.apache.sling.commons.mime")
                        .versionAsInProject(),
                mavenBundle()
                        .groupId("org.apache.sling")
                        .artifactId("org.apache.sling.commons.osgi")
                        .version("2.4.2"),
                mavenBundle()
                        .groupId("org.apache.sling")
                        .artifactId("org.apache.sling.api")
                        .versionAsInProject(),
                mavenBundle()
                        .groupId("org.apache.sling")
                        .artifactId("org.apache.sling.scripting.spi")
                        .versionAsInProject(),
                mavenBundle()
                        .groupId("org.apache.sling")
                        .artifactId("org.apache.sling.serviceusermapper")
                        .versionAsInProject(),
                mavenBundle()
                        .groupId("org.apache.felix")
                        .artifactId("org.apache.felix.healthcheck.api")
                        .versionAsInProject(),
                mavenBundle()
                        .groupId("org.apache.sling")
                        .artifactId("org.apache.sling.auth.core")
                        .versionAsInProject(),
                mavenBundle()
                        .groupId("org.apache.sling")
                        .artifactId("org.apache.sling.resourceresolver")
                        .versionAsInProject(),
                mavenBundle()
                        .groupId("org.apache.sling")
                        .artifactId("org.apache.sling.settings")
                        .version("1.4.2"),
                mavenBundle()
                        .groupId("org.apache.commons")
                        .artifactId("commons-fileupload2-core")
                        .version("2.0.0-M2"),
                mavenBundle()
                        .groupId("org.apache.commons")
                        .artifactId("commons-fileupload2-jakarta-servlet5")
                        .version("2.0.0-M2"),
                mavenBundle()
                        .groupId("org.apache.sling")
                        .artifactId("org.apache.sling.engine")
                        .versionAsInProject(),
                factoryConfiguration("org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended")
                        .put("user.mapping", new String[] {
                            "org.apache.sling.servlets.resolver:console=sling-readall",
                            "org.apache.sling.servlets.resolver:scripts=sling-scripting"
                        })
                        .asOption(),
                newConfiguration("org.apache.felix.http")
                        .put("org.osgi.service.http.port", httpPort)
                        .asOption(),
                newConfiguration("org.apache.sling.jcr.resource.internal.JcrResourceResolverFactoryImpl")
                        .put("resource.resolver.required.providernames", "")
                        .asOption(),
                buildBundleWithBnd(
                        TestResourceProvider.class, TestServiceUserValidator.class, ScriptEngineManagerMock.class),
                testBundle(),
                newConfiguration("org.apache.sling.jcr.base.internal.LoginAdminWhitelist")
                        .put("whitelist.bundles.regexp", "^PAXEXAM.*$")
                        .asOption(),
                newConfiguration("org.apache.sling.servlets.resolver.internal.bundle.BundledScriptTracker")
                        .put("mandatoryBundles", "testBundle")
                        .asOption()));
    }

    protected Option testBundle() {
        return testBundle("bundle.filename");
    }

    protected SlingJakartaHttpServletResponse createMockSlingHttpServletResponse() {
        return Builders.newResponseBuilder().buildJakartaResponseResult();
    }

    protected SlingJakartaHttpServletResponse executeRequest(final String path, final int expectedStatus)
            throws Exception {
        return executeRequest("GET", path, expectedStatus);
    }

    protected SlingJakartaHttpServletResponse executeRequest(
            final String method, final String path, final int expectedStatus) throws Exception {
        final ResourceResolver resourceResolver = resourceResolverFactory.getAdministrativeResourceResolver(null);
        assertNotNull("Expecting ResourceResolver", resourceResolver);
        final Resource resource = new AbstractResource() {
            @Override
            public String getPath() {
                return path;
            }

            @Override
            public @NotNull String getResourceType() {
                return "foo/bar";
            }

            @Override
            public @Nullable String getResourceSuperType() {
                return null;
            }

            @Override
            public @NotNull ResourceMetadata getResourceMetadata() {
                return new ResourceMetadata();
            }

            @Override
            public @NotNull ResourceResolver getResourceResolver() {
                return resourceResolver;
            }
        };

        final SlingJakartaHttpServletRequest request =
                Builders.newRequestBuilder(resource).withRequestMethod(method).buildJakartaRequest();
        final SlingJakartaHttpServletResponse response = createMockSlingHttpServletResponse();

        final ServiceReference<SlingRequestProcessor> ref =
                bundleContext.getServiceReference(SlingRequestProcessor.class);
        assertNotNull("Expecting service:" + SlingRequestProcessor.class, ref);

        final SlingRequestProcessor processor = bundleContext.getService(ref);
        try {
            processor.processRequest(request, response, resourceResolver);
        } finally {
            bundleContext.ungetService(ref);
        }

        if (expectedStatus > 0) {
            assertEquals(
                    "Expected status " + expectedStatus + " for " + method + " at " + path,
                    expectedStatus,
                    response.getStatus());
        }

        return response;
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

    protected void assertTestServlet(final String method, final String path, final String servletName)
            throws Exception {
        final SlingJakartaHttpServletResponse response = executeRequest(method, path, TestServlet.IM_A_TEAPOT);
        final String output = ((SlingJakartaHttpServletResponseResult) response).getOutputAsString();
        final String expected = TestServlet.SERVED_BY_PREFIX + servletName;
        assertTrue("Expecting output to contain " + expected + ", got " + output, output.contains(expected));
    }
}
