/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 ~ Licensed to the Apache Software Foundation (ASF) under one
 ~ or more contributor license agreements.  See the NOTICE file
 ~ distributed with this work for additional information
 ~ regarding copyright ownership.  The ASF licenses this file
 ~ to you under the Apache License, Version 2.0 (the
 ~ "License"); you may not use this file except in compliance
 ~ with the License.  You may obtain a copy of the License at
 ~
 ~   http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing,
 ~ software distributed under the License is distributed on an
 ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 ~ KIND, either express or implied.  See the License for the
 ~ specific language governing permissions and limitations
 ~ under the License.
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
package org.apache.sling.servlets.resolver.it;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.inject.Inject;
import javax.script.ScriptException;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.scripting.spi.bundle.BundledRenderUnit;
import org.apache.sling.scripting.spi.bundle.TypeProvider;
import org.apache.sling.servlets.resolver.internal.bundle.BundledScriptServlet;
import org.apache.sling.testing.paxexam.TestSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.options.extra.VMOption;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.tinybundles.core.TinyBundle;
import org.ops4j.pax.tinybundles.core.TinyBundles;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import static org.apache.sling.testing.paxexam.SlingOptions.slingServlets;
import static org.apache.sling.testing.paxexam.SlingOptions.versionResolver;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.when;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class ServletResourceIT extends TestSupport {

    private BundledRenderUnit bundledRenderUnit;

    @Inject
    private ResourceResolverFactory resourceResolverFactory;

    @Inject
    private BundleContext bundleContext;

    @Configuration
    public Option[] ownConfiguration() throws FileNotFoundException {
        TinyBundle bundle = TinyBundles.bundle().read(new FileInputStream(System.getProperty("bundle.filename")));
        String header = bundle.getHeader("Export-Package");
        bundle.set("Export-Package", header + ",org.apache.sling.servlets.resolver.internal.bundle");
        final String vmOpt = System.getProperty("pax.vm.options");
        VMOption vmOption = null;
        if (StringUtils.isNotEmpty(vmOpt)) {
            vmOption = new VMOption(vmOpt);
        }
        final int httpPort = findFreePort();
        versionResolver.setVersionFromProject("org.apache.sling", "org.apache.sling.api");
        versionResolver.setVersionFromProject("org.apache.sling", "org.apache.sling.resourceresolver");
        versionResolver.setVersionFromProject("org.apache.sling", "org.apache.sling.scripting.core");
        versionResolver.setVersionFromProject("org.apache.sling", "org.apache.sling.commons.johnzon");
        versionResolver.setVersion("org.apache.sling", "org.apache.sling.engine", "2.7.2");

        return options(
                composite(
                        when(vmOption != null).useOptions(vmOption),
                        baseConfiguration(),
                        slingServlets(),
                        mavenBundle().groupId("org.apache.felix").artifactId("org.apache.felix.converter").version("1.0.12"), // new Sling API dependency
                        mavenBundle().groupId("org.apache.johnzon").artifactId("johnzon-mapper").version("1.2.8"),
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
                                .asOption(),
                        CoreOptions.streamBundle(bundle.build()).start()
                ).remove(
                        mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.servlets.resolver").version(versionResolver) // remove bundle from slingQuickstartOakTar, added via testBundle in current version
                )
        );
    }

    @Before
    public void registerBundledScriptServlet() {
        Dictionary<String, String> properties = new Hashtable<>();
        properties.put(ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES, "sling/bundled/test");
        bundledRenderUnit = new TestBundledRenderUnit();
        bundleContext.registerService(Servlet.class, new BundledScriptServlet(new LinkedHashSet<>(), bundledRenderUnit), properties);
    }

    @Test
    public void testResourceAdaptsToBundledRenderedUnit() {
        try (ResourceResolver resourceResolver = resourceResolverFactory.getAdministrativeResourceResolver(null)) {
            Resource servletResource = resourceResolver.resolve("/apps/sling/bundled/test/GET.servlet");
            assertNotNull(servletResource);
            BundledRenderUnit unitFromResource = servletResource.adaptTo(BundledRenderUnit.class);
            assertEquals(bundledRenderUnit, unitFromResource);
        } catch (LoginException e) {
            throw new RuntimeException(e);
        }
    }

    private static class TestBundledRenderUnit implements BundledRenderUnit {

        @Override
        public @NotNull String getName() {
            return null;
        }

        @Override
        public @NotNull Bundle getBundle() {
            return null;
        }

        @Override
        public @NotNull BundleContext getBundleContext() {
            return null;
        }

        @Override
        public @NotNull Set<TypeProvider> getTypeProviders() {
            return null;
        }

        @Override
        public <T> @Nullable T getService(@NotNull String s) {
            return null;
        }

        @Override
        public <T> @Nullable T[] getServices(@NotNull String s, @Nullable String s1) {
            return null;
        }

        @Override
        public @NotNull String getPath() {
            return null;
        }

        @Override
        public void eval(@NotNull HttpServletRequest httpServletRequest, @NotNull HttpServletResponse httpServletResponse)
                throws ScriptException {

        }
    }

}
