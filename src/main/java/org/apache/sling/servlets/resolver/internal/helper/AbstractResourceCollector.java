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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.SyntheticResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <code>ResourceCollector</code> class provides a single public method -
 * {@link #getServlets(ResourceResolver,List<String>)} - which is used to find an ordered collection
 * of <code>Resource</code> instances which may be used to find a servlet or
 * script to handle a request to the given resource.
 */
public abstract class AbstractResourceCollector {
    
    
    
    private static final Logger LOG = LoggerFactory.getLogger(AbstractResourceCollector.class);
    
    
    protected final static String CACHE_KEY_CHILDREN_LIST = AbstractResourceCollector.class.getName() + ".childrenList";

    // the most generic resource type to use. This may be null in which
    // case the default servlet name will be used as the base name
    protected final String baseResourceType;

    // the request extension or null if the request has no extension
    protected final String extension;

    protected int hashCode;

    protected final String resourceType;

    protected final String resourceSuperType;

    protected final String[] executionPaths;
    
    protected boolean useResourceCaching;

    protected AbstractResourceCollector(final String baseResourceType,
            final String resourceType,
            final String resourceSuperType,
            final String extension,
            final String[] executionPaths,
            final boolean useResourceCaching) {
        this.baseResourceType = baseResourceType;
        this.resourceType = resourceType;
        this.resourceSuperType = resourceSuperType;
        this.extension = extension;
        this.executionPaths = executionPaths;
        this.useResourceCaching = useResourceCaching;
    }

    public final Collection<Resource> getServlets(final ResourceResolver resolver, final List<String> scriptExtensions) {

        final SortedSet<WeightedResource> resources = new TreeSet<>((o1, o2) -> {
            String o1Parent = ResourceUtil.getParent(o1.getPath());
            String o2Parent = ResourceUtil.getParent(o2.getPath());
            if (o1Parent != null && o2Parent != null && o1Parent.equals(o2Parent)) {
                String o1ScriptName = o1.getName();
                String o2ScriptName = o2.getName();
                String o1Extension = getScriptExtension(o1ScriptName);
                String o2Extension = getScriptExtension(o2ScriptName);
                if (StringUtils.isNotEmpty(o1Extension) && StringUtils.isNotEmpty(o2Extension)) {
                    String o1ScriptWithoutExtension = o1ScriptName.substring(0, o1ScriptName.lastIndexOf("." + o1Extension));
                    String o2ScriptWithoutExtension = o2ScriptName.substring(0, o2ScriptName.lastIndexOf("." + o2Extension));
                    if (o1ScriptWithoutExtension.equals(o2ScriptWithoutExtension)) {
                        int o1ExtensionIndex = scriptExtensions.indexOf(o1Extension);
                        int o2ExtensionIndex = scriptExtensions.indexOf(o2Extension);
                        if (o1ExtensionIndex == o2ExtensionIndex || o1ExtensionIndex == -1 || o2ExtensionIndex == -1) {
                            return o1.compareTo(o2);
                        } else if (o1ExtensionIndex > o2ExtensionIndex) {
                            return -1;
                        } else {
                            return 1;
                        }
                    }
                }
            }
            return o1.compareTo(o2);
        });
        
        
        List<Resource> locations = LocationCollector.getLocations(resourceType, resourceSuperType, 
        		baseResourceType, resolver, this.useResourceCaching);
        locations.forEach(locationRes -> getWeightedResources(resources, locationRes));

        List<Resource> result = new ArrayList<>(resources.size());
        result.addAll(resources);
        return result;
    }

    protected abstract void getWeightedResources(final Set<WeightedResource> resources,
                                                 final Resource location);

    /**
     * Creates a {@link WeightedResource} and adds it to the set of resources.
     * The number of resources already present in the set is used as the ordinal
     * number for the newly created resource.
     *
     * @param resources The set of resource to which the
     *            {@link WeightedResource} is added.
     * @param resource The <code>Resource</code> on which the
     *            {@link WeightedResource} is based.
     * @param numSelectors The number of request selectors which are matched by
     *            the name of the resource.
     * @param methodPrefixWeight The method/prefix weight assigned to the
     *            resource according to the resource name.
     */
    protected final void addWeightedResource(final Set<WeightedResource> resources,
            final Resource resource,
            final int numSelectors,
            final int methodPrefixWeight) {
        final WeightedResource lr = new WeightedResource(resources.size(), resource,
            numSelectors, methodPrefixWeight);
        resources.add(lr);
    }

    /**
     * Returns a resource for the given <code>path</code>.
     * If no resource exists at the given path a
     * <code>SyntheticResource</code> is returned.
     *
     * @param resolver The <code>ResourceResolver</code> used to access the
     *            resource.
     * @param path The absolute path of the resource to return.
     * @return The actual resource at the given <code>path</code> or a
     *         synthetic resource representing the path location.
     */
    protected final Resource getResource(final ResourceResolver resolver,
                                         String path) {
        Resource res = resolver.getResource(path);

        if (res == null) {
            res = new SyntheticResource(resolver, path, "$synthetic$");
        }

        return res;
    }

    @Override
    public boolean equals(Object obj) {
        if ( !(obj instanceof AbstractResourceCollector) ) {
            return false;
        }
        if ( obj == this ) {
            return true;
        }
        final AbstractResourceCollector o = (AbstractResourceCollector)obj;
        return stringEquals(resourceType, o.resourceType)
             && stringEquals(resourceSuperType, o.resourceSuperType)
             && stringEquals(extension, o.extension)
             && stringEquals(baseResourceType, o.baseResourceType);
    }

    @Override
    public int hashCode() {
        return this.hashCode;
    }

    /**
     * Helper method to compare two strings which can possibly be <code>null</code>
     */
    protected boolean stringEquals(final String s1, final String s2) {
        if ( s1 == null && s2 == null ) {
            return true;
        }
        if ( s1 == null || s2 == null ) {
            return false;
        }
        return s1.equals(s2);
    }

    private String getScriptExtension(String scriptName) {
        int lastIndexOf = scriptName.lastIndexOf('.');
        if (lastIndexOf > -1 && lastIndexOf < scriptName.length() - 1) {
            return scriptName.substring(lastIndexOf + 1);
        }
        return null;
    }
    
    /**
     * Retrieves the list of children for a resource
     * @param parent the resource for which the children should be retrieved
     * @param useCaching if true try to read the list from the cache
     * @return the children (or an empty list of no children are present)
     */
    static List<Resource> getChildrenList(Resource parent, boolean useCaching) {
        
        List<Resource> childList = new ArrayList<Resource>();
        if (useCaching) {
            
            // init the caching structure
            Map<String,List<Resource>> childrenListMap = new HashMap<String,List<Resource>>();
            Map<String,Object> cache = parent.getResourceResolver().getPropertyMap();
            if (!cache.containsKey(CACHE_KEY_CHILDREN_LIST)) {
                childrenListMap = new HashMap<String,List<Resource>>();
                cache.put(CACHE_KEY_CHILDREN_LIST, childrenListMap);
               
            } else {
                Object entry = cache.get(CACHE_KEY_CHILDREN_LIST);
                if (entry instanceof HashMap) {
                    childrenListMap = (Map<String,List<Resource>>) cache.get(CACHE_KEY_CHILDREN_LIST); 
                } else {
                    // unexpected type
                    LOG.debug("Found key '{}' used with the unexpected type '{}', not caching the resource children list", 
                            CACHE_KEY_CHILDREN_LIST, entry.getClass().getName());
                }
            }
            
            // lookup
            if (childrenListMap.containsKey(parent.getPath())) {
                // this is a cache hit
                List<Resource> result = childrenListMap.get(parent.getPath());
                LOG.trace("getChildrenList cache-hit for {} with {} child resources", parent.getPath(), result.size());
                return result;
            }
            // it's a cache miss
            childrenListMap.put(parent.getPath(),childList);
        }
        Iterator<Resource> childrenIterator = parent.listChildren();
        while (childrenIterator.hasNext()) {
            childList.add(childrenIterator.next());
        }
        LOG.trace("getChildrenList cache-miss for {} with {} child resources", parent.getPath(),childList.size());
        return childList;   
    }

}
