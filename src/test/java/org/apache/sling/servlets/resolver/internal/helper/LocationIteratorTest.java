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

import static org.apache.sling.api.servlets.ServletResolverConstants.DEFAULT_RESOURCE_TYPE;

import java.util.HashMap;
import java.util.Map;

import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;

public class LocationIteratorTest extends HelperTestBase {

    private LocationIterator getLocationIterator(final String resourceType,
            final String resourceSuperType) {
       return this.getLocationIterator(resourceType, resourceSuperType, DEFAULT_RESOURCE_TYPE);
    }

    private LocationIterator getLocationIterator(final String resourceType,
            final String resourceSuperType,
            final String baseResourceType) {
        final LocationIterator li = new LocationIterator(resourceType,
                resourceSuperType,
                baseResourceType,
                this.resourceResolver);
        return li;
    }

    public void testSearchPathEmpty() {
        // expect path gets { "/" }
        resourceResolverOptions.setSearchPaths(null);

        final Resource r = request.getResource();
        LocationIterator li = getLocationIterator(r.getResourceType(),
                r.getResourceSuperType());

        // 1. /foo/bar
        assertTrue(li.hasNext());
        assertEquals("/" + resourceTypePath, li.next());

        // 2. /sling/servlet/default
        assertTrue(li.hasNext());
        assertEquals("/" + DEFAULT_RESOURCE_TYPE, li.next());

        // 3. finished
        assertFalse(li.hasNext());
    }

    public void testSearchPath1Element() {
        String root0 = "/apps/";
        resourceResolverOptions.setSearchPaths(new String[] {
                root0
        });

        final Resource r = request.getResource();
        LocationIterator li = getLocationIterator(r.getResourceType(),
                r.getResourceSuperType());

        // 1. /apps/foo/bar
        assertTrue(li.hasNext());
        assertEquals(root0 + resourceTypePath, li.next());

        // 2. /apps/sling/servlet/default
        assertTrue(li.hasNext());
        assertEquals(root0 + DEFAULT_RESOURCE_TYPE, li.next());

        // 3. finished
        assertFalse(li.hasNext());
    }

    public void testSearchPath2Elements() {
        String root0 = "/apps/";
        String root1 = "/libs/";
        resourceResolverOptions.setSearchPaths(new String[] {
                root0,
                root1
        });

        final Resource r = request.getResource();
        LocationIterator li = getLocationIterator(r.getResourceType(),
                r.getResourceSuperType());

        // 1. /apps/foo/bar
        assertTrue(li.hasNext());
        assertEquals(root0 + resourceTypePath, li.next());

        // 2. /libs/foo/bar
        assertTrue(li.hasNext());
        assertEquals(root1 + resourceTypePath, li.next());

        // 3. /apps/sling/servlet/default
        assertTrue(li.hasNext());
        assertEquals(root0 + DEFAULT_RESOURCE_TYPE, li.next());

        // 4. /libs/sling/servlet/default
        assertTrue(li.hasNext());
        assertEquals(root1 + DEFAULT_RESOURCE_TYPE, li.next());

        // 5. finished
        assertFalse(li.hasNext());
    }

    /**
     * Replace a resource with a different type
     * 
     * @param res the resource to replace
     * @param newResourceType the new resource type, or null to not change it
     * @param newResourceSuperType the new resource super type, or null to not change it
     * @return the new resource
     */
    protected void replaceResource(String newResourceType, String newResourceSuperType) {
        @SuppressWarnings("unchecked")
        Map<String, Object> props = new HashMap<>(resource.adaptTo(Map.class));
        if (newResourceType != null) {
            props.put(ResourceResolver.PROPERTY_RESOURCE_TYPE, newResourceType);
        }
        if (newResourceSuperType != null) {
            props.put("sling:resourceSuperType", newResourceSuperType);
        }
        Resource r = addOrReplaceResource(resourceResolver, resource.getPath(), props);
        request.setResource(r);
    }

    public void testSearchPathEmptyAbsoluteType() {
        // expect path gets { "/" }
        resourceResolverOptions.setSearchPaths(null);

        // absolute resource type
        resourceType = "/foo/bar";
        resourceTypePath = ResourceUtil.resourceTypeToPath(resourceType);
        replaceResource(resourceType, null);

        final Resource r = request.getResource();
        LocationIterator li = getLocationIterator(r.getResourceType(),
                r.getResourceSuperType());

        // 1. /foo/bar
        assertTrue(li.hasNext());
        assertEquals(resourceTypePath, li.next());

        // 2. /sling/servlet/default
        assertTrue(li.hasNext());
        assertEquals("/" + DEFAULT_RESOURCE_TYPE, li.next());

        // 3. finished
        assertFalse(li.hasNext());
    }

