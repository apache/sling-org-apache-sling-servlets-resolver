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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;

public class LocationCollectorTest extends HelperTestBase {

    List<String> getLocations(final String resourceType,
            final String resourceSuperType) {
        return getLocations(resourceType, resourceSuperType, DEFAULT_RESOURCE_TYPE);
    }
    
    List<String> getLocations( final String resourceType,
            final String resourceSuperType,
            final String baseResourceType) {
        return LocationCollector.getLocations(resourceType,
                resourceSuperType,
                baseResourceType,
                this.resourceResolver);
    }
    
    public void testSearchPathEmpty() {
        // expect path gets { "/" }
        resourceResolverOptions.setSearchPaths(null);

        final Resource r = request.getResource();
        List<String> loc = getLocations(r.getResourceType(),
                r.getResourceSuperType());
        
        List<String> expected = Arrays.asList(
        		"/" + resourceTypePath, // /foo/bar
                "/" + DEFAULT_RESOURCE_TYPE); // /sling/servlet/default
        assertThat(loc,is(expected));
    }
    
    public void testSearchPath1Element() {
        String root0 = "/apps/";
        resourceResolverOptions.setSearchPaths(new String[] {
                root0
        });

        final Resource r = request.getResource();
        List<String> loc = getLocations(r.getResourceType(),
                r.getResourceSuperType());
        
        List<String> expected = Arrays.asList(
        		root0 + resourceTypePath, // /apps/foo/bar
                root0 + DEFAULT_RESOURCE_TYPE); // /apps/sling/servlet/default
        assertThat(loc,is(expected));
    }
    
    public void testSearchPath2Elements() {
        String root0 = "/apps/";
        String root1 = "/libs/";
        resourceResolverOptions.setSearchPaths(new String[] {
                root0,
                root1
        });

        final Resource r = request.getResource();
        List<String> loc = getLocations(r.getResourceType(),
                r.getResourceSuperType());
        
        List<String> expected = Arrays.asList(
        		root0 + resourceTypePath, // /apps/foo/bar
                root1 + resourceTypePath, // /libs/foo/bar
                root0 + DEFAULT_RESOURCE_TYPE, // /apps/sling/servlet/default
                root1 + DEFAULT_RESOURCE_TYPE); // /libs/sling/servlet/default
        assertThat(loc,is(expected));
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
        List<String> loc = getLocations(r.getResourceType(),
                r.getResourceSuperType());
        
        List<String> expected = Arrays.asList(
        		resourceTypePath, // /foo/bar
                "/" + DEFAULT_RESOURCE_TYPE); // /sling/servlet/default
        assertThat(loc,is(expected));
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
        List<String> loc = getLocations(r.getResourceType(),
                r.getResourceSuperType());
        
        
        List<String> expected = Arrays.asList(
        		resourceTypePath, // /foo/bar
                root0 + DEFAULT_RESOURCE_TYPE); // /apps/sling/servlet/default
        assertThat(loc,is(expected));
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
        List<String> loc = getLocations(r.getResourceType(),
                r.getResourceSuperType());
        
        List<String> expected = Arrays.asList(
        		resourceTypePath, // /foo/bar
                root0 + DEFAULT_RESOURCE_TYPE, // /apps/sling/servlet/default
                root1 + DEFAULT_RESOURCE_TYPE); // /libs/sling/servlet/default
        assertThat(loc,is(expected));
    }
    
    public void testSearchPathEmptyWithSuper() {
        // expect path gets { "/" }
        resourceResolverOptions.setSearchPaths(null);

        // set resource super type
        resourceSuperType = "foo:superBar";
        resourceSuperTypePath = ResourceUtil.resourceTypeToPath(resourceSuperType);
        replaceResource(null, resourceSuperType);

        final Resource r = request.getResource();
        List<String> loc = getLocations(r.getResourceType(),
                r.getResourceSuperType());
        
        List<String> expected = Arrays.asList(
        		"/" + resourceTypePath, // /foo/bar
                "/" + resourceSuperTypePath, // /foo/superBar
                "/" + DEFAULT_RESOURCE_TYPE); // /sling/servlet/default
        assertThat(loc,is(expected));
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
        List<String> loc = getLocations(r.getResourceType(),
                r.getResourceSuperType());
        
        List<String> expected = Arrays.asList(
        		root0 + resourceTypePath, // /apps/foo/bar
                root0 + resourceSuperTypePath, // /apps/foo/superBar
                root0 + DEFAULT_RESOURCE_TYPE); // /apps/sling/servlet/default
        assertThat(loc,is(expected));
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
        List<String> loc = getLocations(r.getResourceType(),
                r.getResourceSuperType());
        
        List<String> expected = Arrays.asList(
        		root0 + resourceTypePath, // /apps/foo/bar
                root1 + resourceTypePath, // /libs/foo/bar
                root0 + resourceSuperTypePath, // /apps/foo/superBar
                root1 + resourceSuperTypePath, // /libs/foo/superBar
                root0 + DEFAULT_RESOURCE_TYPE, // /apps/sling/servlet/default
                root1 + DEFAULT_RESOURCE_TYPE); // /libs/sling/servlet/default
        assertThat(loc,is(expected));
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
        List<String> loc = getLocations(r.getResourceType(),
                r.getResourceSuperType());
        
        List<String> expected = Arrays.asList(
        		resourceTypePath, // /foo/bar
                "/" + resourceSuperTypePath, // /foo/superBar
                "/" + DEFAULT_RESOURCE_TYPE); // /sling/servlet/default
        assertThat(loc,is(expected));
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
        List<String> loc = getLocations(r.getResourceType(),
                r.getResourceSuperType());
        
        List<String> expected = Arrays.asList(
        		resourceTypePath, // /foo/bar
                root0 + resourceSuperTypePath, // /apps/foo/superBar
                root0 + DEFAULT_RESOURCE_TYPE); // /apps/sling/servlet/default
        assertThat(loc,is(expected));
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
        List<String> loc = getLocations(r.getResourceType(),
                r.getResourceSuperType());
        
        List<String> expected = Arrays.asList(
        		resourceTypePath, // /foo/bar
                root0 + resourceSuperTypePath, // /apps/foo/superBar
                root1 + resourceSuperTypePath, // /libs/foo/superBar
                root0 + DEFAULT_RESOURCE_TYPE, // /apps/sling/servlet/default
                root1 + DEFAULT_RESOURCE_TYPE); // /libs/sling/servlet/default
        assertThat(loc,is(expected));
    }
    
