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

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.apache.sling.scripting.bundle.tracker.BundledRenderUnit;
import org.jetbrains.annotations.NotNull;

interface Executable extends BundledRenderUnit {

    /**
     * Releases all acquired dependencies which were retrieved through {@link #getService(String)} or {@link #getServices(String, String)}.
     */
    void releaseDependencies();

    /**
     * Returns the path of this executable in the resource type hierarchy. The path can be relative to the search paths or absolute.
     *
     * @return the path of this executable in the resource type hierarchy
     */
    @NotNull
    String getPath();

    /**
     * Returns the short name of the {@link ScriptEngine} with which {@code this Executable} can be evaluated.
     *
     * @return the short name of the script engine
     * @see #eval(ScriptEngine, ScriptContext)
     */
    @NotNull String getScriptEngineName();

    /**
     * Provided a {@link ScriptContext}, this method will execute / evaluate the wrapped script or precompiled script.
     *
     * @param scriptEngine a suitable script engine; see {@link #getScriptEngineName()} in order to see what {@link ScriptEngine}
     *                     implementation is expected
     * @param context      the {@link ScriptContext}
     * @throws ScriptException if the execution leads to an error
     */
    void eval(@NotNull ScriptEngine scriptEngine, @NotNull ScriptContext context) throws ScriptException;
}
