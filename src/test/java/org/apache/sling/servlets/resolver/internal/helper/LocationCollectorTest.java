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
import static org.apache.sling.servlets.resolver.internal.helper.HelperTestBase.addOrReplaceResource;
import static org.apache.sling.servlets.resolver.internal.helper.HelperTestBase.getOrCreateParentResource;
import static org.apache.sling.servlets.resolver.internal.helper.IsSameResourceList.isSameResourceList;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.SyntheticResource;
import org.apache.sling.commons.testing.sling.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;


public class LocationCollectorTest {
	
	@Rule
	public final SlingContext context = new SlingContext();
	
	SearchPathOptions searchPathOptions = new SearchPathOptions();
    protected String resourcePath;
    protected String resourceType;
    protected String resourceTypePath;
    protected String resourceSuperType;
    protected String resourceSuperTypePath;
	
	protected Resource resource;
	protected MockSlingHttpServletRequest request;
	
	
	protected ResourceResolver resolver; // required because of the spy
	
	@Before
	public void setup() throws Exception {

		searchPathOptions = new SearchPathOptions();
		resolver = Mockito.spy(context.resourceResolver());
		Mockito.when(resolver.getSearchPath()).thenAnswer( invocation -> {
			return searchPathOptions.getSearchPath();
		});
		
        resourceType = "foo:bar";
        resourceTypePath = ResourceUtil.resourceTypeToPath(resourceType);

        resourcePath = "/content/page";
        context.build().resource("/content",Collections.emptyMap()).commit();
        Resource parent = resolver.getResource("/content");
        resource = resolver.create(parent, "page",
                Collections.singletonMap(ResourceResolver.PROPERTY_RESOURCE_TYPE, resourceType));

        request = new MockSlingHttpServletRequest(resourcePath, "print.A4", "html", null, null);
        request.setMethod("GET");
        request.setResourceResolver(resolver);
        request.setResource(resource);
		
	}
    
    @Test
    public void testSearchPathEmpty() {
        // expect path gets { "/" }
        searchPathOptions.setSearchPaths(null);

        final Resource r = request.getResource();
        List<Resource> loc = getLocations(r.getResourceType(),
                r.getResourceSuperType());
        
        List<Resource> expected = Arrays.asList(
        		r("/" + resourceTypePath), // /foo/bar
                r("/" + DEFAULT_RESOURCE_TYPE)); // /sling/servlet/default
        assertThat(loc,isSameResourceList(expected));
    }
    
    @Test
    public void testSearchPath1Element() {
        String root0 = "/apps/";
        searchPathOptions.setSearchPaths(new String[] {
                root0
        });

        final Resource r = request.getResource();
        List<Resource> loc = getLocations(r.getResourceType(),
                r.getResourceSuperType());
        
        List<Resource> expected = Arrays.asList(
        		r(root0 + resourceTypePath), // /apps/foo/bar
                r(root0 + DEFAULT_RESOURCE_TYPE)); // /apps/sling/servlet/default
        assertThat(loc,isSameResourceList(expected));
    }
    
    @Test
    public void testSearchPath2Elements() {
        String root0 = "/apps/";
        String root1 = "/libs/";
        searchPathOptions.setSearchPaths(new String[] {
                root0,
                root1
        });

        final Resource r = request.getResource();
        List<Resource> loc = getLocations(r.getResourceType(),
                r.getResourceSuperType());
        
        List<Resource> expected = Arrays.asList(
        		r(root0 + resourceTypePath), // /apps/foo/bar
                r(root1 + resourceTypePath), // /libs/foo/bar
                r(root0 + DEFAULT_RESOURCE_TYPE), // /apps/sling/servlet/default
                r(root1 + DEFAULT_RESOURCE_TYPE)); // /libs/sling/servlet/default
        assertThat(loc,isSameResourceList(expected));
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
        Resource r = addOrReplaceResource(resolver, resource.getPath(), props);
        request.setResource(r);
    }

