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

import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ConsumerType;
import org.osgi.framework.BundleContext;

/**
 * The {@code BundledScriptFinder} finds the {@link BundledRenderUnit} corresponding to a certain chain of {@link TypeProvider}s or
 * corresponding to a certain path-based {@link BundledRenderUnitCapability}.
 */
@ConsumerType
public interface BundledRenderUnitFinder {

    /**
     * Retrieves the best matching {@link BundledRenderUnit} for the provided {@code inheritanceChain}, by scanning all {@link TypeProvider}
     * bundles for the class or script capable of providing a rendering for resource type chain.
     *
     * @param context             the bundle context to use.
     * @param inheritanceChain    the resource type chain; the set is ordered from the most specific resource type to the most generic one
     * @param allRelatedProviders this is a super set, containing both the {@code inheritanceChain} but also all the required providers; a
     *                            required provider is a provider that's needed by a {@link ResourceType} in order to delegate rendering to
     *                            it, but it's not extended by the same {@link ResourceType}
     * @return a {@link BundledRenderUnit} if one was found, {@code null} otherwise
     */
    @Nullable
    BundledRenderUnit findUnit(@NotNull BundleContext context, @NotNull Set<TypeProvider> inheritanceChain, @NotNull Set<TypeProvider> allRelatedProviders);

    /**
     * Retrieves a path-based {@link BundledRenderUnit} from the passed {@code provider}.
     *
     * @param context             the bundle context to use.
     * @param provider            the provider from which to retrieve the unit
     * @param allRelatedProviders this is a super set, containing both the providers connected through an inheritance relationship but also
     *                            all the required providers; a required provider is a provider that's needed by a {@link ResourceType} in
     *                            order to delegate rendering to it, but it's not extended by the same {@link ResourceType}
     * @return a {@link BundledRenderUnit} if one was found, {@code null} otherwise
     * @see BundledRenderUnitCapability#getPath()
     */
    @Nullable
    BundledRenderUnit findUnit(@NotNull BundleContext context, @NotNull TypeProvider provider, @NotNull Set<TypeProvider> allRelatedProviders);
}
