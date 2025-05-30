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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.servlets.resolver.internal.SlingServletResolver;
import org.apache.sling.servlets.resolver.internal.resource.ServletResourceProviderFactory;

/**
 * The <code>ResourceCollector</code> class provides a single public method -
 * {@link #getServlets(ResourceResolver)} - which is used to find an ordered
 * collection of <code>Resource</code> instances which may be used to find a
 * servlet or script to handle a request to the given resource.
 */
public class ResourceCollector extends AbstractResourceCollector {

    /**
     * The special value returned by
     * {@link #calculatePrefixMethodWeight(Resource, String, boolean)} if the
     * resource is not suitable to handle the request according to the location
     * prefix, request selectors and request extension (value is
     * <code>Integer.MIN_VALUE</code>).
     */
    protected static final int WEIGHT_NO_MATCH = Integer.MIN_VALUE;

    // the request method name used to indicate the script name
    private final String methodName;

    // the request selectors as a string converted to a realtive path or
    // null if the request has no selectors
    private final String[] requestSelectors;

    // the number of request selectors of the request or 0 if none
    private final int numRequestSelectors;

    // request is GET or HEAD
    private final boolean isGet;

    // request is GET or HEAD and extension is html
    private final boolean isDefaultExtension;

    private final String suffExt;

    private final String suffMethod;

    private final String suffExtMethod;

    /**
     * Creates a <code>ResourceCollector</code> for the given
     * <code>request</code>. If the request is a GET or HEAD request, a
     * specialized instance is returned which also takes the request selectors
     * and request extension into account for finding servlet resources.
     * Otherwise an instance of this class itself is returned which just takes
     * the resource type and request method name into account.
     *
     * @param request The <code>SlingHttpServletRequest</code> for which to
     *            return a <code>ResourceCollector</code>.
     * @return The <code>ResourceCollector</code> to find servlets and scripts
     *         suitable for handling the <code>request</code>.
     */
    public static ResourceCollector create(
            final SlingJakartaHttpServletRequest request,
            final String[] executionPaths,
            final Collection<String> defaultExtensions,
            boolean UseResourceCaching) {
        final RequestPathInfo requestPathInfo = request.getRequestPathInfo();
        final boolean isDefaultExtension =
                requestPathInfo.getExtension() != null && defaultExtensions.contains(requestPathInfo.getExtension());
        return new ResourceCollector(
                request.getResource(),
                requestPathInfo.getExtension(),
                executionPaths,
                isDefaultExtension,
                request.getMethod(),
                requestPathInfo.getSelectors(),
                UseResourceCaching);
    }

    public static ResourceCollector create(
            final Resource resource,
            final String extension,
            final String[] executionPaths,
            final Collection<String> defaultExtensions,
            final String methodName,
            final String[] selectors,
            boolean useResourceCaching) {
        final boolean isDefaultExtension = extension != null && defaultExtensions.contains(extension);
        return new ResourceCollector(
                resource, extension, executionPaths, isDefaultExtension, methodName, selectors, useResourceCaching);
    }

    /**
     * Creates a <code>ResourceCollector</code> finding servlets and scripts for
     * the given <code>methodName</code>.
     *
     * @param methodName The <code>methodName</code> used to find scripts for.
     *            This must not be <code>null</code>.
     * @param baseResourceType The basic resource type to use as a final
     *            resource super type. If this is <code>null</code> the default
     *            value
     *            {@link org.apache.sling.servlets.resolver.internal.ServletResolverConstants#DEFAULT_SERVLET_NAME}
     *            is assumed.
     * @param resource the resource to invoke, the resource type and resource
     *            super type are taken from this resource.
     * @param executionPaths the execution paths to consider
     * @deprecated use {@link #ResourceCollector(String, String, Resource, String, String[])} instead.
     */
    @Deprecated
    public ResourceCollector(
            final String methodName,
            final String baseResourceType,
            final Resource resource,
            final String[] executionPaths) {
        this(methodName, baseResourceType, resource, null, executionPaths, false);
    }