    @Test
    public void testSearchPathEmptyAbsoluteType() {
        // expect path gets { "/" }
        searchPathOptions.setSearchPaths(null);

        // absolute resource type
        resourceType = "/foo/bar";
        resourceTypePath = ResourceUtil.resourceTypeToPath(resourceType);
        replaceResource(resourceType, null);

        final Resource r = request.getResource();
        List<Resource> loc = getLocations(r.getResourceType(),
                r.getResourceSuperType());
        
        List<Resource> expected = Arrays.asList(
        		r(resourceTypePath), // /foo/bar
        		r("/" + DEFAULT_RESOURCE_TYPE)); // /sling/servlet/default
        assertThat(loc,isSameResourceList(expected));
    }
    
    @Test
    public void testSearchPath1ElementAbsoluteType() {
        String root0 = "/apps/";
        searchPathOptions.setSearchPaths(new String[] {
                root0
        });

        // absolute resource type
        resourceType = "/foo/bar";
        resourceTypePath = ResourceUtil.resourceTypeToPath(resourceType);
        replaceResource(resourceType, null);

        final Resource r = request.getResource();
        List<Resource> loc = getLocations(r.getResourceType(),
                r.getResourceSuperType());
        
        
        List<Resource> expected = Arrays.asList(
        		r(resourceTypePath), // /foo/bar
                r(root0 + DEFAULT_RESOURCE_TYPE)); // /apps/sling/servlet/default
        assertThat(loc,isSameResourceList(expected));
    }
    
    @Test
    public void testSearchPath2ElementsAbsoluteType() {
        String root0 = "/apps/";
        String root1 = "/libs/";
        searchPathOptions.setSearchPaths(new String[] {
                root0,
                root1
        });

        // absolute resource type
        resourceType = "/foo/bar";
        resourceTypePath = ResourceUtil.resourceTypeToPath(resourceType);
        replaceResource(resourceType, null);

        final Resource r = request.getResource();
        List<Resource> loc = getLocations(r.getResourceType(),
                r.getResourceSuperType());
        
        List<Resource> expected = Arrays.asList(
        		r(resourceTypePath), // /foo/bar
                r(root0 + DEFAULT_RESOURCE_TYPE), // /apps/sling/servlet/default
                r(root1 + DEFAULT_RESOURCE_TYPE)); // /libs/sling/servlet/default
        assertThat(loc,isSameResourceList(expected));
    }
    
    @Test
    public void testSearchPathEmptyWithSuper() {
        // expect path gets { "/" }
        searchPathOptions.setSearchPaths(null);

        // set resource super type
        resourceSuperType = "foo:superBar";
        resourceSuperTypePath = ResourceUtil.resourceTypeToPath(resourceSuperType);
        replaceResource(null, resourceSuperType);

        final Resource r = request.getResource();
        List<Resource> loc = getLocations(r.getResourceType(),
                r.getResourceSuperType());
        
        List<Resource> expected = Arrays.asList(
        		r("/" + resourceTypePath), // /foo/bar
                r("/" + resourceSuperTypePath), // /foo/superBar
                r("/" + DEFAULT_RESOURCE_TYPE)); // /sling/servlet/default
        assertThat(loc,isSameResourceList(expected));
    }
    
    @Test
    public void testSearchPath1ElementWithSuper() {
        String root0 = "/apps/";
        searchPathOptions.setSearchPaths(new String[] {
                root0
        });

        // set resource super type
        resourceSuperType = "foo:superBar";
        resourceSuperTypePath = ResourceUtil.resourceTypeToPath(resourceSuperType);
        replaceResource(null, resourceSuperType);

        final Resource r = request.getResource();
        List<Resource> loc = getLocations(r.getResourceType(),
                r.getResourceSuperType());
        
        List<Resource> expected = Arrays.asList(
        		r(root0 + resourceTypePath), // /apps/foo/bar
                r(root0 + resourceSuperTypePath), // /apps/foo/superBar
                r(root0 + DEFAULT_RESOURCE_TYPE)); // /apps/sling/servlet/default
        assertThat(loc,isSameResourceList(expected));
    }
    