    public void testSearchPath1ElementAbsoluteType() {
        String root0 = "/apps/";
        resourceResolverOptions.setSearchPaths(new String[] {
                root0
        });

        // absolute resource type
        resourceType = "/foo/bar";
        resourceTypePath = ResourceUtil.resourceTypeToPath(resourceType);
        replaceResource(resourceType, null);

        final Resource r = request.getResource();
        LocationIterator li = getLocationIterator(r.getResourceType(),
                r.getResourceSuperType());

        // 1. /foo/bar
        assertTrue(li.hasNext());
        assertEquals(resourceTypePath, li.next());

        // 2. /apps/sling/servlet/default
        assertTrue(li.hasNext());
        assertEquals(root0 + DEFAULT_RESOURCE_TYPE, li.next());

        // 3. finished
        assertFalse(li.hasNext());
    }

    public void testSearchPath2ElementsAbsoluteType() {
        String root0 = "/apps/";
        String root1 = "/libs/";
        resourceResolverOptions.setSearchPaths(new String[] {
                root0,
                root1
        });

        // absolute resource type
        resourceType = "/foo/bar";
        resourceTypePath = ResourceUtil.resourceTypeToPath(resourceType);
        replaceResource(resourceType, null);

        final Resource r = request.getResource();
        LocationIterator li = getLocationIterator(r.getResourceType(),
                r.getResourceSuperType());

        // 1. /foo/bar
        assertTrue(li.hasNext());
        assertEquals(resourceTypePath, li.next());

        // 2. /apps/sling/servlet/default
        assertTrue(li.hasNext());
        assertEquals(root0 + DEFAULT_RESOURCE_TYPE, li.next());

        // 3. /libs/sling/servlet/default
        assertTrue(li.hasNext());
        assertEquals(root1 + DEFAULT_RESOURCE_TYPE, li.next());

        // 4. finished
        assertFalse(li.hasNext());
    }

    public void testSearchPathEmptyWithSuper() {
        // expect path gets { "/" }
        resourceResolverOptions.setSearchPaths(null);

        // set resource super type
        resourceSuperType = "foo:superBar";
        resourceSuperTypePath = ResourceUtil.resourceTypeToPath(resourceSuperType);
        replaceResource(null, resourceSuperType);

        final Resource r = request.getResource();
        LocationIterator li = getLocationIterator(r.getResourceType(),
                r.getResourceSuperType());

        // 1. /foo/bar
        assertTrue(li.hasNext());
        assertEquals("/" + resourceTypePath, li.next());

        // 2. /foo/superBar
        assertTrue(li.hasNext());
        assertEquals("/" + resourceSuperTypePath, li.next());

        // 3. /sling/servlet/default
        assertTrue(li.hasNext());
        assertEquals("/" + DEFAULT_RESOURCE_TYPE, li.next());

        // 4. finished
        assertFalse(li.hasNext());
    }

    public void testSearchPath1ElementWithSuper() {
        String root0 = "/apps/";
        resourceResolverOptions.setSearchPaths(new String[] {
                root0
        });

        // set resource super type
        resourceSuperType = "foo:superBar";
        resourceSuperTypePath = ResourceUtil.resourceTypeToPath(resourceSuperType);
        replaceResource(null, resourceSuperType);

        final Resource r = request.getResource();
        LocationIterator li = getLocationIterator(r.getResourceType(),
                r.getResourceSuperType());

        // 1. /apps/foo/bar
        assertTrue(li.hasNext());
        assertEquals(root0 + resourceTypePath, li.next());

        // 2. /apps/foo/superBar
        assertTrue(li.hasNext());
        assertEquals(root0 + resourceSuperTypePath, li.next());

        // 3. /apps/sling/servlet/default
        assertTrue(li.hasNext());
        assertEquals(root0 + DEFAULT_RESOURCE_TYPE, li.next());

        // 4. finished
        assertFalse(li.hasNext());
    }

