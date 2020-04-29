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

import java.util.Objects;

import org.apache.sling.servlets.resolver.bundle.tracker.BundledRenderUnitCapability;
import org.apache.sling.servlets.resolver.bundle.tracker.TypeProvider;
import org.jetbrains.annotations.NotNull;
import org.osgi.framework.Bundle;

class TypeProviderImpl implements TypeProvider
{

    private final BundledRenderUnitCapability bundledRenderUnitCapability;
    private final Bundle bundle;

    TypeProviderImpl(BundledRenderUnitCapability bundledRenderUnitCapability, Bundle bundle) {
        this.bundledRenderUnitCapability = bundledRenderUnitCapability;
        this.bundle = bundle;
    }

    @NotNull
    @Override
    public BundledRenderUnitCapability getBundledRenderUnitCapability() {
        return bundledRenderUnitCapability;
    }

    @NotNull
    @Override
    public Bundle getBundle() {
        return bundle;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bundle, bundledRenderUnitCapability);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof TypeProviderImpl) {
            TypeProviderImpl other = (TypeProviderImpl) obj;
            return Objects.equals(bundle, other.bundle) && Objects.equals(bundledRenderUnitCapability, other.bundledRenderUnitCapability);
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("TypeProvider{ bundledRenderUnitCapability=%s; bundle=%s }", bundledRenderUnitCapability, bundle.getSymbolicName());
    }
}
