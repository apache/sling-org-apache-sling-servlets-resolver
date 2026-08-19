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
package org.apache.sling.servlets.resolver.internal.bundle;

import javax.servlet.Servlet;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.servlets.resolver.internal.ResolverConfig;
import org.apache.sling.servlets.resolver.internal.resource.ServletMounter;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Demonstrates that {@code BundledScriptTracker.refreshDispatcher()} is not thread-safe: it is reachable
 * concurrently from {@code addingBundle}/{@code removedBundle} and does a non-atomic read/mutate-in-place/swap
 * of the shared {@code dispatchers} map, so concurrent invocations drain the same published map against each
 * other and register duplicate (or lose) {@code DispatcherServlet}s - in production, bundled scripts then
 * intermittently stop resolving or serve a stale script. The test asserts idempotency (refreshing repeatedly
 * with the same registrations must register each resource type's dispatcher exactly once); OSGi collaborators
 * are stubbed with {@link Proxy} because {@link ServiceReference}/{@link Bundle} extend {@link Comparable},
 * which the mocking engine cannot instrument on recent JDKs.
 */
public class BundledScriptTrackerRaceConditionTest {

    private static final int RESOURCE_TYPE_COUNT = 4;
    private static final int THREADS = 4;
    private static final int ROUNDS = 200;

    private BundledScriptTracker tracker;

    /** Counts DispatcherServlet registrations / unregistrations, observed through the stubbed OSGi context. */
    private final AtomicInteger registrations = new AtomicInteger();

    private final AtomicInteger unregistrations = new AtomicInteger();

    /** The bundle whose context every DispatcherServlet registration is funnelled through. */
    private Bundle registeringBundle;

    @Before
    public void setUp() {
        final BundleContext context = proxy(BundleContext.class, (method, args) -> {
            if ("registerService".equals(method.getName()) && args != null && args.length == 3) {
                registrations.incrementAndGet();
                return dispatcherRegistration();
            }
            return null; // getBundles()/addBundleListener()/... during activate()
        });
        registeringBundle =
                proxy(Bundle.class, (method, args) -> "getBundleContext".equals(method.getName()) ? context : null);

        tracker = new BundledScriptTracker();
        tracker.mounter = servletMounter(context);
        // activate() publishes an empty dispatcher map and opens the (empty) bundle tracker.
        tracker.activate(context);
    }

    /**
     * Concurrently refresh the dispatcher with the same set of registrations many times. A correct,
     * thread-safe implementation registers each resource type's DispatcherServlet exactly once and reuses it
     * on every later refresh; the racy implementation registers duplicates (or throws while mutating the
     * shared map from multiple threads).
     */
    @Test
    public void concurrentRefreshMustNotDuplicateOrDropDispatchers() throws Exception {
        final List<ServiceRegistration<Servlet>> inputs = new ArrayList<>();
        for (int i = 0; i < RESOURCE_TYPE_COUNT; i++) {
            inputs.add(inputRegistration("test/rt" + i + "/1.0.0"));
        }

        final List<Throwable> failures = new CopyOnWriteArrayList<>();

        for (int round = 0; round < ROUNDS && failures.isEmpty(); round++) {
            final CyclicBarrier startLine = new CyclicBarrier(THREADS);
            final CountDownLatch done = new CountDownLatch(THREADS);
            for (int t = 0; t < THREADS; t++) {
                new Thread(() -> {
                            try {
                                startLine.await(5, TimeUnit.SECONDS); // release all threads together
                                tracker.refreshDispatcher(new ArrayList<>(inputs));
                            } catch (final Throwable th) {
                                failures.add(th);
                            } finally {
                                done.countDown();
                            }
                        })
                        .start();
            }
            if (!done.await(15, TimeUnit.SECONDS)) {
                fail("refreshDispatcher deadlocked/looped on the shared HashMap - a symptom of the race");
            }
        }

        if (!failures.isEmpty()) {
            fail("refreshDispatcher threw under concurrency (shared-state corruption): " + failures.get(0));
        }

        // Each resource type's dispatcher must be registered exactly once and then reused; extra registrations
        // mean concurrent refreshes drained the shared map and re-registered duplicates.
        assertEquals(
                "DispatcherServlet must be registered exactly once per resource type",
                RESOURCE_TYPE_COUNT,
                registrations.get());
        assertEquals(
                "no live DispatcherServlet must be unregistered when the desired set is unchanged",
                0,
                unregistrations.get());
    }

    // ---------- stubs ----------

    /** A real ServletMounter with a null 'provider' so mountProviders() is true and register() uses the context. */
    private static ServletMounter servletMounter(final BundleContext context) {
        final ResourceResolverFactory resourceResolverFactory = proxy(
                ResourceResolverFactory.class,
                (method, args) -> "getSearchPath".equals(method.getName()) ? List.of("/apps/", "/libs/") : null);
        final ResolverConfig config = proxy(ResolverConfig.class, (method, args) -> {
            switch (method.getName()) {
                case "servletresolver_mountProviders":
                    return true; // -> provider == null -> mountProviders() == true
                case "servletresolver_mountPathProviders":
                    return false;
                case "servletresolver_servletRoot":
                    return "0";
                default:
                    return null;
            }
        });
        return new ServletMounter(context, resourceResolverFactory, null, config);
    }

    /** A DispatcherServlet registration as returned from the (stubbed) registration context. */
    private ServiceRegistration<Servlet> dispatcherRegistration() {
        final ServiceReference<Servlet> ref = proxy(ServiceReference.class, (method, args) -> null);
        return proxy(ServiceRegistration.class, (method, args) -> {
            if ("unregister".equals(method.getName())) {
                unregistrations.incrementAndGet();
                return null;
            }
            return "getReference".equals(method.getName()) ? ref : null;
        });
    }

    /** An incoming servlet registration carrying a versioned resource type, as produced from a bundle. */
    private ServiceRegistration<Servlet> inputRegistration(final String versionedResourceType) {
        final ServiceReference<Servlet> ref = proxy(ServiceReference.class, (method, args) -> {
            if ("getProperty".equals(method.getName())) {
                return ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES.equals(args[0])
                        ? new String[] {versionedResourceType}
                        : null;
            }
            return "getBundle".equals(method.getName()) ? registeringBundle : null;
        });
        return proxy(ServiceRegistration.class, (method, args) -> "getReference".equals(method.getName()) ? ref : null);
    }

    /** Builds a {@link Proxy} for {@code iface}; {@code handler} answers all but the {@link Object} methods. */
    @SuppressWarnings("unchecked")
    private static <T> T proxy(final Class<T> iface, final BiFunction<Method, Object[], Object> handler) {
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] {iface}, (p, method, args) -> {
            switch (method.getName()) {
                case "equals":
                    return p == args[0];
                case "hashCode":
                    return System.identityHashCode(p);
                case "toString":
                    return iface.getSimpleName() + "@proxy";
                case "compareTo":
                    return 0;
                default:
                    return handler.apply(method, args);
            }
        });
    }
}