    public void testSearchPath2ElementsWithSuper() {
        String root0 = "/apps/";
        String root1 = "/libs/";
        resourceResolverOptions.setSearchPaths(new String[] {
                root0,
                root1
        });

        // set resource super type
        resourceSuperType = "foo:superBar";
        resourceSuperTypePath = ResourceUtil.resourceTypeToPath(resourceSuperType);
        replaceResource(null, resourceSuperType);

        final Resource r = request.getResource();
        LocationIterator li = getLocationIterator(r.getResourceType(),
                r.getResourceSuperType());

        // 1. /apps/foo/bar
        assertTrue(li.hasNext());
        assertEquals(root0 + resourceTypePath, li.next());

        // 2. /libs/foo/bar
        assertTrue(li.hasNext());
        assertEquals(root1 + resourceTypePath, li.next());

        // 3. /apps/foo/superBar
        assertTrue(li.hasNext());
        assertEquals(root0 + resourceSuperTypePath, li.next());

        // 4. /libs/foo/superBar
        assertTrue(li.hasNext());
        assertEquals(root1 + resourceSuperTypePath, li.next());

        // 5. /apps/sling/servlet/default
        assertTrue(li.hasNext());
        assertEquals(root0 + DEFAULT_RESOURCE_TYPE, li.next());

        // 6. /libs/sling/servlet/default
        assertTrue(li.hasNext());
        assertEquals(root1 + DEFAULT_RESOURCE_TYPE, li.next());

        // 7. finished
        assertFalse(li.hasNext());
    }

    public void testSearchPathEmptyAbsoluteTypeWithSuper() {
        // expect path gets { "/" }
        resourceResolverOptions.setSearchPaths(null);

        // absolute resource type
        resourceType = "/foo/bar";
        resourceTypePath = ResourceUtil.resourceTypeToPath(resourceType);

        // set resource super type
        resourceSuperType = "foo:superBar";
        resourceSuperTypePath = ResourceUtil.resourceTypeToPath(resourceSuperType);
        replaceResource(resourceType, resourceSuperType);

        final Resource r = request.getResource();
        LocationIterator li = getLocationIterator(r.getResourceType(),
                r.getResourceSuperType());

        // 1. /foo/bar
        assertTrue(li.hasNext());
        assertEquals(resourceTypePath, li.next());

        // 2. /foo/superBar
        assertTrue(li.hasNext());
        assertEquals("/" + resourceSuperTypePath, li.next());

        // 3. /sling/servlet/default
        assertTrue(li.hasNext());
        assertEquals("/" + DEFAULT_RESOURCE_TYPE, li.next());

        // 4. finished
        assertFalse(li.hasNext());
    }

    public void testSearchPath1ElementAbsoluteTypeWithSuper() {
        String root0 = "/apps/";
        resourceResolverOptions.setSearchPaths(new String[] {
                root0
        });

        // absolute resource type
        resourceType = "/foo/bar";
        resourceTypePath = ResourceUtil.resourceTypeToPath(resourceType);

        // set resource super type
        resourceSuperType = "foo:superBar";
        resourceSuperTypePath = ResourceUtil.resourceTypeToPath(resourceSuperType);
        replaceResource(resourceType, resourceSuperType);

        final Resource r = request.getResource();
        LocationIterator li = getLocationIterator(r.getResourceType(),
                r.getResourceSuperType());

        // 1. /foo/bar
        assertTrue(li.hasNext());
        assertEquals(resourceTypePath, li.next());

        // 2. /apps/foo/superBar
        assertTrue(li.hasNext());
        assertEquals(root0 + resourceSuperTypePath, li.next());

        // 3. /apps/sling/servlet/default
        assertTrue(li.hasNext());
        assertEquals(root0 + DEFAULT_RESOURCE_TYPE, li.next());

        // 4. finished
        assertFalse(li.hasNext());
    }

    public void testSearchPath2ElementsAbsoluteTypeWithSuper() {
        String root0 = "/apps/";
        String root1 = "/libs/";
        resourceResolverOptions.setSearchPaths(new String[] {
                root0,
                root1
        });

        // absolute resource type
        resourceType = "/foo/bar";
        resourceTypePath = ResourceUtil.resourceTypeToPath(resourceType);

        // set resource super type
        resourceSuperType = "foo:superBar";
        resourceSuperTypePath = ResourceUtil.resourceTypeToPath(resourceSuperType);
        replaceResource(resourceType, resourceSuperType);

        final Resource r = request.getResource();
        LocationIterator li = getLocationIterator(r.getResourceType(),
                r.getResourceSuperType());

        // 1. /foo/bar
        assertTrue(li.hasNext());
        assertEquals(resourceTypePath, li.next());

        // 2. /apps/foo/superBar
        assertTrue(li.hasNext());
        assertEquals(root0 + resourceSuperTypePath, li.next());

        // 3. /libs/foo/superBar
        assertTrue(li.hasNext());
        assertEquals(root1 + resourceSuperTypePath, li.next());

        // 4. /apps/sling/servlet/default
        assertTrue(li.hasNext());
        assertEquals(root0 + DEFAULT_RESOURCE_TYPE, li.next());

        // 5. /libs/sling/servlet/default
        assertTrue(li.hasNext());
        assertEquals(root1 + DEFAULT_RESOURCE_TYPE, li.next());

        // 6. finished
        assertFalse(li.hasNext());
    }

