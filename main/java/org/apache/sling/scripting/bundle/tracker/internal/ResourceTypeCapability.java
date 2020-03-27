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
package org.apache.sling.scripting.bundle.tracker.internal;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.scripting.bundle.tracker.ResourceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.Version;
import org.osgi.framework.wiring.BundleCapability;

class ResourceTypeCapability {

    private final Set<ResourceType> resourceTypes;
    private final List<String> selectors;
    private final String extension;
    private final String method;
    private final String extendedResourceType;
    private final String scriptEngineName;
    private final String scriptExtension;

    private ResourceTypeCapability(@NotNull Set<ResourceType> resourceTypes, @NotNull List<String> selectors,
                                   @Nullable String extension, @Nullable String method,
                                   @Nullable String extendedResourceType, @Nullable String scriptEngineName,
                                   @Nullable String scriptExtension) {
        this.resourceTypes = resourceTypes;
        this.selectors = selectors;
        this.extension = extension;
        this.method = method;
        this.extendedResourceType = extendedResourceType;
        this.scriptEngineName = scriptEngineName;
        this.scriptExtension = scriptExtension;
    }

    @NotNull
    Set<ResourceType> getResourceTypes() {
        return Collections.unmodifiableSet(resourceTypes);
    }

    @NotNull
    List<String> getSelectors() {
        return Collections.unmodifiableList(selectors);
    }

    @Nullable
    String getExtension() {
        return extension;
    }

    @Nullable
    String getExtendedResourceType() {
        return extendedResourceType;
    }

    @Nullable
    String getMethod() {
        return method;
    }

    @Nullable
    String getScriptEngineName() {
        return scriptEngineName;
    }

    @Nullable
    String getScriptExtension() {
        return scriptExtension;
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceTypes, selectors, extension, method, extendedResourceType, scriptEngineName, scriptExtension);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof ResourceTypeCapability) {
            ResourceTypeCapability other = (ResourceTypeCapability) obj;
            return Objects.equals(resourceTypes, other.resourceTypes) && Objects.equals(selectors, other.selectors) &&
                    Objects.equals(extension, other.extension) && Objects.equals(method, other.method) &&
                    Objects.equals(extendedResourceType, other.extendedResourceType) &&
                    Objects.equals(scriptEngineName, other.scriptEngineName) && Objects.equals(scriptExtension, other.scriptExtension);
        }
        return false;
    }

    static ResourceTypeCapability fromBundleCapability(@NotNull BundleCapability capability) {
        Map<String, Object> attributes = capability.getAttributes();
        Set<ResourceType> resourceTypes = new HashSet<>();
        String[] capabilityResourceTypes = PropertiesUtil.toStringArray(attributes.get(BundledScriptTracker.NS_SLING_RESOURCE_TYPE),
                new String[0]);
        Version version = (Version) attributes.get(BundledScriptTracker.AT_VERSION);
        for (String rt : capabilityResourceTypes) {
            if (version == null) {
                resourceTypes.add(ResourceType.parseResourceType(rt));
            } else {
                resourceTypes.add(ResourceType.parseResourceType(rt + "/" + version.toString()));
            }
        }
        return new ResourceTypeCapability(
                resourceTypes,
                Arrays.asList(PropertiesUtil.toStringArray(attributes.get(BundledScriptTracker.AT_SLING_SELECTORS), new String[0])),
                (String) attributes.get(BundledScriptTracker.AT_SLING_EXTENSIONS),
                (String) attributes.get(ServletResolverConstants.SLING_SERVLET_METHODS),
                (String) attributes.get(BundledScriptTracker.AT_EXTENDS),
                (String) attributes.get(BundledScriptTracker.AT_SCRIPT_ENGINE),
                (String) attributes.get(BundledScriptTracker.AT_SCRIPT_EXTENSION)
        );
    }
}
