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

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.Servlet;
import org.apache.sling.api.resource.AbstractResource;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.scripting.spi.bundle.BundledRenderUnit;
import org.apache.sling.servlets.resolver.internal.ServletWrapperUtil;
import org.apache.sling.servlets.resolver.internal.bundle.BundledScriptServlet;

public class ServletResource extends AbstractResource {

    public static final String DEFAULT_RESOURCE_SUPER_TYPE = "sling/bundle/resource";

    private final ResourceResolver resourceResolver;

    private final Servlet servlet;

    private final String path;

    private final String resourceType;
    private final String resourceSuperType;

    private final ResourceMetadata metadata;

    private AtomicReference<Resource> wrapped = new AtomicReference<>();

    public ServletResource(ResourceResolver resourceResolver, Servlet servlet, String path) {
        this(resourceResolver, servlet, path, null);
    }

    ServletResource(
            final ResourceResolver resourceResolver,
            final Servlet servlet,
            final String path,
            final String resourceSuperType) {
        this.resourceResolver = resourceResolver;
        this.servlet = servlet;
        this.path = path;
        this.resourceType = ServletResourceProviderFactory.ensureServletNameExtension(path);
        this.resourceSuperType = (resourceSuperType == null || resourceSuperType.isEmpty())
                ? DEFAULT_RESOURCE_SUPER_TYPE
                : resourceSuperType;
        this.metadata = new ResourceMetadata();
        this.metadata.put("sling.servlet.resource", "true");
    }

    void setWrappedResource(Resource wrapped) {
        if (wrapped != null && !RESOURCE_TYPE_NON_EXISTING.equals(wrapped.getResourceType())) {
            this.wrapped.set(wrapped);
        }
    }

    @Override
    public ResourceMetadata getResourceMetadata() {
        return metadata;
    }

    @Override
    public ResourceResolver getResourceResolver() {
        return resourceResolver;
    }

    @Override
    public String getResourceType() {
        return resourceType;
    }

    @Override
    public String getResourceSuperType() {
        return resourceSuperType;
    }

    @Override
    public String getPath() {
        return path;
    }

    private String getServletName() {
        String servletName = null;
        if (servlet != null) {
            if (servlet.getServletConfig() != null) {
                servletName = servlet.getServletConfig().getServletName();
            }
            if (servletName == null) {
                servletName = servlet.getServletInfo();
            }
            if (servletName == null) {
                servletName = servlet.getClass().getName();
            }
        }
        return servletName;
    }

    private BundledScriptServlet isBundledScriptServlet() {
        if (servlet instanceof BundledScriptServlet) {
            return (BundledScriptServlet) servlet;
        }
        if (servlet instanceof ServletWrapperUtil.JakartaScriptServletWrapper) {
            final javax.servlet.Servlet w = ((ServletWrapperUtil.JakartaScriptServletWrapper) servlet).servlet;
            if (w instanceof BundledScriptServlet) {
                return (BundledScriptServlet) w;
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T adaptTo(Class<T> type) {
        Resource wrappedResource = this.wrapped.get();
        if (type == Servlet.class && servlet != null) {
            return (T) servlet; // unchecked cast
        }
        if (type == javax.servlet.Servlet.class && servlet != null) {
            return (T) ServletWrapperUtil.toJavaxServlet(servlet); // unchecked cast
        }
        if (type == InputStream.class && isBundledScriptServlet() != null) {
            InputStream result = isBundledScriptServlet().getInputStream();
            if (result != null) {
                return (T) result;
            }
        }

        if (type == BundledRenderUnit.class && isBundledScriptServlet() != null) {
            return (T) isBundledScriptServlet().getBundledRenderUnit();
        }

        if (wrappedResource != null) {
            T result = wrappedResource.adaptTo(type);
            if (result != null) {
                return result;
            }
        }

        if (type == ValueMap.class) {
            final Map<String, Object> props = new HashMap<>();
            props.put("sling:resourceType", this.getResourceType());
            props.put("sling:resourceSuperType", this.getResourceSuperType());
            if (servlet != null) {
                props.put("servletName", this.getServletName());
                props.put("servletClass", this.servlet.getClass().getName());
            }

            return (T) new ValueMapDecorator(props); // unchecked cast
        }

        return super.adaptTo(type);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ", servlet=" + this.getServletName() + ", path=" + getPath();
    }
}