    public void testScriptNameWithoutResourceType() {
        String root0 = "/apps/";
        String root1 = "/libs/";
        resourceResolverOptions.setSearchPaths(new String[] {
                root0,
                root1
        });
        List<String> loc = getLocations("",
                null,"");
        
        List<String> expected = Arrays.asList("/apps/","/libs/");
        assertThat(loc,is(expected));
    }
    
    public void testScriptNameWithResourceType() {
        String root0 = "/apps/";
        String root1 = "/libs/";
        resourceResolverOptions.setSearchPaths(new String[] {
                root0,
                root1
        });
        List<String> loc = getLocations("a/b", null);
        
        List<String> expected = Arrays.asList(
        		root0 + "a/b",
                root1 + "a/b",
                root0 + DEFAULT_RESOURCE_TYPE,
                root1 + DEFAULT_RESOURCE_TYPE);
        assertThat(loc,is(expected));
    }
    
    public void testScriptNameWithResourceTypeAndSuperType() {
        String root0 = "/apps/";
        String root1 = "/libs/";
        resourceResolverOptions.setSearchPaths(new String[] {
                root0,
                root1
        });
        
        List<String> loc = getLocations("a/b", "c/d");

        List<String> expected = Arrays.asList(
        		root0 + "a/b",
                root1 + "a/b",
                root0 + "c/d",
                root1 + "c/d",
                root0 + DEFAULT_RESOURCE_TYPE,
                root1 + DEFAULT_RESOURCE_TYPE);
        assertThat(loc,is(expected));
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
        
        List<String> loc = getLocations(resourceType, resourceSuperType);
        
        List<String> expected = Arrays.asList(
        		root1 + resourceType, // /libs/foo/bar
                root1 + resourceSuperType, // /libs/foo/check1
                root1 + resourceSuperType2, // /libs/foo/check2
                root1 + DEFAULT_RESOURCE_TYPE); // /libs/sling/servlet/default
        assertThat(loc,is(expected));
    }
    
    
    public void testResolveDefaultResourceType() {
    	
    	List<String> loc = getLocations(DEFAULT_RESOURCE_TYPE, resourceSuperType);
    	
    	List<String> expected = Arrays.asList(
    			"/apps/sling/servlet/default",
    			"/libs/sling/servlet/default",
    			"/apps/sling/servlet/default",
    			"/libs/sling/servlet/default"
    			);
    	assertThat(loc,is(expected));
    }
 
    
    public void testAbsoluteResourceSuperType() throws Exception {
        final String root = "/apps/";
        resourceResolverOptions.setSearchPaths(new String[] {
                root
        });
        
        String resourceType="a/b";
        String resourceTypePath= root + resourceType;
        
        String resourceSuperType= "/apps/c/d";
        String resourceSuperTypePath = resourceSuperType;
        
        Map<String, Object> resourceTypeProps = new HashMap<>();
        resourceTypeProps.put(ResourceResolver.PROPERTY_RESOURCE_TYPE, resourceType);
        resourceTypeProps.put("sling:resourceSuperType", resourceSuperType);
        
		resourceResolver.create(getOrCreateParentResource(resourceResolver, resourceTypePath),
				ResourceUtil.getName(resourceTypePath), resourceTypeProps);
		resourceResolver.create(getOrCreateParentResource(resourceResolver, resourceSuperTypePath),
				ResourceUtil.getName(resourceSuperTypePath), null);
        
        
    	List<String> loc = getLocations(resourceType, resourceSuperType);
    	
    	List<String> expected = Arrays.asList(
    			resourceTypePath,      			// /apps/a/b
    			resourceSuperTypePath, 			// /apps/c/d
    			root + DEFAULT_RESOURCE_TYPE 	// /apps/sling/servlet/default
    			);
    	assertThat(loc,is(expected));
    }
    
    
    public void testNoSuperType() throws Exception {
        final String root = "/apps/";
        resourceResolverOptions.setSearchPaths(new String[] {
                root
        });
        
        String resourceType="a/b";
        String resourceTypePath= root + resourceType;
        
        Map<String, Object> resourceTypeProps = new HashMap<>();
        resourceTypeProps.put(ResourceResolver.PROPERTY_RESOURCE_TYPE, resourceType);
        
		resourceResolver.create(getOrCreateParentResource(resourceResolver, resourceTypePath),
				ResourceUtil.getName(resourceTypePath), resourceTypeProps);
        
        
    	List<String> loc = getLocations(resourceType, resourceSuperType);
    	
    	List<String> expected = Arrays.asList(
    			resourceTypePath,      			// /apps/a/b
    			root + DEFAULT_RESOURCE_TYPE 	// /apps/sling/servlet/default
    			);
    	assertThat(loc,is(expected));
    }
    
}
