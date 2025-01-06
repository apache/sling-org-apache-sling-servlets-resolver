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
import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarInputStream;

import javax.inject.Inject;
import javax.script.ScriptException;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.scripting.spi.bundle.BundledRenderUnit;
import org.apache.sling.scripting.spi.bundle.TypeProvider;
import org.apache.sling.servlets.resolver.internal.bundle.BundledScriptServlet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.tinybundles.TinyBundle;
import org.ops4j.pax.tinybundles.TinyBundles;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.ops4j.pax.exam.CoreOptions.streamBundle;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class ServletResourceIT extends ServletResolverTestSupport {

    private BundledRenderUnit bundledRenderUnit;

    @Inject
    private ResourceResolverFactory resourceResolverFactory;

    @Inject
    private BundleContext bundleContext;

    protected Option testBundle() {
        try {
            TinyBundle bundle = TinyBundles.bundle().readIn(new JarInputStream(new FileInputStream(System.getProperty("bundle.filename"))));
            String header = bundle.getHeader("Export-Package");
            bundle.setHeader("Export-Package", header + ",org.apache.sling.servlets.resolver.internal.bundle");
            return streamBundle(bundle.build()).start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
