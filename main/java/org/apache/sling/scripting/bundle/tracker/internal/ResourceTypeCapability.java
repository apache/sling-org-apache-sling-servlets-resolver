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
import java.util.Map;
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
    private final Set<String> selectors;
    private final Set<String> extensions;
    private final Set<String> methods;
    private final String extendedResourceType;
    private final String scriptEngineName;

    private ResourceTypeCapability(@NotNull Set<ResourceType> resourceTypes, @NotNull Set<String> selectors,
                                   @NotNull Set<String> extensions, @NotNull Set<String> methods,
                                   @Nullable String extendedResourceType, @Nullable String scriptEngineName) {
        this.resourceTypes = resourceTypes;
        this.selectors = selectors;
        this.extensions = extensions;
        this.methods = methods;
        this.extendedResourceType = extendedResourceType;
        this.scriptEngineName = scriptEngineName;
    }

    @NotNull
    Set<ResourceType> getResourceTypes() {
        return Collections.unmodifiableSet(resourceTypes);
    }

    @NotNull
    Set<String> getSelectors() {
        return Collections.unmodifiableSet(selectors);
    }

    @NotNull
    Set<String> getExtensions() {
        return Collections.unmodifiableSet(extensions);
    }

    @Nullable
    String getExtendedResourceType() {
        return extendedResourceType;
    }

    @NotNull
    Set<String> getMethods() {
        return Collections.unmodifiableSet(methods);
    }

    @Nullable
    String getScriptEngineName() {
        return scriptEngineName;
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
                new HashSet<>(Arrays.asList(
                        PropertiesUtil.toStringArray(attributes.get(BundledScriptTracker.AT_SLING_SELECTORS), new String[0]))),
                new HashSet<>(Arrays.asList(PropertiesUtil.toStringArray(attributes.get(BundledScriptTracker.AT_SLING_EXTENSIONS),
                        new String[0]))),
                new HashSet<>(Arrays.asList(
                        PropertiesUtil.toStringArray(attributes.get(ServletResolverConstants.SLING_SERVLET_METHODS), new String[0]))),
                (String) attributes.get(BundledScriptTracker.AT_EXTENDS),
                (String) attributes.get(BundledScriptTracker.AT_SCRIPT_ENGINE)
        );
    }
}
