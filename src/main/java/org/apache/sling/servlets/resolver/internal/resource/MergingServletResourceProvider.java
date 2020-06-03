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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.SyntheticResource;
import org.apache.sling.spi.resource.provider.ResolveContext;
import org.apache.sling.spi.resource.provider.ResourceProvider;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;

public class MergingServletResourceProvider {
    private final List<Pair<ServletResourceProvider, ServiceReference<?>>> registrations = new ArrayList<>();

    private final AtomicReference<ConcurrentHashMap<String, Set<String>>> tree = new AtomicReference<>(new ConcurrentHashMap<>());
    private final AtomicReference<ConcurrentHashMap<String, Pair<ServletResourceProvider, ServiceReference<?>>>> providers = new AtomicReference<>(new ConcurrentHashMap<>());

    synchronized void add(ServletResourceProvider provider, ServiceReference<?> reference) {
        registrations.add(Pair.of(provider, reference));
        ConcurrentHashMap<String, Set<String>> localTree = localTree();
        ConcurrentHashMap<String, Pair<ServletResourceProvider, ServiceReference<?>>> localProvs = localProviders();
        index(localTree, localProvs, Arrays.asList(registrations.get(registrations.size() - 1)));
        tree.set(localTree);
        providers.set(localProvs);
    }

    synchronized boolean remove(ServletResourceProvider provider, ServiceReference<?> reference) {
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

    private ConcurrentHashMap<String, Set<String>> localTree() {
        ConcurrentHashMap<String, Set<String>> localTree = new ConcurrentHashMap<>();
        for (Map.Entry<String, Set<String>> entry : tree.get().entrySet()) {
            localTree.put(entry.getKey(), Collections.synchronizedSet(new LinkedHashSet<>(entry.getValue())));
        }
        return localTree;
    }

    private ConcurrentHashMap<String, Pair<ServletResourceProvider, ServiceReference<?>>> localProviders() {
        return new ConcurrentHashMap<>(providers.get());
    }

    private void index(ConcurrentHashMap<String, Set<String>> tree, ConcurrentHashMap<String, Pair<ServletResourceProvider, ServiceReference<?>>> providers, List<Pair<ServletResourceProvider, ServiceReference<?>>> registrations) {
        for (Pair<ServletResourceProvider, ServiceReference<?>> reference : registrations) {
            for (String path : reference.getLeft().getServletPaths()) {
                String current = "";
                for (String part : path.split("/")) {
                    Set<String> childs = tree.get(current);
                    if (childs == null) {
                        childs = Collections.synchronizedSet(new LinkedHashSet<>());
                        tree.put(current, childs);
                    }
                    current += "/" + part;
                    current = current.trim().replace("//", "/");

                    childs.add(current);
                }

                Pair<ServletResourceProvider, ServiceReference<?>> old = providers.put(path, reference);
                if (old != null) {
                    if (reference.getRight().compareTo(old.getRight()) < 0) {
                        providers.put(path, old);
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

    public Resource getResource(ResolveContext resolveContext, String path) {
        Resource wrapped = null;
        final ResourceProvider parentProvider = resolveContext.getParentResourceProvider();
        if (parentProvider != null) {
            wrapped = parentProvider.getResource(resolveContext.getParentResolveContext(), path, null, null);
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
            if (wrapped != null) {
                result = wrapped;
            }
            else {
                result = null;
            }
            if (result == null && tree.get().containsKey(path)) {
                result = new SyntheticResource(resolveContext.getResourceResolver(), path, ResourceProvider.RESOURCE_TYPE_SYNTHETIC);
            }
        }

        return result;
    }

    public Iterator<Resource> listChildren(final ResolveContext ctx, final Resource parent) {
        Map<String, Resource> result = new LinkedHashMap<>();

        final ResourceProvider parentProvider = ctx.getParentResourceProvider();
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
                }
                else if (!result.containsKey(path)) {
                    result.put(path, new SyntheticResource(ctx.getResourceResolver(), path, ResourceProvider.RESOURCE_TYPE_SYNTHETIC));
                }
            }
        }
        return result.values().iterator();
    }
}
