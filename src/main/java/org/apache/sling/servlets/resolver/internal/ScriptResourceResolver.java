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
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.IteratorWrapper;
import org.apache.sling.servlets.resolver.internal.resource.MergingServletResourceProvider;
import org.apache.sling.spi.resource.provider.ResolveContext;
import org.apache.sling.spi.resource.provider.ResourceContext;
import org.apache.sling.spi.resource.provider.ResourceProvider;

import javax.servlet.http.HttpServletRequest;

public class ScriptResourceResolver implements ResourceResolver {
    private final ResourceResolver resolver;
    private final Supplier<MergingServletResourceProvider> providerSupplier;

    public ScriptResourceResolver(ResourceResolver resolver, Supplier<MergingServletResourceProvider> provider) {
        this.resolver = resolver;
        this.providerSupplier = provider;
    }

    public static ScriptResourceResolver wrap(ResourceResolver scriptResourceResolver, Supplier<MergingServletResourceProvider> provider) {
        return new ScriptResourceResolver(scriptResourceResolver, provider);
    }

    @Override
    public Iterable<Resource> getChildren(Resource parent) {
        return () -> listChildren(parent);
    }

    @Override
    public Resource getResource(String scriptPath) {
        MergingServletResourceProvider provider = this.providerSupplier.get();

        if (provider == null) {
            return resolver.getResource(scriptPath);
        }
        else {
            return wrap(provider.getResource(new ResolveContext<Object>() {
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
                    return new ResourceProvider<Object>() {
                        @Override
                        public Resource getResource(ResolveContext<Object> ctx, String path, ResourceContext resourceContext, Resource parent) {
                            return resolver.getResource(path);
                        }

                        @Override
                        public Iterator<Resource> listChildren(ResolveContext<Object> ctx, Resource parent) {
                            return resolver.listChildren(parent);
                        }
                    };
                }
            }, scriptPath));
        }
    }

    @Override
    public Resource getResource(Resource base, String path) {
        if (!path.startsWith("/") && base != null) {
            path = String.format("%s/%s", base.getPath(), path);
        }
        return getResource(path);
    }

    @Override
    public Iterator<Resource> listChildren(Resource parent) {
        MergingServletResourceProvider provider = this.providerSupplier.get();
        if (provider == null) {
            return resolver.listChildren(parent);
        }
        else {
            return wrap(provider.listChildren(new ResolveContext<Object>() {
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
                    return new ResourceProvider<Object>() {
                        @Override
                        public Resource getResource(ResolveContext<Object> ctx, String path, ResourceContext resourceContext, Resource parent) {
                            return resolver.getResource(path);
                        }

                        @Override
                        public Iterator<Resource> listChildren(ResolveContext<Object> ctx, Resource parent) {
                            return resolver.listChildren(parent);
                        }
                    };
                }
            }, unwrap(parent)));
        }
    }

    protected final ResourceResolver getDelegate() {
        return resolver;
    }

    @Override
    public void close() {
        getDelegate().close();
    }

    @Override
    public void commit() throws PersistenceException {
        getDelegate().commit();
    }

    @Override
    public Resource create(final Resource parent, final String name, final Map<String, Object> properties) throws PersistenceException {
        return getDelegate().create(parent, name, properties);
    }

    @Override
    public boolean orderBefore(Resource resource, String s, String s1) throws UnsupportedOperationException, PersistenceException, IllegalArgumentException {
        return getDelegate().orderBefore(resource, s, s1);
    }

    @Override
    public void delete( final Resource resource) throws PersistenceException {
        getDelegate().delete(resource);
    }

    @Override
    public Iterator<Resource> findResources(final String query, final String language) {
        return getDelegate().findResources(query, language);
    }

    @Override
    public Object getAttribute(final String name) {
        return getDelegate().getAttribute(name);
    }

    @Override
    public Iterator<String> getAttributeNames() {
        return getDelegate().getAttributeNames();
    }

    @Override
    public String getParentResourceType(final Resource resource) {
        return getDelegate().getParentResourceType(resource);
    }

    @Override
    public String getParentResourceType(final String resourceType) {
        return getDelegate().getParentResourceType(resourceType);
    }

    @Override
    public String[] getSearchPath() {
        return getDelegate().getSearchPath();
    }

    @Override
    public String getUserID() {
        return getDelegate().getUserID();
    }

    @Override
    public boolean hasChanges() {
        return getDelegate().hasChanges();
    }

    @Override
    public boolean hasChildren(final Resource resource) {
        return listChildren(resource).hasNext();
    }

    @Override
    public boolean isLive() {
        return getDelegate().isLive();
    }

    @Override
    public boolean isResourceType(final Resource resource, final String resourceType) {
        return getDelegate().isResourceType(resource, resourceType);
    }

    @Override
    public String map(final HttpServletRequest request, final String resourcePath) {
        return getDelegate().map(request, resourcePath);
    }

    @Override
    public String map(final String resourcePath) {
        return getDelegate().map(resourcePath);
    }

    @Override
    public Iterator<Map<String, Object>> queryResources(final String query, final String language) {
        return getDelegate().queryResources(query, language);
    }

    @Override
    public void refresh() {
        getDelegate().refresh();
    }

    @Override
    public Resource resolve(final String absPath) {
        return getDelegate().resolve(absPath);
    }

    @Override
    @Deprecated
    public Resource resolve(final HttpServletRequest request) {
        return getDelegate().resolve(request);
    }

    @Override
    public Resource resolve(final HttpServletRequest request, final String absPath) {
        return getDelegate().resolve(request, absPath);
    }

    @Override
    public void revert() {
        getDelegate().revert();
    }

    @Override
    public <AdapterType> AdapterType adaptTo(final Class<AdapterType> type) {
        return getDelegate().adaptTo(type);
    }

    @Override
    public Resource getParent(final Resource child) {
        return getDelegate().getParent(child);
    }

    @Override
    public Resource move(final String srcAbsPath, final String destAbsPath) throws PersistenceException {
        return getDelegate().move(srcAbsPath, destAbsPath);
    }

    @Override
    public Map<String, Object> getPropertyMap() {
        return getDelegate().getPropertyMap();
    }

    @Override
    public Resource copy(final String srcAbsPath, final String destAbsPath) throws PersistenceException {
        return getDelegate().copy(srcAbsPath, destAbsPath);
    }

    Resource wrap(Resource resource) {
        if (resource != null &&
                !Resource.RESOURCE_TYPE_NON_EXISTING.equals(resource.getResourceType()) &&
                !(resource.getResourceResolver() instanceof ScriptResourceResolver)) {
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
            resource = ((ScriptResourceResolverResourceWrapper) resource).getDelegate();
        }
        return resource;
    }

    @Override
    public ScriptResourceResolver clone(Map<String, Object> o) throws LoginException {
        return ScriptResourceResolver.wrap(resolver.clone(o), providerSupplier);
    }

    private class ScriptResourceResolverResourceWrapper implements Resource {
        private final Resource delegate;
        public ScriptResourceResolverResourceWrapper(Resource resource) {
            this.delegate = resource;
        }

        Resource getDelegate() {
            return delegate;
        }

        @Override
        public ResourceResolver getResourceResolver() {
            return ScriptResourceResolver.this;
        }

        @Override
        public Resource getChild( final String relPath) {
            return ScriptResourceResolver.this.getResource(this, relPath);
        }

        @Override
        public Iterable<Resource> getChildren() {
            return ScriptResourceResolver.this.getChildren(this);
        }

        @Override
        public String getName() {
            return this.delegate.getName();
        }

        @Override
        public Resource getParent() {
            return ScriptResourceResolver.this.getParent(this);
        }

        @Override
        public String getPath() {
            return this.delegate.getPath();
        }

        @Override
        public ResourceMetadata getResourceMetadata() {
            return this.delegate.getResourceMetadata();
        }

        @Override
        public String getResourceSuperType() {
            return this.delegate.getResourceSuperType();
        }

        @Override
        public String getResourceType() {
            return this.delegate.getResourceType();
        }

        @Override
        public ValueMap getValueMap() {
            return this.delegate.getValueMap();
        }

        @Override
        public boolean hasChildren() {
            return listChildren().hasNext();
        }

        @Override
        public boolean isResourceType(final String resourceType) {
            return this.delegate.isResourceType(resourceType);
        }

        @Override
        public Iterator<Resource> listChildren() {
            return getChildren().iterator();
        }

        @Override
        public <AdapterType> AdapterType adaptTo(final Class<AdapterType> type) {
            return this.delegate.adaptTo(type);
        }
    }
}