    @Test
    public void testSearchPath2ElementsWithSuper() {
        String root0 = "/apps/";
        String root1 = "/libs/";
        searchPathOptions.setSearchPaths(new String[] {
                root0,
                root1
        });

        // set resource super type
        resourceSuperType = "foo:superBar";
        resourceSuperTypePath = ResourceUtil.resourceTypeToPath(resourceSuperType);
        replaceResource(null, resourceSuperType);

        final Resource r = request.getResource();
        List<Resource> loc = getLocations(r.getResourceType(),
                r.getResourceSuperType());
        
        List<Resource> expected = Arrays.asList(
        		r(root0 + resourceTypePath), // /apps/foo/bar
                r(root1 + resourceTypePath), // /libs/foo/bar
                r(root0 + resourceSuperTypePath), // /apps/foo/superBar
                r(root1 + resourceSuperTypePath), // /libs/foo/superBar
                r(root0 + DEFAULT_RESOURCE_TYPE), // /apps/sling/servlet/default
                r(root1 + DEFAULT_RESOURCE_TYPE)); // /libs/sling/servlet/default
        assertThat(loc,isSameResourceList(expected));
    }
    
    @Test
    public void testSearchPathEmptyAbsoluteTypeWithSuper() {
        // expect path gets { "/" }
        searchPathOptions.setSearchPaths(null);

        // absolute resource type
        resourceType = "/foo/bar";
        resourceTypePath = ResourceUtil.resourceTypeToPath(resourceType);

        // set resource super type
        resourceSuperType = "foo:superBar";
        resourceSuperTypePath = ResourceUtil.resourceTypeToPath(resourceSuperType);
        replaceResource(resourceType, resourceSuperType);

        final Resource r = request.getResource();
        List<Resource> loc = getLocations(r.getResourceType(),
                r.getResourceSuperType());
        
        List<Resource> expected = Arrays.asList(
        		r(resourceTypePath), // /foo/bar
                r("/" + resourceSuperTypePath), // /foo/superBar
                r("/" + DEFAULT_RESOURCE_TYPE)); // /sling/servlet/default
        assertThat(loc,isSameResourceList(expected));
    }
    
