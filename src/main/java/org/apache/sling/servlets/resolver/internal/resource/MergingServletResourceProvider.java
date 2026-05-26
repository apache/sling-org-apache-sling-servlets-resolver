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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.sling.api.resource.NonExistingResource;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.SyntheticResource;
import org.apache.sling.spi.resource.provider.ResolveContext;
import org.apache.sling.spi.resource.provider.ResourceContext;
import org.apache.sling.spi.resource.provider.ResourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;

public class MergingServletResourceProvider extends ResourceProvider<Object> {
    private final List<Map.Entry<ServletResourceProvider, ServiceReference<?>>> registrations = new ArrayList<>();

    private final AtomicReference<ConcurrentHashMap<String, Set<String>>> tree =
            new AtomicReference<>(new ConcurrentHashMap<>());
    private final AtomicReference<ConcurrentHashMap<String, Map.Entry<ServletResourceProvider, ServiceReference<?>>>>
            providers = new AtomicReference<>(new ConcurrentHashMap<>());

    synchronized void add(ServletResourceProvider provider, ServiceReference<?> reference) {
        registrations.add(Map.entry(provider, reference));
        ConcurrentHashMap<String, Set<String>> localTree = tree.get();
        ConcurrentHashMap<String, Map.Entry<ServletResourceProvider, ServiceReference<?>>> localProvs = providers.get();
        index(localTree, localProvs, Arrays.asList(registrations.get(registrations.size() - 1)));
    }

    synchronized boolean remove(ServletResourceProvider provider) {
        boolean found = false;
        for (Iterator<Map.Entry<ServletResourceProvider, ServiceReference<?>>> regIter = registrations.iterator();
                regIter.hasNext(); ) {
            Map.Entry<ServletResourceProvider, ServiceReference<?>> reg = regIter.next();
            if (reg.getKey() == provider) {
                regIter.remove();
                found = true;
            } else {
                Bundle bundle = reg.getValue().getBundle();
                if (bundle == null || bundle.getState() == Bundle.STOPPING) {
                    regIter.remove();
                    found = true;
                }
            }
        }
        if (found) {
            ConcurrentHashMap<String, Set<String>> localTree = new ConcurrentHashMap<>();
            ConcurrentHashMap<String, Map.Entry<ServletResourceProvider, ServiceReference<?>>> localProvs =
                    new ConcurrentHashMap<>();
            index(localTree, localProvs, registrations);
            tree.set(localTree);
            providers.set(localProvs);
        }
        return found;
    }

    synchronized void clear() {
        registrations.clear();
        tree.set(new ConcurrentHashMap<>());
        providers.set(new ConcurrentHashMap<>());
    }

    private void index(
            ConcurrentHashMap<String, Set<String>> tree,
            ConcurrentHashMap<String, Map.Entry<ServletResourceProvider, ServiceReference<?>>> providers,
            List<Map.Entry<ServletResourceProvider, ServiceReference<?>>> registrations) {
        for (Map.Entry<ServletResourceProvider, ServiceReference<?>> reference : registrations) {
            for (String path : reference.getKey().getServletPaths()) {
                StringBuilder currentBuilder = new StringBuilder();
                for (String part : path.split("/")) {
                    Set<String> childs = tree.computeIfAbsent(
                            currentBuilder.toString(), k -> Collections.synchronizedSet(new LinkedHashSet<>()));
                    currentBuilder.append("/").append(part);
                    String cleanedCurrent = currentBuilder.toString().trim().replace("//", "/");
                    // replace the buffer with the cleaned string
                    currentBuilder.setLength(0);
                    currentBuilder.append(cleanedCurrent);

                    childs.add(currentBuilder.toString());
                }

                Map.Entry<ServletResourceProvider, ServiceReference<?>> old = providers.get(path);
                if (old == null) {
                    providers.put(path, reference);
                } else {
                    if (reference.getValue().compareTo(old.getValue()) > 0) {
                        providers.put(path, reference);
                    }
                }
            }
        }
    }