    /**
     * Creates a <code>ResourceCollector</code> finding servlets and scripts for
     * the given <code>methodName</code>.
     *
     * @param methodName The <code>methodName</code> used to find scripts for.
     *            This must not be <code>null</code>.
     * @param baseResourceType The basic resource type to use as a final
     *            resource super type. If this is <code>null</code> the default
     *            value
     *            {@link org.apache.sling.servlets.resolver.internal.ServletResolverConstants#DEFAULT_SERVLET_NAME}
     *            is assumed.
     * @param resource the resource to invoke, the resource type and resource
     *            super type are taken from this resource.
     * @param extension the extension of the request being processed
     * @param executionPaths the execution paths to consider
     */
    public ResourceCollector(
            final String methodName,
            final String baseResourceType,
            final Resource resource,
            final String extension,
            final String[] executionPaths,
            final boolean useResourceCaching) {
        super(
                (baseResourceType != null ? baseResourceType : ServletResolverConstants.DEFAULT_RESOURCE_TYPE),
                resource.getResourceType(),
                resource.getResourceSuperType(),
                extension,
                executionPaths,
                useResourceCaching);
        this.methodName = methodName;
        this.requestSelectors = new String[0];
        this.numRequestSelectors = 0;
        this.isGet = false;
        this.isDefaultExtension = false;

        this.suffExt = "." + extension;
        this.suffMethod = "." + methodName;
        this.suffExtMethod = suffExt + suffMethod;

        // create the hash code once
        final String key = methodName + ':' + baseResourceType + ':'
                + extension + "::"
                + (this.resourceType == null ? "" : this.resourceType) + ':'
                + (this.resourceSuperType == null ? "" : this.resourceSuperType);
        this.hashCode = key.hashCode();
    }

    /**
     * Creates a <code>ResourceCollector</code> finding servlets and scripts for
     * the given <code>resource</code>.
     *
     * @param methodName The <code>methodName</code> used to find scripts for.
     *            This must not be <code>null</code>.
     * @param baseResourceType The basic resource type to use as a final
     *            resource super type. If this is <code>null</code> the default
     *            value
     *            {@link org.apache.sling.servlets.resolver.internal.ServletResolverConstants#DEFAULT_SERVLET_NAME}
     *            is assumed.
     */
    private ResourceCollector(
            final Resource resource,
            final String extension,
            final String[] executionPaths,
            final boolean isDefaultExtension,
            final String methodName,
            final String[] selectors,
            final boolean useResourceCaching) {
        super(
                ServletResolverConstants.DEFAULT_RESOURCE_TYPE,
                resource.getResourceType(),
                resource.getResourceSuperType(),
                extension,
                executionPaths,
                useResourceCaching);
        this.methodName = methodName;

        this.suffExt = "." + extension;
        this.suffMethod = "." + methodName;
        this.suffExtMethod = suffExt + suffMethod;

        this.requestSelectors = selectors;
        this.numRequestSelectors = requestSelectors.length;

        this.isGet = "GET".equals(methodName) || "HEAD".equals(methodName);
        this.isDefaultExtension = isDefaultExtension;

        // create the hash code once
        final String key = methodName
                + ':'
                + baseResourceType
                + ':'
                + extension
                + ':'
                + String.join(".", requestSelectors)
                + ':'
                + (this.resourceType == null ? "" : this.resourceType)
                + ':'
                + (this.resourceSuperType == null ? "" : this.resourceSuperType);
        this.hashCode = key.hashCode();
    }

    @Override
    protected void getWeightedResources(final Set<WeightedResource> resources, final Resource location) {

        final ResourceResolver resolver = location.getResourceResolver();
        Resource current = location;
        String parentName = current.getName();

        int selIdx = 0;
        String selector;
        do {
            selector = (selIdx < numRequestSelectors) ? requestSelectors[selIdx] : null;

            List<Resource> children = getChildrenList(current, isDefaultExtension);
            for (Resource child : children) {

                if (!SlingServletResolver.isPathAllowed(child.getPath(), this.executionPaths)) {
                    continue;
                }
                String scriptName = child.getName();
                int lastDot = scriptName.lastIndexOf('.');
                if (lastDot < 0) {
                    // no extension in the name, this is not a script
                    continue;
                }

                scriptName = scriptName.substring(0, lastDot);

                if (isGet
                        && checkScriptName(scriptName, selector, parentName, suffExt, null, resources, child, selIdx)) {
                    continue;
                }

                if (checkScriptName(
                        scriptName, selector, parentName, suffExtMethod, suffMethod, resources, child, selIdx)) {
                    continue;
                }

                // SLING-754: Not technically really correct because
                // the request extension is only optional in the script
                // name for HTML methods, but we keep this for backwards
                // compatibility.
                if (selector != null && matches(scriptName, selector, suffMethod)) {
                    addWeightedResource(resources, child, selIdx + 1, WeightedResource.WEIGHT_NONE);
                    continue;
                }

                if (scriptName.equals(methodName)) {
                    addWeightedResource(resources, child, selIdx, WeightedResource.WEIGHT_NONE);
                }
            }

            if (selector != null) {
                current = resolver.getResource(current, selector);
                parentName = selector;
                selIdx++;
            }
        } while (selector != null && current != null);

        // special treatment for servlets registered with neither a method
        // name nor extensions and selectors
        addLocationServlet(resources, location);
    }

