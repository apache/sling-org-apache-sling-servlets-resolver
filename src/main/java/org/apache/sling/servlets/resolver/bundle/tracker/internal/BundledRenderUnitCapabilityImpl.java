/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 ~ Licensed to the Apache Software Foundation (ASF) under one
 ~ or more contributor license agreements.  See the NOTICE file
 ~ distributed with this work for additional information
 ~ regarding copyright ownership.  The ASF licenses this file
 ~ to you under the Apache License, Version 2.0 (the
 ~ "License"); you may not use this file except in compliance
 ~ with the License.  You may obtain a copy of the License at
 ~
 ~   http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing,
 ~ software distributed under the License is distributed on an
 ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 ~ KIND, either express or implied.  See the License for the
 ~ specific language governing permissions and limitations
 ~ under the License.
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
package org.apache.sling.servlets.resolver.bundle.tracker.internal;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.servlets.resolver.bundle.tracker.BundledRenderUnitCapability;
import org.apache.sling.servlets.resolver.bundle.tracker.ResourceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.Version;
import org.osgi.framework.wiring.BundleCapability;

class BundledRenderUnitCapabilityImpl  implements BundledRenderUnitCapability {

    private final Set<ResourceType> resourceTypes;
    private final String path;
    private final List<String> selectors;
    private final String extension;
    private final String method;
    private final String extendedResourceType;
    private final String scriptEngineName;
    private final String scriptExtension;

    private BundledRenderUnitCapabilityImpl(@NotNull Set<ResourceType> resourceTypes, @Nullable String path,
                                            @NotNull List<String> selectors,
                                        @Nullable String extension, @Nullable String method,
                                        @Nullable String extendedResourceType, @Nullable String scriptEngineName,
                                        @Nullable String scriptExtension) {
        this.resourceTypes = resourceTypes;
        this.path = path;
        this.selectors = selectors;
        this.extension = extension;
        this.method = method;
        this.extendedResourceType = extendedResourceType;
        this.scriptEngineName = scriptEngineName;
        this.scriptExtension = scriptExtension;
    }

    @Override
    @NotNull
    public Set<ResourceType> getResourceTypes() {
        return Collections.unmodifiableSet(resourceTypes);
    }

    @Override
    @Nullable
    public String getPath() {
        return path;
    }

    @Override
    @NotNull
    public List<String> getSelectors() {
        return Collections.unmodifiableList(selectors);
    }

    @Override
    @Nullable
    public String getExtension() {
        return extension;
    }

    @Override
    @Nullable
    public String getExtendedResourceType() {
        return extendedResourceType;
    }

    @Override
    @Nullable
    public String getMethod() {
        return method;
    }

    @Override
    @Nullable
    public String getScriptEngineName() {
        return scriptEngineName;
    }

    @Override
    @Nullable
    public String getScriptExtension() {
        return scriptExtension;
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceTypes, path, selectors, extension, method, extendedResourceType, scriptEngineName, scriptExtension);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof BundledRenderUnitCapability) {
            BundledRenderUnitCapability other = (BundledRenderUnitCapability) obj;
            return Objects.equals(resourceTypes, other.getResourceTypes()) && Objects.equals(path, other.getPath()) &&
                    Objects.equals(selectors, other.getSelectors()) &&
                    Objects.equals(extension, other.getExtension()) && Objects.equals(method, other.getMethod()) &&
                    Objects.equals(extendedResourceType, other.getExtendedResourceType()) &&
                    Objects.equals(scriptEngineName, other.getScriptEngineName()) &&
                    Objects.equals(scriptExtension, other.getScriptExtension());
        }
        return false;
    }

    public static BundledRenderUnitCapability fromBundleCapability(@NotNull BundleCapability capability) {
        Map<String, Object> attributes = capability.getAttributes();
        Set<ResourceType> resourceTypes = new LinkedHashSet<>();
        String[] capabilityResourceTypes =
                PropertiesUtil.toStringArray(attributes.get(ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES), new String[0]);
        Version version = (Version) attributes.get(BundledScriptTracker.AT_VERSION);
        for (String rt : capabilityResourceTypes) {
            if (version == null) {
                resourceTypes.add(ResourceType.parseResourceType(rt));
            } else {
                resourceTypes.add(ResourceType.parseResourceType(rt + "/" + version.toString()));
            }
        }
        return new BundledRenderUnitCapabilityImpl(
                resourceTypes,
                (String) attributes.get(ServletResolverConstants.SLING_SERVLET_PATHS),
                Arrays.asList(
                        PropertiesUtil.toStringArray(attributes.get(ServletResolverConstants.SLING_SERVLET_SELECTORS), new String[0])),
                (String) attributes.get(ServletResolverConstants.SLING_SERVLET_EXTENSIONS),
                (String) attributes.get(ServletResolverConstants.SLING_SERVLET_METHODS),
                (String) attributes.get(BundledScriptTracker.AT_EXTENDS),
                (String) attributes.get(BundledScriptTracker.AT_SCRIPT_ENGINE),
                (String) attributes.get(BundledScriptTracker.AT_SCRIPT_EXTENSION)
        );
    }
}
