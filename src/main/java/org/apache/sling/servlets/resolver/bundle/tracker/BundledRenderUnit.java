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

import java.io.InputStream;
import java.util.Set;

import javax.script.ScriptException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ConsumerType;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

/**
 * <p>
 * A {@code BundledRenderUnit} represents a pre-packaged script or precompiled script (Java class) that will be executed in order to
 * render a {@link org.apache.sling.api.SlingHttpServletRequest}.
 * </p>
 * <p>
 * The {@code BundledRenderUnit} provider module is responsible for defining how a unit is executed. However, when executing the unit in the
 * context of a {@link javax.script.ScriptEngine}, the provider module should add the current executing unit into the {@link
 * javax.script.ScriptEngine}'s {@link javax.script.ScriptContext} using the {@link #VARIABLE} key.
 * </p>
 */
@ConsumerType
public interface BundledRenderUnit {

    /**
     * The variable available in the {@link javax.script.Bindings} associated to a {@link org.apache.sling.api.SlingHttpServletRequest} if
     * that request is served by a {@code BundledRenderUnit}.
     */
    String VARIABLE = BundledRenderUnit.class.getName();

    /**
     * In case this {@code BundledRenderUnit} wraps a precompiled script, this method will return an instance of that object.
     *
     * @return a precompiled unit, if {@code this} unit wraps a precompiled script; {@code null} otherwise
     */
    @Nullable
    default Object getUnit() {
        return null;
    }

    /**
     * Returns the name of {@code this BundledRenderUnit}. This can be the name of the wrapped script or precompiled script.
     *
     * @return the name {@code this BundledRenderUnit}
     */
    @NotNull String getName();

    /**
     * Returns the {@link Bundle} the publishing bundle of this unit (not to be confused with the provider module, which is the module that
     * instantiates a {@link BundledRenderUnit}). This method can be useful for getting an instance of the bundle's classloader, when
     * needed to load dependencies at run-time. To do so the following code example can help:
     *
     * <pre>
     * Bundle bundle = bundledRenderUnit.getBundle();
     * Classloader bundleClassloader = bundle.adapt(BundleWiring.class).getClassLoader();
     * </pre>
     */
    @NotNull Bundle getBundle();

    /**
     * Returns the {@link BundleContext} to use for this unit. This method can be useful for getting an instance of the publishing bundle's
     * context, when needed to load dependencies at run-time.
     *
     * @return the bundle context of the bundle publishing this unit
     */
    @NotNull BundleContext getBundleContext();

    /**
     * Returns the {@code Set} of {@link TypeProvider}s which are related to this unit.
     *
     * @return the set of providers; if the unit doesn't have any inheritance chains, then the set will contain only one {@link
     * TypeProvider}
     */
    @NotNull Set<TypeProvider> getTypeProviders();

    /**
     * Retrieves an OSGi runtime dependency of the wrapped script identified by the passed {@code className} parameter.
     *
     * @param className the fully qualified class name
     * @param <T>       the expected service type
     * @return an instance of the {@link T} or {@code null}
     */
    @Nullable <T> T getService(@NotNull String className);

    /**
     * Retrieves multiple instances of an OSGi runtime dependency of the wrapped script identified by the passed {@code className}
     * parameter, filtered according to the passed {@code filter}.
     *
     * @param className the fully qualified class name
     * @param filter    a filter expression or {@code null} if all the instances should be returned; for more details about the {@code
     *                  filter}'s syntax check {@link org.osgi.framework.BundleContext#getServiceReferences(String, String)}
     * @param <T>       the expected service type
     * @return an instance of the {@link T} or {@code null}
     */
    @Nullable <T> T[] getServices(@NotNull String className, @Nullable String filter);

    /**
     * Returns the path of this executable in the resource type hierarchy. The path can be relative to the search paths or absolute.
     *
     * @return the path of this executable in the resource type hierarchy
     */
    @NotNull
    String getPath();

    /**
     * This method will execute / evaluate the wrapped script or precompiled script with the given request.
     *
     * @throws ScriptException if the execution leads to an error
     */
    void eval(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) throws ScriptException;

    /**
     * This method will return an input stream if {@code this} unit is backed by a script that can be interpreted.
     *
     * @return an {@link InputStream} providing the source code of the backing script; if {@code this} unit is backed by a precompiled
     * script (essentially a Java class), then this method will return {@code null}
     */
    @Nullable
    default InputStream getInputStream() {
        return null;
    }
}
