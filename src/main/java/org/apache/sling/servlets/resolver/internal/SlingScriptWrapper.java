/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.servlets.resolver.internal;

import java.io.PrintWriter;
import java.io.Reader;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.api.scripting.SlingJakartaBindings;
import org.apache.sling.api.scripting.SlingJakartaScript;
import org.apache.sling.api.scripting.SlingScript;
import org.apache.sling.api.scripting.SlingScriptHelper;
import org.apache.sling.api.wrappers.JakartaToJavaxRequestWrapper;
import org.apache.sling.api.wrappers.JakartaToJavaxResponseWrapper;
import org.apache.sling.api.wrappers.JavaxToJakartaRequestWrapper;
import org.apache.sling.api.wrappers.JavaxToJakartaResponseWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * TODO - we need to figure out what the best approach to support scripts is
 */
@Deprecated
public class SlingScriptWrapper implements SlingJakartaScript {

    private final SlingScript legacy;

    public SlingScriptWrapper(final SlingScript legacy) {
        this.legacy = legacy;
    }

    @Override
    public Object call(@NotNull final SlingJakartaBindings props, @NotNull final String method, final Object... args) {
        return this.legacy.call(new SlingBindingsWrapper(props), method, args);
    }

    @Override
    public Object eval(@NotNull final SlingJakartaBindings props) {
        return this.legacy.eval(new SlingBindingsWrapper(props));
    }

    @Override
    public @NotNull Resource getScriptResource() {
        return this.legacy.getScriptResource();
    }

    private static class SlingBindingsWrapper extends SlingBindings {

        private final SlingJakartaBindings bindings;

        public SlingBindingsWrapper(final SlingJakartaBindings bindings) {
            this.bindings = bindings;
        }

        public Object put(String key, Object value) {
            return bindings.put(key, value);
        }

        public void putAll(Map<? extends String, ?> toMerge) {
            bindings.putAll(toMerge);
        }

        public void clear() {
            bindings.clear();
        }

        public @NotNull Set<String> keySet() {
            return bindings.keySet();
        }

        public @NotNull Collection<Object> values() {
            return bindings.values();
        }

        public @NotNull Set<Entry<String, Object>> entrySet() {
            return bindings.entrySet();
        }

        public int size() {
            return bindings.size();
        }

        public boolean isEmpty() {
            return bindings.isEmpty();
        }

        public boolean containsKey(Object key) {
            return bindings.containsKey(key);
        }

        public Object get(Object key) {
            return bindings.get(key);
        }

        public Object remove(Object key) {
            return bindings.remove(key);
        }

        public boolean equals(Object o) {
            return bindings.equals(o);
        }

        public int hashCode() {
            return bindings.hashCode();
        }

        public Object getOrDefault(Object key, Object defaultValue) {
            return bindings.getOrDefault(key, defaultValue);
        }

        public void setFlush(boolean flush) {
            bindings.setFlush(flush);
        }

        public boolean getFlush() {
            return bindings.getFlush();
        }

        public void setLog(Logger log) {
            bindings.setLog(log);
        }

        public @Nullable Logger getLog() {
            return bindings.getLog();
        }

        public void setOut(PrintWriter out) {
            bindings.setOut(out);
        }

        public @Nullable PrintWriter getOut() {
            return bindings.getOut();
        }

        public void setRequest(SlingHttpServletRequest request) {
            bindings.setRequest(JavaxToJakartaRequestWrapper.toJakartaRequest(request));
        }

        public @Nullable SlingHttpServletRequest getRequest() {
            return JakartaToJavaxRequestWrapper.toJavaxRequest(bindings.getRequest());
        }

        public void setReader(Reader reader) {
            bindings.setReader(reader);
        }

        public @Nullable Reader getReader() {
            return bindings.getReader();
        }

        public void setResource(Resource resource) {
            bindings.setResource(resource);
        }

        public @Nullable Resource getResource() {
            return bindings.getResource();
        }

        public void setResourceResolver(ResourceResolver resourceResolver) {
            bindings.setResourceResolver(resourceResolver);
        }

        public @Nullable ResourceResolver getResourceResolver() {
            return bindings.getResourceResolver();
        }

        public void setResponse(SlingHttpServletResponse response) {
            bindings.setResponse(JavaxToJakartaResponseWrapper.toJakartaResponse(response));
        }

        public @Nullable SlingHttpServletResponse getResponse() {
            return JakartaToJavaxResponseWrapper.toJavaxResponse(bindings.getResponse());
        }

        public void setSling(SlingScriptHelper sling) {
            // bindings.setSling(sling);
        }

        public @Nullable SlingScriptHelper getSling() {
            return null; // bindings.getSling();
        }

        public String toString() {
            return bindings.toString();
        }

        public boolean containsValue(Object value) {
            return bindings.containsValue(value);
        }

        public Object putIfAbsent(String key, Object value) {
            return bindings.putIfAbsent(key, value);
        }

        public boolean remove(Object key, Object value) {
            return bindings.remove(key, value);
        }

        public boolean replace(String key, Object oldValue, Object newValue) {
            return bindings.replace(key, oldValue, newValue);
        }

        public Object replace(String key, Object value) {
            return bindings.replace(key, value);
        }

        public Object computeIfAbsent(String key, Function<? super String, ? extends Object> mappingFunction) {
            return bindings.computeIfAbsent(key, mappingFunction);
        }

        public Object computeIfPresent(
                String key, BiFunction<? super String, ? super Object, ? extends Object> remappingFunction) {
            return bindings.computeIfPresent(key, remappingFunction);
        }

        public Object compute(
                String key, BiFunction<? super String, ? super Object, ? extends Object> remappingFunction) {
            return bindings.compute(key, remappingFunction);
        }

        public Object merge(
                String key,
                Object value,
                BiFunction<? super Object, ? super Object, ? extends Object> remappingFunction) {
            return bindings.merge(key, value, remappingFunction);
        }

        public void forEach(BiConsumer<? super String, ? super Object> action) {
            bindings.forEach(action);
        }

        public void replaceAll(BiFunction<? super String, ? super Object, ? extends Object> function) {
            bindings.replaceAll(function);
        }
    }
}
