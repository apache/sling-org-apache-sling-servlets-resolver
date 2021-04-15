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
package org.apache.sling.servlets.resolver.internal.bundle;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.type.ResourceType;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.scripting.spi.bundle.BundledRenderUnitCapability;
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(BundledRenderUnitCapability.class.getSimpleName()).append("[");
        if (!resourceTypes.isEmpty()) {
            sb.append(ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES).append("=").append(resourceTypes);
        }
        if (!selectors.isEmpty()) {
            sb.append("; ").append(ServletResolverConstants.SLING_SERVLET_SELECTORS).append("=").append(selectors);
        }
        if (StringUtils.isNotEmpty(extension)) {
            sb.append("; ").append(ServletResolverConstants.SLING_SERVLET_EXTENSIONS).append("=").append(extension);
        }
        if (StringUtils.isNotEmpty(method)) {
            sb.append("; ").append(ServletResolverConstants.SLING_SERVLET_METHODS).append("=").append(method);
        }
        if (StringUtils.isNotEmpty(path)) {
            sb.append("; ").append(ServletResolverConstants.SLING_SERVLET_PATHS).append("=").append(path);
        }
        if (StringUtils.isNotEmpty(extendedResourceType)) {
            sb.append("; ").append(BundledScriptTracker.AT_EXTENDS).append("=").append(extendedResourceType);
        }
        if (StringUtils.isNotEmpty(scriptEngineName)) {
            sb.append("; ").append(BundledScriptTracker.AT_SCRIPT_ENGINE).append("=").append(scriptEngineName);
        }
        if (StringUtils.isNotEmpty(scriptExtension)) {
            sb.append("; ").append(BundledScriptTracker.AT_SCRIPT_EXTENSION).append("=").append(scriptExtension);
        }
        sb.append("]");
        return sb.toString();
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
                resourceTypes.add(ResourceType.parseResourceType(rt + "/" + version));
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Set<ResourceType> resourceTypes;
        private String path;
        private List<String> selectors;
        private String extension;
        private String method;
        private String extendedResourceType;
        private String scriptEngineName;
        private String scriptExtension;

        public Builder withResourceTypes(@NotNull Set<ResourceType> resourceTypes) {
            this.resourceTypes = resourceTypes;
            return this;
        }

        public Builder withPath(@Nullable String path) {
            this.path = path;
            return this;
        }

        public Builder withSelectors(@NotNull List<String> selectors) {
            this.selectors = selectors;
            return this;
        }

        public Builder withExtension(@Nullable String extension) {
            this.extension = extension;
            return this;
        }

        public Builder withMethod(@Nullable String method) {
            this.method = method;
            return this;
        }

        public Builder withExtendedResourceType(@Nullable String extendedResourceType) {
            this.extendedResourceType = extendedResourceType;
            return this;
        }

        public Builder withScriptEngineName(@Nullable String scriptEngineName) {
            this.scriptEngineName = scriptEngineName;
            return this;
        }

        public Builder withScriptEngineExtension(@Nullable String scriptExtension) {
            this.scriptExtension = scriptExtension;
            return this;
        }

        public Builder fromCapability(@NotNull BundledRenderUnitCapability capability) {
            this.extendedResourceType = capability.getExtendedResourceType();
            this.extension = capability.getExtension();
            this.method = capability.getMethod();
            this.path = capability.getPath();
            this.resourceTypes = capability.getResourceTypes();
            this.scriptEngineName = capability.getScriptEngineName();
            this.scriptExtension = capability.getScriptExtension();
            this.selectors = capability.getSelectors();
            return this;
        }

        public BundledRenderUnitCapability build() {
            return new BundledRenderUnitCapabilityImpl(resourceTypes, path, selectors, extension, method, extendedResourceType,
                    scriptEngineName, scriptExtension);
        }
    }
}
