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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

/**
 * The <code>LocationCollector</code> provides access to an ordered collection
 * of absolute paths containing potential request handling. The primary order of
 * the collection is the resource type hierarchy with the base resource type at
 * the top. The secondary order is the search path retrieved from the resource
 * resolver.
 * <p>
 * Example: For a node type hierarchy "sample" > "super" > "default" and a
 * search path of [ "/apps", "/libs" ], the iterator would provide access to the
 * following list of paths:
 * <ol>
 * <li><code>/apps/sample</code></li>
 * <li><code>/libs/sample</code></li>
 * <li><code>/apps/super</code></li>
 * <li><code>/libs/super</code></li>
 * <li><code>/apps/default</code></li>
 * <li><code>/libs/default</code></li>
 * </ol>
 */

public class LocationCollector {

    public static @NotNull List<String> getLocations(String resourceType, String resourceSuperType, String baseResourceType,
                                                     ResourceResolver resolver) {
        LocationCollector collector = new LocationCollector(resourceType, resourceSuperType, baseResourceType, resolver);
        return collector.getResolvedLocations();
    }

    // The search path of the resource resolver
    private final String[] searchPath;

    private final ResourceResolver resolver;
    private final String baseResourceType;
    private final String resourceType;
    private final String resourceSuperType;

    /** Set of used resource types to detect a circular resource type hierarchy. */
    private final Set<String> usedResourceTypes = new HashSet<>();
    
    private final List<String> result = new ArrayList<>();

    private LocationCollector(String resourceType, String resourceSuperType, String baseResourceType,
                              ResourceResolver resolver) {

        this.resourceType = resourceType;
        this.resourceSuperType = resourceSuperType;
        this.baseResourceType = baseResourceType;
        this.resolver = resolver;

        String[] tmpPath = resolver.getSearchPath();
        if (tmpPath.length == 0) {
            tmpPath = new String[] { "/" };
        }
        searchPath = tmpPath;
        this.usedResourceTypes.add(this.resourceType);
        collectPaths();
    }

    private @NotNull List<String> getResolvedLocations() {
        return result;
    }

    /**
     * Collect all resource types
     */
    private void collectPaths() {
        
        String rt = this.resourceType;
        do {
            String superType = handleResourceType(rt);
            rt = superType;
        } while (rt != null);
        
        // add default resourceTypes
        final String defaultResourceTypeSuffix;
        boolean blankResourceType = StringUtils.isBlank(resourceType);
        if (blankResourceType) {
            defaultResourceTypeSuffix = "";
        } else {
            defaultResourceTypeSuffix = this.baseResourceType;
        }
        for (String spath : searchPath) {
            result.add(spath + defaultResourceTypeSuffix);
        }
    }
    
    /**
     * Add all necessary path entries to the result list, and return the resourceSuperType
     * for the given resourceType
     * @param resourceType the resourceType
     * @return the resourceSuperType or null if the given resourceType does not have a resourceSuperType
     */
    @Nullable String handleResourceType (@NotNull String resourceType) {
        boolean isBlank = StringUtils.isBlank(resourceType);
        boolean isAbsoluteResourceType = resourceType.startsWith("/");
        String rst = null;
        if (!isBlank) {
            if (isAbsoluteResourceType) {
                result.add(ResourceUtil.resourceTypeToPath(resourceType));
            } else {
                for (String spath : searchPath) {
                    result.add(spath + ResourceUtil.resourceTypeToPath(resourceType));
                }
                
            }
            rst = getResourceSuperType(resourceType);
        }
        return rst;
    }
    

    /**
     * Returns the resource super type of the given resource type:
     * <ol>
     * <li>If the resource type is the base resource type <code>null</code>
     * is returned.</li>
     * <li>If the resource type is the resource type of the resource the
     * resource super type from the resource is returned.</li>
     * <li>Otherwise the resource super type is tried to be found in the
     * resource tree. If one is found, it is returned.</li>
     * <li>Otherwise the base resource type is returned.</li>
     * </ol>
     */
    private @Nullable String getResourceSuperType(@NotNull String resourceType) {

        // if the current resource type is the default value, there are no more
        if (resourceType.equals(baseResourceType)) {
            return null;
        }

        // get the super type of the current resource type
        String superType;
        if (resourceType.equals(this.resourceType)
                && this.resourceSuperType != null ) {
            superType = this.resourceSuperType;
        } else {
            superType = getResourceSuperType(resolver, resourceType);
        }

        // detect circular dependency
        if ( superType != null ) {
            if ( this.usedResourceTypes.contains(superType) ) {
                LoggerFactory.getLogger(this.getClass()).error("Circular dependency in resource type hierarchy detected! Check super types of {}", superType);
                superType = null;
            } else {
                this.usedResourceTypes.add(superType);
            }
        }
        return superType;
    }

    // this method is largely duplicated from ResourceUtil
    private @Nullable String getResourceSuperType(final @NotNull ResourceResolver resourceResolver,
                                                  final @NotNull String resourceType) {
        // normalize resource type to a path string
        final String rtPath = ResourceUtil.resourceTypeToPath(resourceType);
        // get the resource type resource and check its super type
        String rst = null;
        // if the path is absolute, use it directly
        if (rtPath.startsWith("/")) {
            final Resource rtResource = resourceResolver.getResource(rtPath);
            if (rtResource != null) {
                rst = rtResource.getResourceSuperType();
            }
        } else {
            // if the path is relative we use the search paths
            for (final String path : this.searchPath) {
                final String candidatePath = path + rtPath;
                final Resource rtResource = resourceResolver.getResource(candidatePath);
                if (rtResource != null && rtResource.getResourceSuperType() != null) {
                    rst = rtResource.getResourceSuperType();
                    break;
                }
            }
        }
        return rst;
    }
}
