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

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.scripting.spi.bundle.BundledRenderUnit;
import org.apache.sling.scripting.spi.bundle.BundledRenderUnitFinder;
import org.apache.sling.scripting.spi.bundle.TypeProvider;
import org.apache.sling.servlets.resolver.internal.ResolverConfig;
import org.apache.sling.servlets.resolver.internal.helper.SearchPathProvider;
import org.apache.sling.servlets.resolver.internal.resource.ServletMounter;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.Version;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BundledScriptTrackerTest {

    private static final String SCRIPT_PATH = "/apps/foo/foo.html";

    @Rule
    public final OsgiContext context = new OsgiContext();

    private BundledScriptTracker tracker;
    private ServletMounter mounter;

    @Before
    public void setUp() {
        mounter = context.registerService(ServletMounter.class, mock(OSGiMockFriendlyServletMounter.class));

        BundledRenderUnitFinder finder =
                context.registerService(BundledRenderUnitFinder.class, mock(BundledRenderUnitFinder.class));

        // the finder resolves every capability to a unit whose path is the indexed script path
        final BundledRenderUnit unit = mock(BundledRenderUnit.class);
        when(unit.getPath()).thenReturn(SCRIPT_PATH);
        when(finder.findUnit(any(), any(TypeProvider.class), anySet())).thenReturn(unit);
        when(finder.findUnit(any(), anySet(), anySet())).thenReturn(unit);

        final SearchPathProvider searchPathProvider = mock(SearchPathProvider.class);
        when(searchPathProvider.getSearchPaths()).thenReturn(List.of("/apps/", "/libs/"));
        context.registerService(SearchPathProvider.class, searchPathProvider);

        // registerInjectActivateService injects the (mandatory) references registered above and calls activate()
        tracker = context.registerInjectActivateService(new BundledScriptTracker());
    }

    @Test
    public void removedBundle() {
        List<ServiceRegistration<Servlet>> registrations = new ArrayList<>();
        @SuppressWarnings("unchecked")
        ServiceRegistration<Servlet> registration = mock(ServiceRegistration.class);
        registrations.add(registration);
        tracker.removedBundle(mock(Bundle.class), mock(BundleEvent.class), registrations);
        verify(registration).unregister();
    }

    /** A bundle that is not wired to the scripting extender contributes nothing. */
    @Test
    public void addingBundleNotWiredToExtender() {
        final Bundle bundle = mock(Bundle.class);
        final BundleWiring wiring = mock(BundleWiring.class);
        when(bundle.adapt(BundleWiring.class)).thenReturn(wiring);
        when(wiring.getRequiredWires("osgi.extender")).thenReturn(Collections.emptyList());

        final List<ServiceRegistration<Servlet>> regs = tracker.addingBundle(bundle, mock(BundleEvent.class));

        assertTrue("no registrations expected", regs.isEmpty());
        assertTrue("no bundle should be tracked", tracker.getRegisteredBundles().isEmpty());
    }

    /**
     * A bundle carrying a {@code Sling-Bundled-Scripts-Ranking} header registers its script with that value as the
     * {@code service.ranking} property (verified on the merging-mode proxy reference).
     */
    @Test
    public void addingBundleAppliesRankingHeader() {
        when(mounter.mountProviders()).thenReturn(false);
        final Bundle bundle = wiredScriptBundle("com.example.scripts", "42");

        final List<ServiceRegistration<Servlet>> regs = tracker.addingBundle(bundle, mock(BundleEvent.class));

        assertEquals(1, regs.size());
        final ServiceReference<?> ref = capturedBoundReference();
        assertEquals(SCRIPT_PATH, ref.getProperty(ServletResolverConstants.SLING_SERVLET_PATHS));
        assertEquals(42, ref.getProperty(Constants.SERVICE_RANKING));
        assertEquals("true", ref.getProperty(BundledHooks.class.getName()));
        assertTrue("a synthetic service id is assigned", ref.getProperty(Constants.SERVICE_ID) instanceof Long);
        assertTrue(tracker.getRegisteredBundles().contains("com.example.scripts"));
    }

    /** Without the header, no ranking is applied - the historical behaviour is preserved. */
    @Test
    public void addingBundleWithoutHeaderLeavesRankingUnset() {
        when(mounter.mountProviders()).thenReturn(false);
        final Bundle bundle = wiredScriptBundle("com.example.noheader", null);

        tracker.addingBundle(bundle, mock(BundleEvent.class));

        final ServiceReference<?> ref = capturedBoundReference();
        assertNull(ref.getProperty(Constants.SERVICE_RANKING));
    }

    /** An unparseable header value is ignored, again leaving the ranking unset. */
    @Test
    public void addingBundleWithInvalidHeaderLeavesRankingUnset() {
        when(mounter.mountProviders()).thenReturn(false);
        final Bundle bundle = wiredScriptBundle("com.example.bad", "not-a-number");

        tracker.addingBundle(bundle, mock(BundleEvent.class));

        final ServiceReference<?> ref = capturedBoundReference();
        assertNull(ref.getProperty(Constants.SERVICE_RANKING));
    }

    /** In non-merging mode the script is registered as a real OSGi service, with the ranking in its properties. */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void addingBundleRegistersRealServiceWithRanking() {
        when(mounter.mountProviders()).thenReturn(true);

        final Bundle bundle = wiredScriptBundle("com.example.real", "7");
        final BundleContext ctx = bundle.getBundleContext();

        // register through a plain mock context so this does not trigger osgi-mock's automatic reference wiring
        ServiceRegistration<Servlet> registration = createMockServiceRegistration();
        when(ctx.registerService(eq(Servlet.class), any(Servlet.class), any())).thenReturn(registration);

        final List<ServiceRegistration<Servlet>> regs = tracker.addingBundle(bundle, mock(BundleEvent.class));
        assertEquals(1, regs.size());

        final ArgumentCaptor<Dictionary> props = ArgumentCaptor.forClass(Dictionary.class);
        verify(ctx).registerService(eq(Servlet.class), any(Servlet.class), props.capture());
        assertEquals(SCRIPT_PATH, props.getValue().get(ServletResolverConstants.SLING_SERVLET_PATHS));
        assertEquals(7, props.getValue().get(Constants.SERVICE_RANKING));
    }

    private static <T> ServiceRegistration<T> createMockServiceRegistration() {
        final ServiceRegistration<T> registration = mock();
        final ServiceReference<T> reference = mock();
        when(registration.getReference()).thenReturn(reference);
        return registration;
    }

    /** Removing a tracked bundle unregisters its script and drops it from the tracked set. */
    @Test
    public void removedBundleUnregistersScriptsAndUntracks() {
        when(mounter.mountProviders()).thenReturn(false);
        final Bundle bundle = wiredScriptBundle("com.example.scripts", "5");
        final List<ServiceRegistration<Servlet>> regs = tracker.addingBundle(bundle, mock(BundleEvent.class));
        assertTrue(tracker.getRegisteredBundles().contains("com.example.scripts"));

        tracker.removedBundle(bundle, mock(BundleEvent.class), regs);

        assertFalse(tracker.getRegisteredBundles().contains("com.example.scripts"));
        verify(mounter).unbindJakartaServlet(any());
    }

    /** {@code modifiedBundle} is unexpected and merely logs; it must not fail or mutate state. */
    @Test
    public void modifiedBundleIsHarmless() {
        tracker.modifiedBundle(mock(Bundle.class), mock(BundleEvent.class), Collections.emptyList());
        assertTrue(tracker.getRegisteredBundles().isEmpty());
    }

    /** Re-binding the search path provider reconfigures the tracker without failing. */
    @Test
    public void rebindSearchPathProviderReopensTracker() {
        final SearchPathProvider reconfigured = mock(SearchPathProvider.class);
        when(reconfigured.getSearchPaths()).thenReturn(List.of("/apps/"));

        tracker.bindSearchPathProvider(reconfigured);

        assertTrue(tracker.getRegisteredBundles().isEmpty());
    }

    /** After deactivation the tracker no longer publishes and tolerates further callbacks. */
    @Test
    public void deactivateStopsPublishing() {
        tracker.deactivate();
        // refreshDispatcher must short-circuit on the now-null dispatcher map instead of throwing
        tracker.removedBundle(mock(Bundle.class), mock(BundleEvent.class), Collections.emptyList());
        assertTrue(tracker.getRegisteredBundles().isEmpty());
    }

    /** In merging mode the script is exposed as a synthetic proxy {@link ServiceReference} with full metadata. */
    @Test
    public void mergingModeProxyReferenceExposesMetadata() {
        when(mounter.mountProviders()).thenReturn(false);
        Bundle scriptTrackerBundle = this.context.bundleContext().getBundle();
        Bundle scriptBundle = wiredScriptBundle("com.example.scripts", "3");
        tracker.addingBundle(scriptBundle, mock(BundleEvent.class));

        final ServiceReference<?> ref = capturedBoundReference();
        assertTrue(ref instanceof Proxy);
        assertSame(scriptBundle, ref.getBundle());
        assertEquals(1, ref.getUsingBundles().length);
        assertSame(scriptTrackerBundle, ref.getUsingBundles()[0]);
        assertEquals(6, ref.getPropertyKeys().length);
        assertTrue(ref.isAssignableTo(scriptBundle, Servlet.class.getName()));
        assertTrue(ref.isAssignableTo(scriptTrackerBundle, Servlet.class.getName()));
        // hashCode is the synthetic service id (0 for the first registration on a fresh tracker)
        assertEquals(0, ref.hashCode());
    }

    /** The proxy references order by service ranking first, then by the synthetic (registration) service id. */
    @Test
    public void mergingModeProxyReferenceOrdering() {
        when(mounter.mountProviders()).thenReturn(false);
        tracker.addingBundle(wiredScriptBundle("com.example.a", "5"), mock(BundleEvent.class)); // id 0, rank 5
        tracker.addingBundle(wiredScriptBundle("com.example.b", "5"), mock(BundleEvent.class)); // id 1, rank 5
        tracker.addingBundle(wiredScriptBundle("com.example.c", "9"), mock(BundleEvent.class)); // id 2, rank 9

        @SuppressWarnings({"unchecked", "rawtypes"})
        final ArgumentCaptor<ServiceReference> captor = ArgumentCaptor.forClass(ServiceReference.class);
        verify(mounter, times(3)).bindJakartaServlet(any(Servlet.class), captor.capture());
        final ServiceReference<?> a = captor.getAllValues().get(0);
        final ServiceReference<?> b = captor.getAllValues().get(1);
        final ServiceReference<?> c = captor.getAllValues().get(2);

        assertEquals(0, a.compareTo(a)); // identical service
        assertTrue(c.compareTo(a) > 0); // higher ranking wins
        assertTrue(a.compareTo(c) < 0);
        assertTrue(a.compareTo(b) > 0); // equal ranking: earlier registration (lower id) wins

        // comparison against a non-bundled (real) reference treats the proxy's own id as -1
        final ServiceReference<?> real = mock(ServiceReference.class);
        when(real.getProperty(Constants.SERVICE_ID)).thenReturn(100L);
        when(real.getProperty(Constants.SERVICE_RANKING)).thenReturn(0);
        assertTrue(a.compareTo(real) > 0); // rank 5 beats rank 0
    }

    /** The synthetic {@link ServiceRegistration} proxy supports the registration lifecycle methods. */
    @Test
    public void mergingModeProxyRegistrationLifecycle() {
        when(mounter.mountProviders()).thenReturn(false);
        final List<ServiceRegistration<Servlet>> regs =
                tracker.addingBundle(wiredScriptBundle("com.example.scripts", "1"), mock(BundleEvent.class));

        final ServiceRegistration<Servlet> reg = regs.get(0);
        assertNotNull(reg.getReference());
        reg.setProperties(new Hashtable<>()); // no-op on the proxy
        assertNotNull(reg.toString());
        assertEquals(0, reg.hashCode()); // synthetic id of the first registration
    }

    /**
     * A versioned resource-type script triggers registration of a dispatcher servlet; with nothing else tracked the
     * dispatcher responds with 404. Exercises the resource-type registration branch, the dispatcher refresh and the
     * dispatcher servlet itself.
     */
    @Test
    public void resourceTypeScriptRegistersDispatcherServlet() throws Exception {
        when(mounter.mountProviders()).thenReturn(false);
        tracker.addingBundle(wiredResourceTypeBundle("com.example.rt"), mock(BundleEvent.class));

        // in merging mode both the script and its dispatcher servlet are handed to the mounter
        final ArgumentCaptor<Servlet> servletCaptor = ArgumentCaptor.forClass(Servlet.class);
        verify(mounter, atLeastOnce()).bindJakartaServlet(servletCaptor.capture(), any());
        final Servlet dispatcher = servletCaptor.getAllValues().stream()
                .filter(BundledScriptTracker.DispatcherServlet.class::isInstance)
                .findFirst()
                .orElse(null);
        assertNotNull("a dispatcher servlet must be registered for the resource type", dispatcher);

        // with nothing else tracked the dispatcher cannot find a target and responds with 404
        final SlingJakartaHttpServletRequest request = mock(SlingJakartaHttpServletRequest.class);
        final SlingJakartaHttpServletResponse response = mock(SlingJakartaHttpServletResponse.class);
        dispatcher.service(request, response);
        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    /**
     * An "extends"-only capability is merged into the plain capability for the same resource type by {@code reduce()},
     * and a resource-type script without extension/selectors/method exercises the servlet-path derivation branch.
     */
    @Test
    public void mergedExtenderCapabilityRegistersSingleServlet() {
        when(mounter.mountProviders()).thenReturn(false);

        final Map<String, Object> extendsAttributes = new HashMap<>();
        extendsAttributes.put(ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES, "foo/bar");
        extendsAttributes.put(BundledScriptTracker.AT_EXTENDS, "foo/base");
        final Map<String, Object> scriptAttributes = new HashMap<>();
        scriptAttributes.put(ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES, "foo/bar");
        scriptAttributes.put(BundledScriptTracker.AT_SCRIPT_ENGINE, "htl");

        final Bundle bundle = wiredBundleMulti(
                "com.example.merged",
                List.of(capabilityWith(extendsAttributes), capabilityWith(scriptAttributes)),
                Collections.emptyList());

        final List<ServiceRegistration<Servlet>> regs = tracker.addingBundle(bundle, mock(BundleEvent.class));

        assertEquals(1, regs.size());
        assertTrue(tracker.getRegisteredBundles().contains("com.example.merged"));
    }

    /**
     * A script that {@code extends} another resource type resolves its super-type from a wired {@code sling.servlet}
     * capability, exercising the requires- and inheritance-chain collection.
     */
    @Test
    public void resourceSuperTypeResolvedFromWiredCapability() {
        when(mounter.mountProviders()).thenReturn(false);

        final Map<String, Object> scriptAttributes = new HashMap<>();
        scriptAttributes.put(ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES, "foo/bar");
        scriptAttributes.put(BundledScriptTracker.AT_EXTENDS, "foo/base");
        scriptAttributes.put(BundledScriptTracker.AT_SCRIPT_ENGINE, "htl");

        // a wired capability that provides the extended (super) resource type
        final Map<String, Object> superTypeAttributes = new HashMap<>();
        superTypeAttributes.put(ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES, "foo/base");
        final BundleWire superTypeWire = wireProviding(capabilityWith(superTypeAttributes));

        final Bundle bundle = wiredBundleMulti(
                "com.example.extends", List.of(capabilityWith(scriptAttributes)), List.of(superTypeWire));

        final List<ServiceRegistration<Servlet>> regs = tracker.addingBundle(bundle, mock(BundleEvent.class));

        // the script plus the wired super-type capability (which is also a script) are both registered
        assertFalse(regs.isEmpty());
        assertTrue(tracker.getRegisteredBundles().contains("com.example.extends"));
    }

    // ---------- helpers ----------

    private static BundleCapability capabilityWith(final Map<String, Object> attributes) {
        final BundleCapability capability = mock(BundleCapability.class);
        when(capability.getAttributes()).thenReturn(attributes);
        return capability;
    }

    /** A {@code sling.servlet} required wire whose provider exposes the given capability. */
    private BundleWire wireProviding(final BundleCapability capability) {
        final BundleWire wire = mock(BundleWire.class);
        when(wire.getCapability()).thenReturn(capability);
        final BundleRevision revision = mock(BundleRevision.class);
        Bundle bundle = mock(Bundle.class);
        when(revision.getBundle()).thenReturn(bundle);
        when(wire.getProvider()).thenReturn(revision);
        return wire;
    }

    /** Builds an extender-wired bundle exposing the given capabilities and {@code sling.servlet} required wires. */
    private Bundle wiredBundleMulti(
            final String symbolicName,
            final List<BundleCapability> capabilities,
            final List<BundleWire> slingServletWires) {
        final Bundle bundle = mock(Bundle.class);
        when(bundle.getSymbolicName()).thenReturn(symbolicName);
        when(bundle.getBundleContext()).thenReturn(context.bundleContext());
        when(bundle.getHeaders()).thenReturn(new Hashtable<>());

        final BundleWiring wiring = mock(BundleWiring.class);
        when(bundle.adapt(BundleWiring.class)).thenReturn(wiring);

        final Bundle extenderBundle = context.bundleContext().getBundle();
        final BundleRevision providerRevision = mock(BundleRevision.class);
        when(providerRevision.getBundle()).thenReturn(extenderBundle);
        final BundleWire extenderWire = mock(BundleWire.class);
        when(extenderWire.getProvider()).thenReturn(providerRevision);
        when(wiring.getRequiredWires("osgi.extender")).thenReturn(List.of(extenderWire));

        when(wiring.getCapabilities(BundledScriptTracker.NS_SLING_SERVLET)).thenReturn(capabilities);
        when(wiring.getRequiredWires(BundledScriptTracker.NS_SLING_SERVLET)).thenReturn(slingServletWires);
        return bundle;
    }

    /**
     * Builds a bundle that is wired to the scripting extender and exposes a single path-based {@code sling.servlet}
     * capability, optionally declaring the ranking header.
     */
    private Bundle wiredScriptBundle(final String symbolicName, final String rankingHeader) {
        final Map<String, Object> attributes = new HashMap<>();
        attributes.put(ServletResolverConstants.SLING_SERVLET_PATHS, SCRIPT_PATH);
        attributes.put(BundledScriptTracker.AT_SCRIPT_ENGINE, "htl");
        return wiredBundle(symbolicName, rankingHeader, attributes);
    }

    /**
     * Builds a bundle exposing a single versioned resource-type {@code sling.servlet} capability. The version makes
     * the registration eligible for a dispatcher servlet.
     */
    private Bundle wiredResourceTypeBundle(final String symbolicName) {
        final Map<String, Object> attributes = new HashMap<>();
        attributes.put(ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES, "foo/bar");
        attributes.put(BundledScriptTracker.AT_VERSION, Version.parseVersion("1.0.0"));
        attributes.put(ServletResolverConstants.SLING_SERVLET_EXTENSIONS, "html");
        attributes.put(BundledScriptTracker.AT_SCRIPT_ENGINE, "htl");
        return wiredBundle(symbolicName, null, attributes);
    }

    private Bundle wiredBundle(
            final String symbolicName, final String rankingHeader, final Map<String, Object> capabilityAttributes) {
        final BundleContext bundleContext = mock();
        final Bundle bundle = mock();
        when(bundleContext.getBundle()).thenReturn(bundle);
        when(bundle.getSymbolicName()).thenReturn(symbolicName);
        when(bundle.getBundleContext()).thenReturn(bundleContext);

        final Dictionary<String, String> headers = new Hashtable<>();
        if (rankingHeader != null) {
            headers.put(BundledScriptTracker.HEADER_SCRIPTS_RANKING, rankingHeader);
        }
        when(bundle.getHeaders()).thenReturn(headers);

        final BundleWiring wiring = mock(BundleWiring.class);
        when(bundle.adapt(BundleWiring.class)).thenReturn(wiring);

        // extender gate: a required "osgi.extender" wire whose provider is this component's own bundle
        final Bundle extenderBundle = context.bundleContext().getBundle();
        final BundleRevision providerRevision = mock(BundleRevision.class);
        when(providerRevision.getBundle()).thenReturn(extenderBundle);
        final BundleWire extenderWire = mock(BundleWire.class);
        when(extenderWire.getProvider()).thenReturn(providerRevision);
        when(wiring.getRequiredWires("osgi.extender")).thenReturn(List.of(extenderWire));

        final BundleCapability capability = mock(BundleCapability.class);
        when(capability.getAttributes()).thenReturn(capabilityAttributes);
        when(wiring.getCapabilities(BundledScriptTracker.NS_SLING_SERVLET)).thenReturn(List.of(capability));
        when(wiring.getRequiredWires(BundledScriptTracker.NS_SLING_SERVLET)).thenReturn(Collections.emptyList());

        return bundle;
    }

    /** Captures the proxy {@link ServiceReference} handed to the mounter in merging mode. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private ServiceReference<?> capturedBoundReference() {
        final ArgumentCaptor<ServiceReference> captor = ArgumentCaptor.forClass(ServiceReference.class);
        verify(mounter).bindJakartaServlet(any(Servlet.class), captor.capture());
        return captor.getValue();
    }

    // workaround: osgi-mocks only support parameters in the order ServiceReference, Servlet and not the reverse
    private static class OSGiMockFriendlyServletMounter extends ServletMounter {
        public OSGiMockFriendlyServletMounter(
                BundleContext context,
                ResourceResolverFactory resourceResolverFactory,
                ServletContext servletContext,
                ResolverConfig config) {
            super(context, resourceResolverFactory, servletContext, config);
        }

        public void bindJakartaServlet(ServiceReference<Servlet> reference, Servlet servlet) {
            super.bindJakartaServlet(servlet, reference);
        }

        public void bindServlet(ServiceReference<javax.servlet.Servlet> reference, javax.servlet.Servlet servlet) {
            super.bindServlet(servlet, reference);
        }
    }
}
