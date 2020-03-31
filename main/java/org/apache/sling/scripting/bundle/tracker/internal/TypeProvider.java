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

import java.util.Objects;

import org.osgi.framework.Bundle;

/**
 * A {@code TypeProvider} keeps an association between a versioned resource type and the bundle that provides it.
 */
public class TypeProvider {

    private final ServletCapability servletCapability;
    private final Bundle bundle;
    private final boolean precompiled;

    /**
     * Builds a {@code TypeProvider}.
     *
     * @param servletCapability  the resource type capability
     * @param bundle the bundle that provides the resource type
     */
    TypeProvider(ServletCapability servletCapability, Bundle bundle) {
        this.servletCapability = servletCapability;
        this.bundle = bundle;
        precompiled = Boolean.parseBoolean(bundle.getHeaders().get("Sling-ResourceType-Precompiled"));
    }

    /**
     * Returns the resource type capabilities.
     *
     * @return the resource type capabilities
     */
    ServletCapability getServletCapability() {
        return servletCapability;
    }

    /**
     * Returns the providing bundle.
     *
     * @return the providing bundle
     */
    Bundle getBundle() {
        return bundle;
    }

    /**
     * Returns {@code true} if the bundle provides precompiled scripts.
     *
     * @return {@code true} if the bundle provides precompiled scripts, {@code false} otherwise
     */
    public boolean isPrecompiled() {
        return precompiled;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bundle, servletCapability, precompiled);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof TypeProvider) {
            TypeProvider other = (TypeProvider) obj;
            return Objects.equals(bundle, other.bundle) && Objects.equals(servletCapability, other.servletCapability) &&
                    Objects.equals(precompiled, other.precompiled);
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("TypeProvider{ resourceTypeCapability=%s; bundle=%s; precompiled=%s }", servletCapability,
                bundle.getSymbolicName(), precompiled);
    }
}
