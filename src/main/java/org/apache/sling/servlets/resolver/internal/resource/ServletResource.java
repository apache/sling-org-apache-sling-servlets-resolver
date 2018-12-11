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

import java.util.HashMap;
import java.util.Map;

import javax.servlet.Servlet;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.AbstractResource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;

class ServletResource extends AbstractResource {

    private final ResourceResolver resourceResolver;

    private final Servlet servlet;

    private final String path;

    private final String resourceType;
    private final String resourceSuperType;

    private final ResourceMetadata metadata;

    public ServletResource(ResourceResolver resourceResolver, Servlet servlet, String path) {
        this(resourceResolver, servlet, path, null);
    }

    ServletResource(final ResourceResolver resourceResolver,
                    final Servlet servlet,
                    final String path,
                    final String resourceSuperType) {
        this.resourceResolver = resourceResolver;
        this.servlet = servlet;
        this.path = path;
        this.resourceType = ServletResourceProviderFactory.ensureServletNameExtension(path);
        this.resourceSuperType = StringUtils.isEmpty(resourceSuperType) ? "sling/bundle/resource" : resourceSuperType;
        this.metadata = new ResourceMetadata();
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

    /** Servlet Resources always returns "sling/bundle/resource" as
     * the super type. */
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
        if (servlet.getServletConfig() != null) {
            servletName = servlet.getServletConfig().getServletName();
        }
        if (servletName == null) {
            servletName = servlet.getServletInfo();
        }
        if (servletName == null) {
            servletName = servlet.getClass().getName();
        }
        return servletName;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <AdapterType> AdapterType adaptTo(Class<AdapterType> type) {
        if (type == Servlet.class) {
            return (AdapterType) servlet; // unchecked cast
        } else if ( type == ValueMap.class ) {
            final Map<String, Object> props = new HashMap<>();
            props.put("sling:resourceType", this.getResourceType());
            props.put("sling:resourceSuperType", this.getResourceSuperType());
            props.put("servletName", this.getServletName());
            props.put("servletClass", this.servlet.getClass().getName());

            return (AdapterType) new ValueMapDecorator(props); // unchecked cast
        }

        return super.adaptTo(type);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ", servlet=" + this.getServletName()
            + ", path=" + getPath();
    }

}