    @Test
    public void testSearchPath1ElementAbsoluteTypeWithSuper() {
        String root0 = "/apps/";
        searchPathOptions.setSearchPaths(new String[] {
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
        List<Resource> loc = getLocations(r.getResourceType(),
                r.getResourceSuperType());
        
        List<Resource> expected = Arrays.asList(
        		r(resourceTypePath), // /foo/bar
                r(root0 + resourceSuperTypePath), // /apps/foo/superBar
                r(root0 + DEFAULT_RESOURCE_TYPE)); // /apps/sling/servlet/default
        assertThat(loc,isSameResourceList(expected));
    }
    
    @Test
    public void testSearchPath2ElementsAbsoluteTypeWithSuper() {
        String root0 = "/apps/";
        String root1 = "/libs/";
        searchPathOptions.setSearchPaths(new String[] {
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
        List<Resource> loc = getLocations(r.getResourceType(),
                r.getResourceSuperType());
        
        List<Resource> expected = Arrays.asList(
        		r(resourceTypePath), // /foo/bar
                r(root0 + resourceSuperTypePath), // /apps/foo/superBar
                r(root1 + resourceSuperTypePath), // /libs/foo/superBar
                r(root0 + DEFAULT_RESOURCE_TYPE), // /apps/sling/servlet/default
                r(root1 + DEFAULT_RESOURCE_TYPE)); // /libs/sling/servlet/default
        assertThat(loc,isSameResourceList(expected));
    }
    
    @Test
    public void testScriptNameWithoutResourceType() {
        String root0 = "/apps/";
        String root1 = "/libs/";
        searchPathOptions.setSearchPaths(new String[] {
                root0,
                root1
        });
        List<Resource> loc = getLocations("",
                null,"");
        
        List<Resource> expected = Arrays.asList(
        		r("/apps"),
        		r("/libs"));
        assertThat(loc,isSameResourceList(expected));
    }
    
    @Test
    public void testScriptNameWithResourceType() {
        String root0 = "/apps/";
        String root1 = "/libs/";
        searchPathOptions.setSearchPaths(new String[] {
                root0,
                root1
        });
        List<Resource> loc = getLocations("a/b", null);
        
        List<Resource> expected = Arrays.asList(
        		r(root0 + "a/b"),
                r(root1 + "a/b"),
                r(root0 + DEFAULT_RESOURCE_TYPE),
                r(root1 + DEFAULT_RESOURCE_TYPE));
        assertThat(loc,isSameResourceList(expected));
    }
    
    @Test
    public void testScriptNameWithResourceTypeAndSuperType() {
        String root0 = "/apps/";
        String root1 = "/libs/";
        searchPathOptions.setSearchPaths(new String[] {
                root0,
                root1
        });
        
        List<Resource> loc = getLocations("a/b", "c/d");

        List<Resource> expected = Arrays.asList(
        		r(root0 + "a/b"),
                r(root1 + "a/b"),
                r(root0 + "c/d"),
                r(root1 + "c/d"),
                r(root0 + DEFAULT_RESOURCE_TYPE),
                r(root1 + DEFAULT_RESOURCE_TYPE));
        assertThat(loc,isSameResourceList(expected));
    }
    
    @Test
    public void testCircularResourceTypeHierarchy() throws PersistenceException {
        final String root1 = "/libs/";
        searchPathOptions.setSearchPaths(new String[] {
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
        resolver.create(getOrCreateParentResource(resolver, resource2Path),
                    ResourceUtil.getName(resource2Path),
                    resource2Props);

        String resource3Path = root1 + resourceSuperType2;
        Map<String, Object> resource3Props = new HashMap<>();
        resource3Props.put(ResourceResolver.PROPERTY_RESOURCE_TYPE, resourceType);
		resource3Props.put("sling:resourceSuperType", resourceType);
		resolver.create(getOrCreateParentResource(resolver, resource3Path), ResourceUtil.getName(resource3Path),
				resource3Props);

		List<Resource> loc = getLocations(resourceType, resourceSuperType);
        
        List<Resource> expected = Arrays.asList(
        		r(root1 + resourceType), // /libs/foo/bar
                r(root1 + resourceSuperType), // /libs/foo/check1
                r(root1 + resourceSuperType2), // /libs/foo/check2
                r(root1 + DEFAULT_RESOURCE_TYPE)); // /libs/sling/servlet/default
        assertThat(loc,isSameResourceList(expected));
    }
    
    @Test
    public void testResolveDefaultResourceType() {
    	
    	searchPathOptions.setSearchPaths(new String[] {
                "/apps/",
                "/libs/"
        });
    	
    	List<Resource> loc = getLocations(DEFAULT_RESOURCE_TYPE, resourceSuperType);
    	
    	List<Resource> expected = Arrays.asList(
    			r("/apps/sling/servlet/default"),
    			r("/libs/sling/servlet/default"),
    			r("/apps/sling/servlet/default"),
    			r("/libs/sling/servlet/default"));
    	assertThat(loc,isSameResourceList(expected));
    }
 
    @Test
    public void testAbsoluteResourceSuperType() throws Exception {
        final String root = "/apps/";
        searchPathOptions.setSearchPaths(new String[] {
                root
        });
        
        String resourceType="a/b";
        String resourceTypePath= root + resourceType;
        
        String resourceSuperType= "/apps/c/d";
        String resourceSuperTypePath = resourceSuperType;
        
        Map<String, Object> resourceTypeProps = new HashMap<>();
        resourceTypeProps.put(ResourceResolver.PROPERTY_RESOURCE_TYPE, resourceType);
        resourceTypeProps.put("sling:resourceSuperType", resourceSuperType);
        
        resolver.create(getOrCreateParentResource(resolver, resourceTypePath),
				ResourceUtil.getName(resourceTypePath), resourceTypeProps);
        resolver.create(getOrCreateParentResource(resolver, resourceSuperTypePath),
				ResourceUtil.getName(resourceSuperTypePath), null);
        
        
    	List<Resource> loc = getLocations(resourceType, resourceSuperType);
    	
    	List<Resource> expected = Arrays.asList(
    			r(resourceTypePath),      			// /apps/a/b
    			r(resourceSuperTypePath), 			// /apps/c/d
    			r(root + DEFAULT_RESOURCE_TYPE) 	// /apps/sling/servlet/default
    			);
    	assertThat(loc,isSameResourceList(expected));
    }
    
    @Test
    public void testNoSuperType() throws Exception {
        final String root = "/apps/";
        searchPathOptions.setSearchPaths(new String[] {
                root
        });
        
        String resourceType="a/b";
        String resourceTypePath= root + resourceType;
        
        Map<String, Object> resourceTypeProps = new HashMap<>();
        resourceTypeProps.put(ResourceResolver.PROPERTY_RESOURCE_TYPE, resourceType);
        
        resolver.create(getOrCreateParentResource(resolver, resourceTypePath),
				ResourceUtil.getName(resourceTypePath), resourceTypeProps);
        
        
    	List<Resource> loc = getLocations(resourceType, resourceSuperType);
    	
    	List<Resource> expected = Arrays.asList(
    			r(resourceTypePath),      			// /apps/a/b
    			r(root + DEFAULT_RESOURCE_TYPE) 	// /apps/sling/servlet/default
    			);
    	assertThat(loc,isSameResourceList(expected));
    }
    

    @Test
    public void checkThatTheCacheIsUsed() {

    	// The basic test setup is copied from testSearchPath2ElementsWithSuper
        String root0 = "/apps/";
        String root1 = "/libs/";
        searchPathOptions.setSearchPaths(new String[] {
                root0,
                root1
        });

        // set resource super type
        resourceSuperType = "foo:superBar";
        resourceSuperTypePath = ResourceUtil.resourceTypeToPath(resourceSuperType);
        replaceResource(null, resourceSuperType);

        final Resource r = request.getResource();
        
    	// Execute the same call twice and expect that on 2nd time the ResourceResolver
    	// is never used, because all is taken from the cache
        getLocations(r.getResourceType(),
                r.getResourceSuperType());
        
        Mockito.clearInvocations(resolver);
        
        getLocations(r.getResourceType(),
                r.getResourceSuperType());
    	
        Mockito.verify(resolver, Mockito.never()).getResource(Mockito.anyString());
    }
    
    
    // --- helper ---
    
    private Resource r (String path) {
    	return new SyntheticResource(resolver, path, "resourcetype");
    }
    
    
    List<Resource> getLocations(final String resourceType,
            final String resourceSuperType) {
        return getLocations(resourceType, resourceSuperType, DEFAULT_RESOURCE_TYPE);
    }
    
    List<Resource> getLocations( final String resourceType,
            final String resourceSuperType,
            final String baseResourceType) {
    	
        return LocationCollector.getLocations(resourceType,
                resourceSuperType,
                baseResourceType,
                resolver);
    }
    
    // Mimic the searchpath semantic of the ResourceResolverFactory
    public class SearchPathOptions {
    	
    	String[] searchPath = new String[0];
    	
    	public void setSearchPaths(String[] searchpath) {
    		if (searchpath == null) {
    			this.searchPath = new String[0];
    		} else {
    			this.searchPath = searchpath;
    		}
    	}
    	
    	public String[] getSearchPath() {
    		return searchPath;
    	}
    }
    
    
    
}
