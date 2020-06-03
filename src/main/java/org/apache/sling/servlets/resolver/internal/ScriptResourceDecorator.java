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
import javax.servlet.http.HttpServletRequest;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceDecorator;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.servlets.resolver.internal.resource.MergingServletResourceProvider;
import org.apache.sling.spi.resource.provider.ResolveContext;
import org.apache.sling.spi.resource.provider.ResourceContext;
import org.apache.sling.spi.resource.provider.ResourceProvider;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component
public class ScriptResourceDecorator implements ResourceDecorator {
    private final MergingServletResourceProvider provider;

    @Activate
    public ScriptResourceDecorator(@Reference MergingServletResourceProvider provider) {
        this.provider = provider;
    }

    @Override
    public Resource decorate(Resource resource) {
        String path = ResourceUtil.normalize(resource.getPath());
        if (this.provider.isRootOf(path)) {
            String resolutionPath = resource.getResourceMetadata().getResolutionPath();
            Resource script = getResource(resource, path);
            if (script == resource && Resource.RESOURCE_TYPE_NON_EXISTING.equals(resource.getResourceType())) {
                int idx = path.indexOf('.');
                if (idx != -1) {
                    path = path.substring(0, idx);
                    script = getResource(resource, path);
                    resolutionPath = path;
                }
            }
            if (script != resource) {
                script.getResourceMetadata().putAll(resource.getResourceMetadata());
                script.getResourceMetadata().setResolutionPath(resolutionPath);
            }

            return script;
        }
        else {
            return resource;
        }
    }

    @Override
    public Resource decorate(Resource resource, HttpServletRequest request) {
        return decorate(resource);
    }

    private Resource getResource(Resource resource, String path) {
        return provider.getResource(new ResolveContext<Void>() {
            @Override
            public ResourceResolver getResourceResolver() {
                return new ScriptResourceResolver(resource.getResourceResolver(), () -> provider);
            }

            @Override
            public Void getProviderState() {
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
                        return resource;
                    }

                    @Override
                    public Iterator<Resource> listChildren(ResolveContext<Object> ctx, Resource parent) {
                        return null;
                    }
                };
            }
        }, path);
    }
}
