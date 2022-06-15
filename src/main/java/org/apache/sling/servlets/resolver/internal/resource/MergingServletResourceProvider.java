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

import org.apache.commons.lang3.tuple.Pair;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class MergingServletResourceProvider extends ResourceProvider<Object> {
    private final List<Pair<ServletResourceProvider, ServiceReference<?>>> registrations = new ArrayList<>();

    private final AtomicReference<ConcurrentHashMap<String, Set<String>>> tree = new AtomicReference<>(new ConcurrentHashMap<>());
    private final AtomicReference<ConcurrentHashMap<String, Pair<ServletResourceProvider, ServiceReference<?>>>> providers = new AtomicReference<>(new ConcurrentHashMap<>());

    synchronized void add(ServletResourceProvider provider, ServiceReference<?> reference) {
        registrations.add(Pair.of(provider, reference));
        ConcurrentHashMap<String, Set<String>> localTree = tree.get();
        ConcurrentHashMap<String, Pair<ServletResourceProvider, ServiceReference<?>>> localProvs = providers.get();
        index(localTree, localProvs, Arrays.asList(registrations.get(registrations.size() - 1)));
    }

    synchronized boolean remove(ServletResourceProvider provider) {
        boolean found = false;
        for (Iterator<Pair<ServletResourceProvider, ServiceReference<?>>> regIter = registrations.iterator(); regIter.hasNext(); ) {
            Pair<ServletResourceProvider, ServiceReference<?>> reg = regIter.next();
            if (reg.getLeft() == provider) {
                regIter.remove();
                found = true;
            }
            else {
                Bundle bundle = reg.getRight().getBundle();
                if (bundle == null || bundle.getState() == Bundle.STOPPING) {
                    regIter.remove();
                    found = true;
                }
            }
        }
        if (found) {
            ConcurrentHashMap<String, Set<String>> localTree = new ConcurrentHashMap<>();
            ConcurrentHashMap<String, Pair<ServletResourceProvider, ServiceReference<?>>> localProvs = new ConcurrentHashMap<>();
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

    private void index(ConcurrentHashMap<String, Set<String>> tree, ConcurrentHashMap<String, Pair<ServletResourceProvider, ServiceReference<?>>> providers, List<Pair<ServletResourceProvider, ServiceReference<?>>> registrations) {
        for (Pair<ServletResourceProvider, ServiceReference<?>> reference : registrations) {
            for (String path : reference.getLeft().getServletPaths()) {
                StringBuilder currentBuilder = new StringBuilder();
                for (String part : path.split("/")) {
                    Set<String> childs = tree.computeIfAbsent(currentBuilder.toString(), k -> Collections.synchronizedSet(new LinkedHashSet<>()));
                    currentBuilder.append("/").append(part);
                    String cleanedCurrent = currentBuilder.toString().trim().replace("//", "/");
                    // replace the buffer with the cleaned string
                    currentBuilder.setLength(0);
                    currentBuilder.append(cleanedCurrent);

                    childs.add(currentBuilder.toString());
                }

                Pair<ServletResourceProvider, ServiceReference<?>> old = providers.get(path);
                if (old == null) {
                    providers.put(path, reference);
                } else {
                    if (reference.getRight().compareTo(old.getRight()) > 0) {
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
    public @Nullable Resource getResource(@NotNull ResolveContext<Object> resolveContext, @NotNull String s, @NotNull ResourceContext resourceContext, @Nullable Resource resource) {
        return getResource(resolveContext, s);
    }

    @SuppressWarnings("unchecked")
    public Resource getResource(@SuppressWarnings("rawtypes") ResolveContext resolveContext, String path) {
        Resource wrapped = null;
        final ResourceProvider<?> parentProvider = resolveContext.getParentResourceProvider();
        if (parentProvider != null) {
            wrapped = parentProvider.getResource(resolveContext.getParentResolveContext(), path, ResourceContext.EMPTY_CONTEXT, null);
        }
        Resource result;
        Pair<ServletResourceProvider, ServiceReference<?>> provider = providers.get().get(path);

        if (provider != null) {
            result = provider.getLeft().getResource(resolveContext, path, null, null);
            if (result instanceof ServletResource) {
                ((ServletResource) result).setWrappedResource(wrapped);
            }
        }
        else {
            if (wrapped != null && !(wrapped instanceof NonExistingResource)) {
                result = wrapped;
            } else {
                result = null;
            }
            if (result == null && tree.get().containsKey(path)) {
                result = new SyntheticResource(resolveContext.getResourceResolver(), path, ResourceProvider.RESOURCE_TYPE_SYNTHETIC);
            } else {
                return wrapped;
            }
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    public Iterator<Resource> listChildren(@SuppressWarnings("rawtypes") final ResolveContext ctx, final Resource parent) {
        Map<String, Resource> result = new LinkedHashMap<>();

        final ResourceProvider<?> parentProvider = ctx.getParentResourceProvider();
        if (parentProvider != null) {
            for (Iterator<Resource> iter = parentProvider.listChildren(ctx.getParentResolveContext(), parent); iter != null && iter.hasNext(); ) {
                Resource resource = iter.next();
                result.put(resource.getPath(), resource);
            }
        }
        Set<String> paths = tree.get().get(parent.getPath());

        if (paths != null) {
            for (String path : paths.toArray(new String[0])) {
                Pair<ServletResourceProvider, ServiceReference<?>> provider = providers.get().get(path);

                if (provider != null) {
                    Resource resource = provider.getLeft().getResource(ctx, path, null, parent);
                    if (resource != null) {
                        Resource wrapped = result.put(path, resource);
                        if (resource instanceof ServletResource) {
                            ((ServletResource) resource).setWrappedResource(wrapped);
                        }
                    }
                } else {
                    result.computeIfAbsent(path, key -> new SyntheticResource(ctx.getResourceResolver(), key, 
                            ResourceProvider.RESOURCE_TYPE_SYNTHETIC));
                }
            }
        }
        return result.values().iterator();
    }
}
