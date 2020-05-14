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

import java.util.Iterator;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceWrapper;
import org.apache.sling.api.wrappers.IteratorWrapper;
import org.apache.sling.api.wrappers.ResourceResolverWrapper;
import org.apache.sling.servlets.resolver.internal.resource.MergingServletResourceProvider;
import org.apache.sling.spi.resource.provider.ResolveContext;
import org.apache.sling.spi.resource.provider.ResourceContext;
import org.apache.sling.spi.resource.provider.ResourceProvider;
import org.jetbrains.annotations.NotNull;

public class ScriptResourceResolver extends ResourceResolverWrapper {
    private final ResourceResolver resolver;
    private final Supplier<MergingServletResourceProvider> provider;

    public ScriptResourceResolver(ResourceResolver resolver, Supplier<MergingServletResourceProvider> provider) {
        super(resolver);
        this.resolver = resolver;
        this.provider = provider;
    }

    public static ScriptResourceResolver wrap(ResourceResolver scriptResourceResolver, Supplier<MergingServletResourceProvider> provider) {
        return new ScriptResourceResolver(scriptResourceResolver, provider);
    }

    @Override
    public Iterable<Resource> getChildren(Resource parent) {
        return () -> listChildren(parent);
    }

    public Resource getResource(String scriptPath) {
        MergingServletResourceProvider provider = this.provider.get();

        if (provider == null) {
            return super.getResource(scriptPath);
        }
        else {
            return wrap(provider.getResource(new ResolveContext() {
                @Override
                public ResourceResolver getResourceResolver() {
                    return ScriptResourceResolver.this;
                }

                @Override
                public Object getProviderState() {
                    return null;
                }

                @Override
                public ResolveContext<?> getParentResolveContext() {
                    return null;
                }

                @Override
                public ResourceProvider<?> getParentResourceProvider() {
                    return new ResourceProvider() {
                        @Override
                        public Resource getResource(ResolveContext ctx, String path, ResourceContext resourceContext, Resource parent) {
                            return resolver.getResource(path);
                        }

                        @Override
                        public Iterator<Resource> listChildren(ResolveContext ctx, Resource parent) {
                            return resolver.listChildren(parent);
                        }
                    };
                }
            }, scriptPath));
        }
    }

    @Override
    public Resource getResource(Resource base, @NotNull String path) {
        if (!path.startsWith("/") && base != null) {
            path = base.getPath() + "/" + path;
        }
        return getResource(path);
    }

    @Override
    public Iterator<Resource> listChildren(Resource parent) {
        MergingServletResourceProvider provider = this.provider.get();
        if (provider == null) {
            return super.listChildren(parent);
        }
        else {
            return wrap(provider.listChildren(new ResolveContext() {
                @Override
                public ResourceResolver getResourceResolver() {
                    return ScriptResourceResolver.this;
                }

                @Override
                public Object getProviderState() {
                    return null;
                }

                public ResolveContext<?> getParentResolveContext() {
                    return null;
                }

                public ResourceProvider<?> getParentResourceProvider() {
                    return new ResourceProvider() {
                        @Override
                        public Resource getResource(ResolveContext ctx, String path, ResourceContext resourceContext, Resource parent) {
                            return resolver.getResource(path);
                        }

                        @Override
                        public Iterator<Resource> listChildren(ResolveContext ctx, Resource parent) {
                            return resolver.listChildren(parent);
                        }
                    };
                }
            }, unwrap(parent)));
        }
    }

    private Resource wrap(Resource resource) {
        if (resource != null && !(resource.getResourceResolver() instanceof ScriptResourceResolver)) {
            resource = new ScriptResourceResolverResourceWrapper(resource);
        }
        return resource;
    }

    private Iterator<Resource> wrap(Iterator<Resource> iter) {
        if (iter != null) {
            iter = new IteratorWrapper<Resource>(iter){
                @Override
                public Resource next() {
                    return wrap(super.next());
                }
            };
        }
        return iter;
    }

    private Resource unwrap(Resource resource) {
        if (resource instanceof ScriptResourceResolverResourceWrapper) {
            resource = ((ResourceWrapper) resource).getResource();
        }
        return resource;
    }

    public ScriptResourceResolver clone(Map o) throws LoginException {
        return ScriptResourceResolver.wrap(resolver.clone(o), provider);
    }

    private class ScriptResourceResolverResourceWrapper extends ResourceWrapper {
        public ScriptResourceResolverResourceWrapper(Resource resource) {
            super(resource);
        }

        @Override
        public ResourceResolver getResourceResolver() {
            return ScriptResourceResolver.this;
        }
    }
}
