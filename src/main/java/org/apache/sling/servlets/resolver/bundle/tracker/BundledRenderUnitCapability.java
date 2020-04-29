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
package org.apache.sling.servlets.resolver.bundle.tracker;

import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ProviderType;

/**
 * A {@code BundledRenderUnitCapability} encapsulates the values of a {@code Provided-Capability}, based on which {@link BundledRenderUnit}s
 * are generated.
 */
@ProviderType
public interface BundledRenderUnitCapability {

    /**
     * Returns the resource types to which a {@link BundledRenderUnit} described by this capability will be bound to.
     *
     * @return the resource types to which a {@link BundledRenderUnit} described by this capability will be bound to
     */
    @NotNull Set<ResourceType> getResourceTypes();

    /**
     * Returns the path to which a {@link BundledRenderUnit} described by this capability will be bound to.
     *
     * @return the path to which a {@link BundledRenderUnit} described by this capability will be bound to; this can be {@code null} if the
     * {@link #getResourceTypes()} doesn't return an empty set
     */
    @Nullable String getPath();

    /**
     * Returns the selectors to which a {@link BundledRenderUnit} described by this capability will be bound to.
     *
     * @return the selectors to which a {@link BundledRenderUnit} described by this capability will be bound to
     */
    @NotNull List<String> getSelectors();

    /**
     * Returns the extension to which a {@link BundledRenderUnit} described by this capability will be bound to.
     *
     * @return the extension to which a {@link BundledRenderUnit} described by this capability will be bound to
     */
    @Nullable String getExtension();

    /**
     * Returns the resource type extended by this capability.
     *
     * @return the extended resource type or {@code null}
     */
    @Nullable String getExtendedResourceType();

    /**
     * Returns the request method to which a {@link BundledRenderUnit} described by this capability will be bound to.
     *
     * @return the request method to which a {@link BundledRenderUnit} described by this capability will be bound to
     */
    @Nullable String getMethod();

    /**
     * Returns the script engine short name which can be used to evaluate the {@link BundledRenderUnit} described by this capability.
     *
     * @return the script engine short name which can be used to evaluate the {@link BundledRenderUnit} described by this capability.
     */
    @Nullable String getScriptEngineName();

    /**
     * Returns the original's script extension that was used to generate this capability.
     *
     * @return the original's script extension that was used to generate this capability.
     */
    @Nullable String getScriptExtension();
}
