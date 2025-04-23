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
package org.apache.sling.servlets.resolver.internal.helper;

import java.util.Collections;
import java.util.Map;

import junit.framework.TestCase;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.request.builder.Builders;
import org.apache.sling.api.request.builder.SlingHttpServletRequestBuilder;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.testing.resourceresolver.MockResourceResolverFactory;
import org.apache.sling.testing.resourceresolver.MockResourceResolverFactoryOptions;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mockito;

public abstract class HelperTestBase extends TestCase {

    protected MockResourceResolverFactoryOptions resourceResolverOptions;
    protected ResourceResolver resourceResolver;

    protected Resource resource;

    protected String resourcePath;

    protected String resourceType;

    protected String resourceTypePath;

    protected String resourceSuperType;

    protected String resourceSuperTypePath;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        resourceResolverOptions = new MockResourceResolverFactoryOptions();
        MockResourceResolverFactory factory = new MockResourceResolverFactory(resourceResolverOptions);
        resourceResolver = factory.getResourceResolver(null);

        resourceType = "foo:bar";
        resourceTypePath = ResourceUtil.resourceTypeToPath(resourceType);

        resourcePath = "/content/page";
        Resource parent = getOrCreateParentResource(resourceResolver, resourcePath);
        resource = resourceResolver.create(
                parent, "page", Collections.singletonMap(ResourceResolver.PROPERTY_RESOURCE_TYPE, resourceType));
    }

    public static Resource addOrReplaceResource(ResourceResolver resolver, String path, String resourceType) {
        return addOrReplaceResource(
                resolver, path, Collections.singletonMap(ResourceResolver.PROPERTY_RESOURCE_TYPE, resourceType));
    }

    public static Resource addOrReplaceResource(ResourceResolver resolver, String path, Map<String, Object> props) {
        Resource res = null;
        try {
            // if the resource already exists, then remove it
            @Nullable Resource r = resolver.getResource(path);
            if (r != null) {
                resolver.delete(r);
            }

            // create the new resource
            Resource parent = getOrCreateParentResource(resolver, path);
            res = resolver.create(parent, ResourceUtil.getName(path), props);
        } catch (PersistenceException e) {
            fail("Did not expect a persistence exception: " + e.getMessage());
        }
        return res;
    }
    ;

    public static Resource getOrCreateParentResource(ResourceResolver resolver, String path)
            throws PersistenceException {
        Resource parent = null;
        Resource tmp = resolver.getResource("/");
        String[] segments = path.split("/");
        for (int i = 1; i < segments.length - 1; i++) {
            String name = segments[i];
            @Nullable Resource child = tmp.getChild(name);
            if (child == null) {
                tmp = resolver.create(
                        tmp, name, Collections.singletonMap(ResourceResolver.PROPERTY_RESOURCE_TYPE, "nt:folder"));
            } else {
                tmp = child;
            }
        }
        parent = tmp;

        return parent;
    }

    protected SlingJakartaHttpServletRequest makeRequest(String method, String selectors, String extension) {
        final Resource rsrc = Mockito.mock(Resource.class);
        final ResourceMetadata md = new ResourceMetadata();
        Mockito.when(rsrc.getResourceResolver()).thenReturn(resourceResolver);
        Mockito.when(rsrc.getPath()).thenReturn(this.resource.getPath());
        Mockito.when(rsrc.getName()).thenReturn(this.resource.getName());
        Mockito.when(rsrc.getResourceType()).thenReturn(this.resource.getResourceType());
        md.setResolutionPath(rsrc.getPath());
        StringBuilder sb = new StringBuilder();
        sb.append(".");
        if (selectors != null) {
            sb.append(selectors);
            sb.append(".");
        }
        sb.append(extension);
        md.setResolutionPathInfo(sb.toString());
        SlingHttpServletRequestBuilder builder = Builders.newRequestBuilder(rsrc);
        builder.withExtension(extension);
        if (selectors != null) {
            builder.withSelectors(selectors.split("\\."));
        }
        builder.withRequestMethod(method);
        return builder.buildJakartaRequest();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        resourceResolver = null;
        resource = null;
    }
}