    public void testScriptNameWithoutResourceType() {
        String root0 = "/apps/";
        String root1 = "/libs/";
        resourceResolverOptions.setSearchPaths(new String[] {
                root0,
                root1
        });
        LocationIterator li = getLocationIterator("",
                null,
                "");
        assertTrue(li.hasNext());
        assertEquals("/apps/", li.next());
        assertTrue(li.hasNext());
        assertEquals("/libs/", li.next());
        assertFalse(li.hasNext());
    }

    public void testScriptNameWithResourceType() {
        String root0 = "/apps/";
        String root1 = "/libs/";
        resourceResolverOptions.setSearchPaths(new String[] {
                root0,
                root1
        });
        LocationIterator li = getLocationIterator("a/b",
                null);
        assertTrue(li.hasNext());
        assertEquals(root0 + "a/b", li.next());
        assertTrue(li.hasNext());
        assertEquals(root1 + "a/b", li.next());
        assertTrue(li.hasNext());
        assertEquals(root0 + DEFAULT_RESOURCE_TYPE, li.next());
        assertTrue(li.hasNext());
        assertEquals(root1 + DEFAULT_RESOURCE_TYPE, li.next());
        assertFalse(li.hasNext());
    }

    public void testScriptNameWithResourceTypeAndSuperType() {
        String root0 = "/apps/";
        String root1 = "/libs/";
        resourceResolverOptions.setSearchPaths(new String[] {
                root0,
                root1
        });
        LocationIterator li = getLocationIterator("a/b",
                "c/d");
        assertTrue(li.hasNext());
        assertEquals(root0 + "a/b", li.next());
        assertTrue(li.hasNext());
        assertEquals(root1 + "a/b", li.next());
        assertTrue(li.hasNext());
        assertEquals(root0 + "c/d", li.next());
        assertTrue(li.hasNext());
        assertEquals(root1 + "c/d", li.next());
        assertTrue(li.hasNext());
        assertEquals(root0 + DEFAULT_RESOURCE_TYPE, li.next());
        assertTrue(li.hasNext());
        assertEquals(root1 + DEFAULT_RESOURCE_TYPE, li.next());
        assertFalse(li.hasNext());
    }

    public void testCircularResourceTypeHierarchy() {
        final String root1 = "/libs/";
        resourceResolverOptions.setSearchPaths(new String[] {
                root1
        });

        // resource type and super type for start resource
        final String resourceType = "foo/bar";
        final String resourceSuperType = "foo/check1";
        final String resourceSuperType2 = "foo/check2";

        String resource2Path = root1 + resourceSuperType;
        Map<String, Object> resource2Props = new HashMap<>();
        resource2Props.put(ResourceResolver.PROPERTY_RESOURCE_TYPE, resourceType);
        resource2Props.put("sling:resourceSuperType", resourceSuperType2);
        try {
            resourceResolver.create(getOrCreateParentResource(resourceResolver, resource2Path),
                    ResourceUtil.getName(resource2Path),
                    resource2Props);
        } catch (PersistenceException e) {
            fail("Did not expect a persistence exception: " + e.getMessage());
        }

        String resource3Path = root1 + resourceSuperType2;
        Map<String, Object> resource3Props = new HashMap<>();
        resource3Props.put(ResourceResolver.PROPERTY_RESOURCE_TYPE, resourceType);
        resource3Props.put("sling:resourceSuperType", resourceType);
        try {
            resourceResolver.create(getOrCreateParentResource(resourceResolver, resource3Path),
                    ResourceUtil.getName(resource3Path),
                    resource3Props);
        } catch (PersistenceException e) {
            fail("Did not expect a persistence exception: " + e.getMessage());
        }

        LocationIterator li = getLocationIterator(resourceType,
                resourceSuperType);

        // 1. /libs/foo/bar
        assertTrue(li.hasNext());
        assertEquals(root1 + resourceType, li.next());

        // 1. /libs/foo/check1
        assertTrue(li.hasNext());
        assertEquals(root1 + resourceSuperType, li.next());

        // 3. /libs/foo/check2
        assertTrue(li.hasNext());
        assertEquals(root1 + resourceSuperType2, li.next());

        // 4. /libs/sling/servlet/default
        assertTrue(li.hasNext());
        assertEquals(root1 + DEFAULT_RESOURCE_TYPE, li.next());

        // 5. finished
        assertFalse(li.hasNext());
    }
}