    public boolean isRootOf(String path) {
        if (path != null && path.startsWith("/")) {
            int idx = path.indexOf('/', 1);
            if (idx != -1) {
                path = path.substring(0, idx);
                return tree.get().containsKey(path);
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    @Override
    public @Nullable Resource getResource(
            @NotNull ResolveContext<Object> resolveContext,
            @NotNull String s,
            @NotNull ResourceContext resourceContext,
            @Nullable Resource resource) {
        return getResource(resolveContext, s);
    }

    @SuppressWarnings("unchecked")
    public Resource getResource(@SuppressWarnings("rawtypes") ResolveContext resolveContext, String path) {
        Resource wrapped = null;
        final ResourceProvider<?> parentProvider = resolveContext.getParentResourceProvider();
        if (parentProvider != null) {
            wrapped = parentProvider.getResource(
                    resolveContext.getParentResolveContext(), path, ResourceContext.EMPTY_CONTEXT, null);
        }
        Resource result;
        Map.Entry<ServletResourceProvider, ServiceReference<?>> provider =
                providers.get().get(path);

        if (provider != null) {
            result = provider.getKey().getResource(resolveContext, path, null, null);
            if (result instanceof ServletResource) {
                ((ServletResource) result).setWrappedResource(wrapped);
            }
        } else {
            if (wrapped != null && !(wrapped instanceof NonExistingResource)) {
                result = wrapped;
            } else {
                result = null;
            }
            if (result == null && tree.get().containsKey(path)) {
                result = new SyntheticResource(
                        resolveContext.getResourceResolver(), path, ResourceProvider.RESOURCE_TYPE_SYNTHETIC);
            } else {
                return wrapped;
            }
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    public Iterator<Resource> listChildren(
            @SuppressWarnings("rawtypes") final ResolveContext ctx, final Resource parent) {
        final ResourceProvider<?> parentProvider = ctx.getParentResourceProvider();
        final Iterator<Resource> parentIterator =
                parentProvider == null ? null : parentProvider.listChildren(ctx.getParentResolveContext(), parent);

        // Indexed servlet paths under this parent (from tree). Snapshot to array for stable iteration.
        final Set<String> paths = tree.get().get(parent.getPath());
        final String[] pathArray = paths == null ? new String[0] : paths.toArray(new String[0]);
        // LinkedHashSet preserves order; iterator yields overlay-only paths after parent iteration.
        final Set<String> pendingPaths = new LinkedHashSet<>(Arrays.asList(pathArray));
        final Iterator<String> overlayIterator = pendingPaths.iterator();

        final ConcurrentHashMap<String, Map.Entry<ServletResourceProvider, ServiceReference<?>>> localProviders =
                providers.get();

        return new MergingChildrenIterator(parentIterator, ctx, parent, pendingPaths, overlayIterator, localProviders);
    }

    private static final class MergingChildrenIterator implements Iterator<Resource> {

        private final Iterator<Resource> parentIterator;
        private final ResolveContext<?> ctx;
        private final Resource parent;
        private final Set<String> pendingPaths;
        private final Set<String> processedPaths = new HashSet<>();
        private final Iterator<String> overlayIterator;
        private final ConcurrentHashMap<String, Map.Entry<ServletResourceProvider, ServiceReference<?>>> localProviders;

        private Resource next;
        private boolean nextComputed;

        MergingChildrenIterator(
                Iterator<Resource> parentIterator,
                ResolveContext<?> ctx,
                Resource parent,
                Set<String> pendingPaths,
                Iterator<String> overlayIterator,
                ConcurrentHashMap<String, Map.Entry<ServletResourceProvider, ServiceReference<?>>> localProviders) {
            this.parentIterator = parentIterator;
            this.ctx = ctx;
            this.parent = parent;
            this.pendingPaths = pendingPaths;
            this.overlayIterator = overlayIterator;
            this.localProviders = localProviders;
        }

        @Override
        public boolean hasNext() {
            if (!nextComputed) {
                next = fetchNext();
                nextComputed = true;
            }
            return next != null;
        }

        @Override
        public Resource next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            Resource result = next;
            next = null;
            nextComputed = false;
            return result;
        }

        /**
         * Returns the next merged child, or null when exhausted, either from parent iterator or
         * from overlay paths not yet processed.
         */
        private Resource fetchNext() {
            Resource fromParent = tryNextFromParent();
            if (fromParent != null) {
                return fromParent;
            }
            return tryNextFromOverlay();
        }

        /** advance parent iterator; emit overlay replacement or parent child per path. */
        @SuppressWarnings("unchecked")
        private Resource tryNextFromParent() {
            if (parentIterator == null || !parentIterator.hasNext()) {
                return null;
            }
            Resource parentChild = parentIterator.next();
            String path = parentChild.getPath();
            if (!pendingPaths.contains(path)) {
                return parentChild;
            }
            processedPaths.add(path);
            Map.Entry<ServletResourceProvider, ServiceReference<?>> provider = localProviders.get(path);
            if (provider != null) {
                Resource resource = provider.getKey().getResource((ResolveContext<Object>) ctx, path, null, parent);
                if (resource != null) {
                    if (resource instanceof ServletResource) {
                        ((ServletResource) resource).setWrappedResource(parentChild);
                    }
                    return resource;
                }
            }
            return parentChild;
        }

        /** emit overlay-only paths (not already processed) as servlet or synthetic. */
        @SuppressWarnings("unchecked")
        private Resource tryNextFromOverlay() {
            while (overlayIterator.hasNext()) {
                String path = overlayIterator.next();
                if (processedPaths.contains(path)) {
                    continue;
                }
                Map.Entry<ServletResourceProvider, ServiceReference<?>> provider = localProviders.get(path);
                if (provider != null) {
                    Resource resource = provider.getKey().getResource((ResolveContext<Object>) ctx, path, null, parent);
                    if (resource != null) {
                        return resource;
                    }
                } else {
                    return new SyntheticResource(
                            ctx.getResourceResolver(), path, ResourceProvider.RESOURCE_TYPE_SYNTHETIC);
                }
            }
            return null;
        }
    }
}
