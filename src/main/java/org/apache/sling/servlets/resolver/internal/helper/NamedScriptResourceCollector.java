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

import java.util.Iterator;
import java.util.Set;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.servlets.resolver.internal.SlingServletResolver;

/**
 * The <code>ResourceCollector</code> class provides a single public method -
 * {@link #getServlets(ResourceResolver)} - which is used to find an ordered collection
 * of <code>Resource</code> instances which may be used to find a servlet or
 * script to handle a request to the given resource.
 */
public class NamedScriptResourceCollector extends AbstractResourceCollector {

    private final String scriptName;

    public static NamedScriptResourceCollector create(final String name,
            final Resource resource,
            final String[] executionPaths,
            final boolean useResourceCaching) {
        final String resourceType;
        final String resourceSuperType;
        final String baseResourceType;
        final String extension;
        final String scriptName;
        if ( resource != null ) {
            resourceType = resource.getResourceType();
            resourceSuperType = resource.getResourceSuperType();
            baseResourceType = ServletResolverConstants.DEFAULT_RESOURCE_TYPE;
        } else {
            resourceType = "";
            resourceSuperType = null;
            baseResourceType = "";
        }
        scriptName = name;
        final int pos = name.lastIndexOf('.');
        if ( pos == -1 ) {
            extension = null;
        } else {
            extension = name.substring(pos);
        }
        return new NamedScriptResourceCollector(baseResourceType,
                resourceType,
                resourceSuperType,
                scriptName,
                extension,
                executionPaths,
                useResourceCaching);
    }

    public NamedScriptResourceCollector(final String baseResourceType,
                              final String resourceType,
                              final String resourceSuperType,
                              final String scriptName,
                              final String extension,
                              final String[] executionPaths,
                              final boolean useResourceCaching) {
        super(baseResourceType, resourceType, resourceSuperType, extension, executionPaths, useResourceCaching);
        this.scriptName = scriptName;
        // create the hash code once
        final String key = baseResourceType + ':' + this.scriptName + ':' +
            this.resourceType + ':' + (this.resourceSuperType == null ? "" : this.resourceSuperType) +
            ':' + (this.extension == null ? "" : this.extension);
        this.hashCode = key.hashCode();
    }

    @Override
    protected void getWeightedResources(final Set<WeightedResource> resources,
                                        final Resource location) {
        final ResourceResolver resolver = location.getResourceResolver();
        // if extension is set, we first check for an exact script match
        if ( this.extension != null ) {
            final String path = ResourceUtil.normalize(location.getPath() + '/' + this.scriptName);
            if ( SlingServletResolver.isPathAllowed(path, this.executionPaths) ) {
                final Resource current = resolver.getResource(path);
                if ( current != null ) {
                    this.addWeightedResource(resources, current, 0, WeightedResource.WEIGHT_EXTENSION);
                }
            }
        }
        // if the script name denotes a path we have to get the denoted resource
        // first
        final Resource current;
        final String name;
        final int pos = this.scriptName.lastIndexOf('/');
        if ( pos == -1 ) {
            current = location;
            name = this.scriptName;
        } else {
            current = getResource(resolver, location.getPath() + '/' + this.scriptName.substring(0, pos));
            name = this.scriptName.substring(pos + 1);
        }
        final Iterator<Resource> children = resolver.listChildren(current);
        while (children.hasNext()) {
            final Resource child = children.next();

            if ( SlingServletResolver.isPathAllowed(child.getPath(), this.executionPaths) ) {
                final String currentScriptName = child.getName();
                final int lastDot = currentScriptName.lastIndexOf('.');
                if (lastDot < 0) {
                    // no extension in the name, this is not a script
                    continue;
                }

                if ( currentScriptName.substring(0, lastDot).equals(name) ) {
                    this.addWeightedResource(resources, child, 0, WeightedResource.WEIGHT_PREFIX);
                }
            }
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((scriptName == null) ? 0 : scriptName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        NamedScriptResourceCollector other = (NamedScriptResourceCollector) obj;
        if (scriptName == null) {
            if (other.scriptName != null)
                return false;
        } else if (!scriptName.equals(other.scriptName))
            return false;
        return true;
    }

}
