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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import jakarta.servlet.Servlet;
import org.apache.sling.api.resource.AbstractResource;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.ResourceWrapper;
import org.apache.sling.api.scripting.SlingScript;
import org.apache.sling.servlets.resolver.internal.resource.ServletResource;

/**
 * ScriptResource is a resource wrapper of a resource fetched by a
 * per thread resource resolver.
 * As the script engines usually keep a reference to this resource
 * for a longer time, the resource switches internally to
 * a resource fetched by the shared resource resolver.
 *
 * We can't extend ResourceWrapper as its not possible to clear
 * the wrapped resource field.
 */
public class ScriptResource extends AbstractResource {

    private AtomicReference<Resource> sharedResource = new AtomicReference<>();

    private final ResourceResolver sharedResourceResolver;

    private final Supplier<ResourceResolver> perThreadResourceResolver;

    private final String path;

    public ScriptResource(
            final Resource resource,
            final Supplier<ResourceResolver> perThreadScriptResolver,
            final ResourceResolver sharedResourceResolver) {
        this.path = resource.getPath();
        this.sharedResourceResolver = sharedResourceResolver;
        this.perThreadResourceResolver = perThreadScriptResolver;
    }

    private Resource getActiveResource() {
        ResourceResolver perThreadResolver = this.perThreadResourceResolver.get();
        if (perThreadResolver != null && perThreadResolver.isLive()) {
            return perThreadResolver.getResource(this.path);
        }
        Resource resource = this.sharedResource.get();
        if (resource == null) {
            resource = this.sharedResourceResolver.getResource(this.path);
            this.sharedResource.set(resource);
        }
        return resource;
    }

    /**
     * @see org.apache.sling.api.resource.Resource#getResourceType()
     */
    @Override
    public String getResourceType() {
        return this.getActiveResource().getResourceType();
    }

    /**
     * @see org.apache.sling.api.resource.Resource#getResourceSuperType()
     */
    @Override
    public String getResourceSuperType() {
        return this.getActiveResource().getResourceSuperType();
    }

    /**
     * @see org.apache.sling.api.resource.Resource#getResourceResolver()
     */
    @Override
    public ResourceResolver getResourceResolver() {
        return this.getActiveResource().getResourceResolver();
    }

    /**
     * @see org.apache.sling.api.adapter.SlingAdaptable#adaptTo(java.lang.Class)
     */
    @Override
    public <T> T adaptTo(final Class<T> type) {
        if (type == javax.servlet.Servlet.class) {
            Resource activeResource = this.getActiveResource();
            while (activeResource instanceof ResourceWrapper) {
                activeResource = ((ResourceWrapper) activeResource).getResource();
            }
            if (!(activeResource instanceof ServletResource)) {
                final javax.servlet.Servlet s = (javax.servlet.Servlet) super.adaptTo(type);
                if (s != null) {
                    return (T) s;
                }
                final Servlet sj = (Servlet) super.adaptTo(Servlet.class);
                if (sj != null) {
                    return (T) ServletWrapperUtil.toJavaxServlet(sj);
                }
            }
        } else if (type == Servlet.class) {
            Resource activeResource = this.getActiveResource();
            while (activeResource instanceof ResourceWrapper) {
                activeResource = ((ResourceWrapper) activeResource).getResource();
            }
            if (!(activeResource instanceof ServletResource)) {
                final Servlet s = (Servlet) super.adaptTo(type);
                if (s != null) {
                    return (T) s;
                }
                final javax.servlet.Servlet sx = (javax.servlet.Servlet) super.adaptTo(javax.servlet.Servlet.class);
                if (sx != null) {
                    return (T) ServletWrapperUtil.toJakartaServlet(sx);
                }
            }
        } else if (type == SlingScript.class) {
            final SlingScript s = (SlingScript) super.adaptTo(type);
            if (s != null) {
                return (T) s;
            }
        } else if (type == SlingScript.class) {
            final SlingScript s = (SlingScript) super.adaptTo(type);
            if (s != null) {
                return (T) s;
            }
        }
        return this.getActiveResource().adaptTo(type);
    }

    /**
     * @see org.apache.sling.api.resource.Resource#getPath()
     */
    @Override
    public String getPath() {
        return this.path;
    }

    /**
     * @see org.apache.sling.api.resource.Resource#getResourceMetadata()
     */
    @Override
    public ResourceMetadata getResourceMetadata() {
        return this.getActiveResource().getResourceMetadata();
    }

    /**
     * @see org.apache.sling.api.resource.AbstractResource#getName()
     */
    @Override
    public String getName() {
        return ResourceUtil.getName(this.path);
    }

    /**
     * @see org.apache.sling.api.resource.AbstractResource#getParent()
     */
    @Override
    public Resource getParent() {
        return this.getActiveResource().getParent();
    }

    /**
     * @see org.apache.sling.api.resource.AbstractResource#getChild(java.lang.String)
     */
    @Override
    public Resource getChild(String relPath) {
        return this.getActiveResource().getChild(relPath);
    }

    /**
     * @see org.apache.sling.api.resource.AbstractResource#listChildren()
     */
    @Override
    public Iterator<Resource> listChildren() {
        return this.getActiveResource().listChildren();
    }

    /**
     * @see org.apache.sling.api.resource.AbstractResource#getChildren()
     */
    @Override
    public Iterable<Resource> getChildren() {
        return this.getActiveResource().getChildren();
    }

    /**
     * @see org.apache.sling.api.resource.AbstractResource#isResourceType(java.lang.String)
     */
    @Override
    public boolean isResourceType(String resourceType) {
        return this.getActiveResource().isResourceType(resourceType);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + ", type=" + getResourceType()
                + ", superType=" + getResourceSuperType()
                + ", path=" + getPath();
    }
}