    /**
     * Checks whether the <code>scriptName</code> matches a certain number of
     * combinations of <code>selector</code>, <code>parentName</code>,
     * <code>suffix</code> and <code>htmlSuffix</code>. If a match is found the
     * {@link #addWeightedResource(Set, Resource, int, int)} method is called to
     * register the found script resource with appropriate selection weight.
     *
     * @param scriptName The name of the script (without the script extension)
     *            to check for compliance.
     * @param selector The current selector to check for in the script name; may
     *            be <code>null</code>.
     * @param parentName The name of the parent folder; must not be
     *            <code>null</code>.
     * @param suffix Expected second part of the script name (besides either the
     *            selector or the parent name); must not be <code>null</code>;
     *            applicable for any request method.
     * @param htmlSuffix Expected second part of the script name (besides either
     *            the selector or the parent name); may be <code>null</code>;
     *            applicable for GET or HEAD methods only.
     * @param resources The set of weighted resource to which the new weighted
     *            resource is added if a match is found.
     * @param child The resource representing the script
     * @param selIdx The selector weight value
     * @return <code>true</code> if a match has been found and a weighted
     *         resource has been added to the <code>resources</code> set.
     */
    private boolean checkScriptName(
            final String scriptName,
            final String selector,
            final String parentName,
            final String suffix,
            final String htmlSuffix,
            final Set<WeightedResource> resources,
            final Resource child,
            final int selIdx) {
        if (selector != null && matches(scriptName, selector, suffix)) {
            addWeightedResource(resources, child, selIdx + 1, WeightedResource.WEIGHT_EXTENSION);
            return true;
        }

        if (matches(scriptName, parentName, suffix)) {
            addWeightedResource(
                    resources,
                    child,
                    selIdx,
                    WeightedResource.WEIGHT_EXTENSION
                            + WeightedResource.WEIGHT_PREFIX
                            + ((htmlSuffix != null) ? WeightedResource.WEIGHT_METHOD : WeightedResource.WEIGHT_NONE));
            return true;
        }

        if (suffix != null && !suffix.isEmpty() && scriptName.equals(suffix.substring(1))) {
            addWeightedResource(
                    resources,
                    child,
                    selIdx,
                    WeightedResource.WEIGHT_EXTENSION
                            + ((htmlSuffix != null) ? WeightedResource.WEIGHT_METHOD : WeightedResource.WEIGHT_NONE));
            return true;
        }

        if (isDefaultExtension) {
            if (selector != null && matches(scriptName, selector, htmlSuffix)) {
                addWeightedResource(resources, child, selIdx + 1, WeightedResource.WEIGHT_NONE);
                return true;
            }

            if (matches(scriptName, parentName, htmlSuffix)) {
                addWeightedResource(resources, child, selIdx, WeightedResource.WEIGHT_PREFIX);
                return true;
            }
        }
        return false;
    }

    private boolean matches(final String scriptName, final String name, String suffix) {
        if (suffix == null) {
            return scriptName.equals(name);
        }
        final int lenScriptName = scriptName.length();
        final int lenName = name.length();
        final int lenSuffix = suffix.length();
        return scriptName.regionMatches(0, name, 0, lenName)
                && scriptName.regionMatches(lenName, suffix, 0, lenSuffix)
                && lenScriptName == (lenName + lenSuffix);
    }

    private void addLocationServlet(final Set<WeightedResource> resources, final Resource location) {
        final String path = location.getPath() + ServletResourceProviderFactory.SERVLET_PATH_EXTENSION;
        if (SlingServletResolver.isPathAllowed(path, this.executionPaths)) {
            final Resource servlet = location.getResourceResolver().getResource(path);
            if (servlet != null) {
                addWeightedResource(resources, servlet, 0, WeightedResource.WEIGHT_LAST_RESSORT);
            }
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + (isDefaultExtension ? 1231 : 1237);
        result = prime * result + (isGet ? 1231 : 1237);
        result = prime * result + ((methodName == null) ? 0 : methodName.hashCode());
        result = prime * result + numRequestSelectors;
        result = prime * result + Arrays.hashCode(requestSelectors);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        ResourceCollector other = (ResourceCollector) obj;
        if (isDefaultExtension != other.isDefaultExtension) return false;
        if (isGet != other.isGet) return false;
        if (methodName == null) {
            if (other.methodName != null) return false;
        } else if (!methodName.equals(other.methodName)) return false;
        if (numRequestSelectors != other.numRequestSelectors) return false;
        return Arrays.equals(requestSelectors, other.requestSelectors);
    }
}
