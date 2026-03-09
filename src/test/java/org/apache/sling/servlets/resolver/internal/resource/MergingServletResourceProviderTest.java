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
package org.apache.sling.servlets.resolver.internal.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.GenericServlet;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.SyntheticResource;
import org.apache.sling.spi.resource.provider.ResolveContext;
import org.apache.sling.spi.resource.provider.ResourceProvider;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.ServiceReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class MergingServletResourceProviderTest {

    private static final Servlet TEST_SERVLET = new GenericServlet() {
        private static final long serialVersionUID = 1L;

        @Override
        public void service(ServletRequest req, ServletResponse res) {
            // nothing do do
        }
    };

    private interface Marker {}

    /**
     * Validates behavior when there is no parent provider and only indexed servlet paths are available, ensuring that
     * synthetic intermediate resources are still exposed as children so traversal to deeper servlet paths remains
     * possible.
     */
    @Test
    public void testListChildrenWithNoParentProviderCreatesSyntheticIntermediate() {
        final ResourceResolver resolver = Mockito.mock(ResourceResolver.class);
        final ResolveContext<Object> ctx = mockContext(resolver, null, null);
        final MergingServletResourceProvider mergingProvider = new MergingServletResourceProvider();
        final Resource parent = new SyntheticResource(resolver, "/apps", "type");

        // Register a deep path so /apps/sling becomes a synthetic child.
        addProvider(mergingProvider, "/apps/sling/sample/GET.servlet");

        final List<Resource> children = toList(mergingProvider.listChildren(ctx, parent));

        assertEquals(1, children.size());
        assertEquals("/apps/sling", children.get(0).getPath());
        assertTrue(children.get(0) instanceof SyntheticResource);
    }

    /**
     * Verifies merge semantics when parent children and servlet-backed children overlap, asserting that existing parent
     * entries are overridden in place, wrapped-resource adaptation still works, and additional indexed branches are
     * represented as synthetic children.
     */
    @Test
    public void testListChildrenMergesParentAndOverlaysProviderResource() {
        final ResourceResolver resolver = Mockito.mock(ResourceResolver.class);

        final Marker marker = new Marker() {};
        final Resource parentA = mockParentChild("/apps/parent/a", marker);
        final Resource parentB = mockParentChild("/apps/parent/b", marker);
        final Resource parent = new SyntheticResource(resolver, "/apps/parent", "type");

        @SuppressWarnings("unchecked")
        final ResourceProvider<Object> parentProvider = Mockito.mock(ResourceProvider.class);
        Mockito.when(parentProvider.listChildren(Mockito.any(), Mockito.eq(parent)))
                .thenReturn(List.of(parentA, parentB).iterator());

        final ResolveContext<Object> parentCtx = Mockito.mock(ResolveContext.class);
        final ResolveContext<Object> ctx = mockContext(resolver, parentProvider, parentCtx);

        final MergingServletResourceProvider mergingProvider = new MergingServletResourceProvider();
        addProvider(mergingProvider, "/apps/parent/b", "/apps/parent/c/deep");

        final List<Resource> children = toList(mergingProvider.listChildren(ctx, parent));

        assertEquals(3, children.size());
        assertEquals("/apps/parent/a", children.get(0).getPath());
        assertEquals("/apps/parent/b", children.get(1).getPath());
        assertEquals("/apps/parent/c", children.get(2).getPath());

        assertTrue(children.get(1) instanceof ServletResource);
        assertSame(marker, children.get(1).adaptTo(Marker.class));
        assertTrue(children.get(2) instanceof SyntheticResource);
    }

    /**
     * Covers defensive handling for providers returning a null child iterator, confirming that overlay children are
     * still returned and that listChildren continues to produce a valid result without throwing or dropping indexed
     * servlet resources.
     */
    @Test
    public void testListChildrenHandlesNullParentIterator() {
        final ResourceResolver resolver = Mockito.mock(ResourceResolver.class);
        final Resource parent = new SyntheticResource(resolver, "/apps/parent", "type");

        @SuppressWarnings("unchecked")
        final ResourceProvider<Object> parentProvider = Mockito.mock(ResourceProvider.class);
        Mockito.when(parentProvider.listChildren(Mockito.any(), Mockito.eq(parent)))
                .thenReturn(null);

        final ResolveContext<Object> parentCtx = Mockito.mock(ResolveContext.class);
        final ResolveContext<Object> ctx = mockContext(resolver, parentProvider, parentCtx);

        final MergingServletResourceProvider mergingProvider = new MergingServletResourceProvider();
        addProvider(mergingProvider, "/apps/parent/child");

        final List<Resource> children = toList(mergingProvider.listChildren(ctx, parent));

        assertEquals(1, children.size());
        assertEquals("/apps/parent/child", children.get(0).getPath());
        assertTrue(children.get(0) instanceof ServletResource);
    }

    /**
     * Exercises a large parent-child set to lock in expected merge outcomes under high cardinality, including stable
     * presence of overridden entries and appended overlay-only entries, which provides a baseline for later
     * performance-focused refactoring.
     */
    @Test
    public void testListChildrenWithLargeParentChildrenSet() {
        final ResourceResolver resolver = Mockito.mock(ResourceResolver.class);
        final Resource parent = new SyntheticResource(resolver, "/apps/parent", "type");

        final int count = 5000;
        final List<Resource> parentChildren = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            parentChildren.add(mockParentChild("/apps/parent/child-" + i, null));
        }

        @SuppressWarnings("unchecked")
        final ResourceProvider<Object> parentProvider = Mockito.mock(ResourceProvider.class);
        Mockito.when(parentProvider.listChildren(Mockito.any(), Mockito.eq(parent)))
                .thenReturn(parentChildren.iterator());

        final ResolveContext<Object> parentCtx = Mockito.mock(ResolveContext.class);
        final ResolveContext<Object> ctx = mockContext(resolver, parentProvider, parentCtx);

        final MergingServletResourceProvider mergingProvider = new MergingServletResourceProvider();
        addProvider(mergingProvider, "/apps/parent/child-1200", "/apps/parent/child-2200", "/apps/parent/overlay-only");

        final List<Resource> children = toList(mergingProvider.listChildren(ctx, parent));

        assertEquals(count + 1, children.size());
        assertEquals("/apps/parent/child-0", children.get(0).getPath());
        assertEquals(
                "/apps/parent/overlay-only", children.get(children.size() - 1).getPath());
        assertTrue(pathExists(children, "/apps/parent/child-1200"));
        assertTrue(pathExists(children, "/apps/parent/child-2200"));
        assertTrue(pathExists(children, "/apps/parent/overlay-only"));

        final Resource overridden = childByPath(children, "/apps/parent/child-1200");
        assertNotNull(overridden);
        assertTrue(overridden instanceof ServletResource);
    }

    /**
     * Ensures the merged iterator behaves in a streaming fashion by verifying that reading only the first result does
     * not force full consumption of the parent iterator, which protects callers that stop early from paying the full
     * cost of enumerating all parent children.
     */
    @Test
    public void testListChildrenStreamsWithoutConsumingAllParentChildrenForFirstResult() {
        final ResourceResolver resolver = Mockito.mock(ResourceResolver.class);
        final Resource parent = new SyntheticResource(resolver, "/apps/parent", "type");

        final int count = 5000;
        final List<Resource> parentChildren = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            parentChildren.add(mockParentChild("/apps/parent/child-" + i, null));
        }
        final AtomicInteger consumed = new AtomicInteger();
        final Iterator<Resource> countingIterator = new Iterator<Resource>() {
            private final Iterator<Resource> delegate = parentChildren.iterator();

            @Override
            public boolean hasNext() {
                return delegate.hasNext();
            }

            @Override
            public Resource next() {
                consumed.incrementAndGet();
                return delegate.next();
            }
        };

        @SuppressWarnings("unchecked")
        final ResourceProvider<Object> parentProvider = Mockito.mock(ResourceProvider.class);
        Mockito.when(parentProvider.listChildren(Mockito.any(), Mockito.eq(parent)))
                .thenReturn(countingIterator);

        final ResolveContext<Object> parentCtx = Mockito.mock(ResolveContext.class);
        final ResolveContext<Object> ctx = mockContext(resolver, parentProvider, parentCtx);
        final MergingServletResourceProvider mergingProvider = new MergingServletResourceProvider();
        addProvider(mergingProvider, "/apps/parent/overlay-only");

        final Iterator<Resource> children = mergingProvider.listChildren(ctx, parent);

        assertTrue(children.hasNext());
        assertEquals("/apps/parent/child-0", children.next().getPath());
        assertEquals(1, consumed.get());
        assertTrue(consumed.get() < count);
    }

    /**
     * Validates MergingChildrenIterator semantics from its javadoc: (1) Phase 1 — parent children in order, with
     * overlay paths replaced by servlet/synthetic and marked processed, others emitted as-is. (2) Phase 2 — after
     * parent exhausted, overlay-only paths emitted. (3) No path emitted twice (processedPaths deduplication).
     */
    @Test
    public void testMergingChildrenIteratorPhaseOrderAndNoDuplicatePaths() {
        final ResourceResolver resolver = Mockito.mock(ResourceResolver.class);
        final Resource parent = new SyntheticResource(resolver, "/apps/merge", "type");
        final Resource parentA = mockParentChild("/apps/merge/a", null);
        final Resource parentB = mockParentChild("/apps/merge/b", null);
        final Resource parentC = mockParentChild("/apps/merge/c", null);

        @SuppressWarnings("unchecked")
        final ResourceProvider<Object> parentProvider = Mockito.mock(ResourceProvider.class);
        Mockito.when(parentProvider.listChildren(Mockito.any(), Mockito.eq(parent)))
                .thenReturn(List.of(parentA, parentB, parentC).iterator());

        final ResolveContext<Object> parentCtx = Mockito.mock(ResolveContext.class);
        final ResolveContext<Object> ctx = mockContext(resolver, parentProvider, parentCtx);
        final MergingServletResourceProvider mergingProvider = new MergingServletResourceProvider();
        // Overlay: b (replacement for parent b), d (overlay-only).
        addProvider(mergingProvider, "/apps/merge/b", "/apps/merge/d");

        final List<Resource> children = toList(mergingProvider.listChildren(ctx, parent));
        final List<String> paths = new ArrayList<>();
        for (Resource r : children) {
            paths.add(r.getPath());
        }

        // Phase 1 order: parent a (as-is), parent b replaced by servlet, parent c (as-is). Phase 2: overlay-only d.
        assertEquals(4, children.size());
        assertEquals("/apps/merge/a", paths.get(0));
        assertEquals("/apps/merge/b", paths.get(1));
        assertEquals("/apps/merge/c", paths.get(2));
        assertEquals("/apps/merge/d", paths.get(3));

        assertTrue(children.get(1) instanceof ServletResource);
        assertTrue(children.get(3) instanceof ServletResource);

        // processedPaths: no path emitted twice.
        assertEquals("no duplicate paths", paths.size(), new LinkedHashSet<>(paths).size());
    }

    private static boolean pathExists(List<Resource> resources, String path) {
        return childByPath(resources, path) != null;
    }

    private static Resource childByPath(List<Resource> resources, String path) {
        for (Resource child : resources) {
            if (path.equals(child.getPath())) {
                return child;
            }
        }
        return null;
    }

    private static Resource mockParentChild(String path, Marker marker) {
        final Resource resource = Mockito.mock(Resource.class);
        Mockito.when(resource.getPath()).thenReturn(path);
        Mockito.when(resource.getResourceType()).thenReturn("parent/type");
        if (marker != null) {
            Mockito.when(resource.adaptTo(Marker.class)).thenReturn(marker);
        } else {
            Mockito.when(resource.adaptTo(Marker.class)).thenReturn(null);
        }
        return resource;
    }

    private static ResolveContext<Object> mockContext(
            ResourceResolver resolver, ResourceProvider<?> parentProvider, ResolveContext<?> parentCtx) {
        @SuppressWarnings("unchecked")
        final ResolveContext<Object> ctx = Mockito.mock(ResolveContext.class);
        Mockito.when(ctx.getResourceResolver()).thenReturn(resolver);
        Mockito.doReturn(parentProvider).when(ctx).getParentResourceProvider();
        Mockito.doReturn(parentCtx).when(ctx).getParentResolveContext();
        return ctx;
    }

    @SafeVarargs
    private static void addProvider(MergingServletResourceProvider mergingProvider, String... paths) {
        final Set<String> servletPaths = new LinkedHashSet<>();
        Collections.addAll(servletPaths, paths);

        final ServletResourceProvider servletProvider =
                new ServletResourceProvider(TEST_SERVLET, servletPaths, Collections.emptySet(), null);
        @SuppressWarnings("unchecked")
        final ServiceReference<Servlet> reference = Mockito.mock(ServiceReference.class);
        mergingProvider.add(servletProvider, reference);
    }

    private static List<Resource> toList(Iterator<Resource> it) {
        if (it == null) {
            return Collections.emptyList();
        }
        final List<Resource> resources = new ArrayList<>();
        while (it.hasNext()) {
            resources.add(it.next());
        }
        return resources;
    }
}
