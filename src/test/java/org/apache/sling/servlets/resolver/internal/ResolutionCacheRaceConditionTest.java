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

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.request.RequestProgressTracker;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.wrappers.ResourceResolverWrapper;
import org.apache.sling.servlets.resolver.internal.helper.AbstractResourceCollector;
import org.apache.sling.servlets.resolver.internal.helper.ResourceCollector;
import org.apache.sling.servlets.resolver.internal.resolution.ResolutionCache;
import org.apache.sling.servlets.resolver.internal.resource.MockServletResource;
import org.apache.sling.testing.resourceresolver.DefaultMockResourceFactory;
import org.apache.sling.testing.resourceresolver.MockResourceResolverFactory;
import org.apache.sling.testing.resourceresolver.MockResourceResolverFactoryOptions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.BundleContext;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reproduces the "stale servlet served from the resolution cache" problem: when two servlets share
 * the same {@link ResolutionCache} key (same resource type, method and extension) and the correct one
 * registers after the other, {@code SlingServletResolver.getServletInternal()} resolves and caches
 * with a non-atomic <em>get &rarr; resolve &rarr; put</em>. If a {@link ResolutionCache#flushCache()}
 * triggered by that registration lands between the resolve and the put, the resolution re-inserts the
 * now-stale servlet into the just-flushed cache, and it is served to later requests until an unrelated
 * flush clears it. The test forces the flush to occur mid-resolution to trigger this deterministically.
 */
public class ResolutionCacheRaceConditionTest {

    /** Custom factory turning any {@code *.servlet} resource into a servlet-backed resource. */
    private static final class ServletMockResourceFactory extends DefaultMockResourceFactory {
        @Override
        public Resource newMockResource(String path, Map<String, Object> properties, ResourceResolver resolver) {
            if (path.endsWith(".servlet")) {
                Servlet servlet = (Servlet) properties.get(MockServletResource.PROP_SERVLET);
                return new MockServletResource(resolver, servlet, path);
            }
            return super.newMockResource(path, properties, resolver);
        }
    }

    private static final String RESOURCE_TYPE = "foo";
    private static final String SCRIPT_PATH = "/foo/foo.servlet";

    private SlingServletResolver servletResolver;
    private ResolutionCache resolutionCache;
    private ResourceResolver mockResourceResolver;

    /** Fired once, during candidate collection of a resolution, to simulate a concurrent flush. */
    private Runnable duringResolutionHook;

    @Before
    public void setUp() throws Exception {
        // ResolverConfig is an annotation type, which Mockito cannot mock on recent JDKs; build a
        // real implementation via a dynamic proxy returning the values we care about.
        final ResolverConfig config = newResolverConfig();

        final MockResourceResolverFactoryOptions options = new MockResourceResolverFactoryOptions()
                .setSearchPaths(new String[] {"/"})
                .setMockResourceFactory(new ServletMockResourceFactory());
        final MockResourceResolverFactory factory = new MockResourceResolverFactory(options);

        // Wrap the resolver so we can run a hook the first time a resolution touches the tree.
        final ResourceResolver realResolver = factory.getResourceResolver(null);
        final AtomicBoolean hookFired = new AtomicBoolean(false);
        mockResourceResolver = new ResourceResolverWrapper(realResolver) {
            private void maybeFire() {
                if (duringResolutionHook != null && hookFired.compareAndSet(false, true)) {
                    duringResolutionHook.run();
                }
            }

            @Override
            public Resource getResource(String path) {
                maybeFire();
                return super.getResource(path);
            }

            @Override
            public Resource getResource(Resource base, String path) {
                maybeFire();
                return super.getResource(base, path);
            }
        };

        // Activate a real resolution cache (bundle context is only used for whiteboard registrations).
        final BundleContext bundleContext = Mockito.mock(BundleContext.class);
        resolutionCache = new ResolutionCache();
        final java.lang.reflect.Method activate =
                ResolutionCache.class.getDeclaredMethod("activate", BundleContext.class, ResolverConfig.class);
        activate.setAccessible(true);
        activate.invoke(resolutionCache, bundleContext, config);

        servletResolver = new SlingServletResolver();
        setField(servletResolver, "resourceResolverFactory", factory);
        setField(servletResolver, "resolutionCache", resolutionCache);
        setField(servletResolver, "executionPaths", new AtomicReference<String[]>(null));
        setField(servletResolver, "defaultExtensions", new AtomicReference<>(Arrays.asList("html")));
        setField(servletResolver, "useResourceCaching", Boolean.FALSE);
        setField(servletResolver, "sharedScriptResolver", new AtomicReference<>(mockResourceResolver));
    }

    /**
     * Positive control: without any overlap, registering the correct servlet (modelled by a flush)
     * cleanly invalidates the cache. This passes today and proves the flush mechanism itself is fine.
     */
    @Test
    public void testFlushClearsCacheWhenNoConcurrentResolution() throws Exception {
        final Servlet oldServlet = registerServlet(SCRIPT_PATH, "old-servlet");

        final SlingHttpServletRequest request = newRequest(RESOURCE_TYPE, "GET", "html");
        assertSame("sanity: first resolution returns the only registered servlet", oldServlet, resolve(request));

        // entry is now cached
        assertNotNull("resolution should have been cached", resolutionCache.get(cacheKey()));

        // second servlet registers -> cache flush (no resolution in flight)
        resolutionCache.flushCache();

        assertNull("a plain flush must remove the stale entry", resolutionCache.get(cacheKey()));
    }

    /**
     * The bug: a flush that happens <em>while a resolution is in flight</em> is defeated by the
     * resolution's trailing {@code cache.put(...)}, leaving the stale servlet cached.
     *
     * <p>On the current code this assertion FAILS - {@code cacheKey()} still maps to the old servlet,
     * which is exactly the servlet that would be (wrongly) served to every following request.
     */
    @Test
    public void testResolutionOverlappingFlushMustNotRepopulateStaleEntry() throws Exception {
        final Servlet oldServlet = registerServlet(SCRIPT_PATH, "old-servlet");

        // Simulate the correct servlet registering concurrently: during candidate collection of this
        // very resolution, its registration fires a cache invalidation (flushCache()).
        duringResolutionHook = () -> resolutionCache.flushCache();

        final SlingHttpServletRequest request = newRequest(RESOURCE_TYPE, "GET", "html");
        final Servlet resolved = resolve(request);
        assertSame("this resolution still legitimately returns the old servlet", oldServlet, resolved);

        // The invalidation happened after this resolution started, so nothing it observed may remain
        // cached. Otherwise the stale servlet is served to all later requests.
        assertNull(
                "A resolution that overlapped a cache flush must not leave a stale entry behind; "
                        + "the resolutionCache still returns the outdated servlet, which is the "
                        + "root cause of the intermittent 'wrong servlet executed' symptom.",
                resolutionCache.get(cacheKey()));
    }

    // ---------- helpers ----------

    private Servlet resolve(final SlingHttpServletRequest request) {
        return servletResolver.resolveServlet(request);
    }

    /** Rebuilds the exact {@link ResourceCollector} key the resolver uses internally for the request. */
    private AbstractResourceCollector cacheKey() {
        final Resource resource = mock(Resource.class);
        when(resource.getResourceType()).thenReturn(RESOURCE_TYPE);
        when(resource.getResourceSuperType()).thenReturn(null);
        return ResourceCollector.create(resource, "html", null, Arrays.asList("html"), "GET", new String[0], false);
    }

    private Servlet registerServlet(final String path, final String name) throws Exception {
        final Servlet servlet = mock(Servlet.class, name);
        final Map<String, Object> props = new HashMap<>();
        props.put(MockServletResource.PROP_SERVLET, servlet);
        // remove a previous registration if present
        final Resource existing = mockResourceResolver.getResource(path);
        if (existing != null) {
            mockResourceResolver.delete(existing);
        }
        Resource parent = mockResourceResolver.getResource("/");
        final String[] segments = path.substring(1).split("/");
        for (int i = 0; i < segments.length - 1; i++) {
            Resource child = parent.getChild(segments[i]);
            parent = child != null ? child : mockResourceResolver.create(parent, segments[i], null);
        }
        mockResourceResolver.create(parent, segments[segments.length - 1], props);
        mockResourceResolver.commit();
        return servlet;
    }

    private SlingHttpServletRequest newRequest(final String resourceType, final String method, final String extension) {
        final SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
        final Resource resource = mock(Resource.class);
        final RequestProgressTracker tracker = mock(RequestProgressTracker.class);
        final RequestPathInfo pathInfo = mock(RequestPathInfo.class);

        when(request.getResource()).thenReturn(resource);
        when(request.getRequestProgressTracker()).thenReturn(tracker);
        when(request.getRequestPathInfo()).thenReturn(pathInfo);
        when(request.getMethod()).thenReturn(method);
        when(pathInfo.getExtension()).thenReturn(extension);
        when(pathInfo.getSelectors()).thenReturn(new String[0]);
        when(resource.getResourceType()).thenReturn(resourceType);
        when(resource.getResourceSuperType()).thenReturn(null);
        when(resource.getPath()).thenReturn("/content/test");
        return request;
    }

    /** Builds a {@link ResolverConfig} with the values relevant to this test and defaults otherwise. */
    private static ResolverConfig newResolverConfig() {
        final Map<String, Object> values = new HashMap<>();
        values.put("servletresolver_servletRoot", "0");
        values.put("servletresolver_cacheSize", 200);
        values.put("servletresolver_paths", new String[] {"/"});
        values.put("servletresolver_defaultExtensions", new String[] {"html"});
        values.put("servletresolver_mountProviders", true);
        values.put("servletresolver_mountPathProviders", false);
        values.put("enable_resource_caching", false);
        return (ResolverConfig) java.lang.reflect.Proxy.newProxyInstance(
                ResolverConfig.class.getClassLoader(), new Class<?>[] {ResolverConfig.class}, (proxy, method, args) -> {
                    if ("annotationType".equals(method.getName())) {
                        return ResolverConfig.class;
                    }
                    return values.get(method.getName());
                });
    }

    private static void setField(final Object target, final String name, final Object value) throws Exception {
        final Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Field findField(Class<?> type, final String name) throws NoSuchFieldException {
        while (type != null) {
            try {
                return type.getDeclaredField(name);
            } catch (final NoSuchFieldException e) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
