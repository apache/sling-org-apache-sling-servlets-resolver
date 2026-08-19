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
package org.apache.sling.servlets.resolver.internal.bundle;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.sling.api.resource.type.ResourceType;
import org.apache.sling.scripting.spi.bundle.BundledRenderUnitCapability;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BundledRenderUnitCapabilityImplTest {

    @Test
    public void testNullValues() {
        Set<ResourceType> resourceTypes = Collections.emptySet();
        List<String> selectors = Collections.emptyList();

        BundledRenderUnitCapability capability = new BundledRenderUnitCapabilityImpl.Builder()
                .withResourceTypes(resourceTypes)
                .withPath(null)
                .withSelectors(selectors)
                .withExtension(null)
                .withMethod(null)
                .withExtendedResourceType(null)
                .withScriptEngineName(null)
                .withScriptEngineExtension(null)
                .build();

        assertNull("Path should be null when null is passed", capability.getPath());
        assertNull("Extension should be null when null is passed", capability.getExtension());
        assertNull("Method should be null when null is passed", capability.getMethod());
        assertNull("ExtendedResourceType should be null when null is passed", capability.getExtendedResourceType());
        assertNull("ScriptEngineName should be null when null is passed", capability.getScriptEngineName());
        assertNull("ScriptExtension should be null when null is passed", capability.getScriptExtension());
    }

    @Test
    public void testEmptyStringsReturnNull() {
        Set<ResourceType> resourceTypes = Collections.emptySet();
        List<String> selectors = Collections.emptyList();

        BundledRenderUnitCapability capability = new BundledRenderUnitCapabilityImpl.Builder()
                .withResourceTypes(resourceTypes)
                .withPath("")
                .withSelectors(selectors)
                .withExtension("")
                .withMethod("")
                .withExtendedResourceType("")
                .withScriptEngineName("")
                .withScriptEngineExtension("")
                .build();

        assertNull("Path should be null when empty string is passed", capability.getPath());
        assertNull("Extension should be null when empty string is passed", capability.getExtension());
        assertNull("Method should be null when empty string is passed", capability.getMethod());
        assertNull(
                "ExtendedResourceType should be null when empty string is passed",
                capability.getExtendedResourceType());
        assertNull("ScriptEngineName should be null when empty string is passed", capability.getScriptEngineName());
        assertNull("ScriptExtension should be null when empty string is passed", capability.getScriptExtension());
    }

    @Test
    public void testNonEmptyValues() {
        Set<ResourceType> resourceTypes = Collections.singleton(ResourceType.parseResourceType("test/resourceType"));
        List<String> selectors = List.of("selector1", "selector2");

        BundledRenderUnitCapability capability = new BundledRenderUnitCapabilityImpl.Builder()
                .withResourceTypes(resourceTypes)
                .withPath("/test/path")
                .withSelectors(selectors)
                .withExtension("html")
                .withMethod("GET")
                .withExtendedResourceType("extended/type")
                .withScriptEngineName("engineName")
                .withScriptEngineExtension("scriptExt")
                .build();

        assertEquals("/test/path", capability.getPath());
        assertEquals("html", capability.getExtension());
        assertEquals("GET", capability.getMethod());
        assertEquals("extended/type", capability.getExtendedResourceType());
        assertEquals("engineName", capability.getScriptEngineName());
        assertEquals("scriptExt", capability.getScriptExtension());
        assertEquals(resourceTypes, capability.getResourceTypes());
        assertEquals(selectors, capability.getSelectors());
    }

    @Test
    public void testEmptyCollections() {
        Set<ResourceType> resourceTypes = Collections.emptySet();
        List<String> selectors = Collections.emptyList();

        BundledRenderUnitCapability capability = new BundledRenderUnitCapabilityImpl.Builder()
                .withResourceTypes(resourceTypes)
                .withSelectors(selectors)
                .build();

        assertTrue(
                "ResourceTypes should be empty", capability.getResourceTypes().isEmpty());
        assertTrue("Selectors should be empty", capability.getSelectors().isEmpty());
    }

    @Test
    public void testEqualsAndHashCode() {
        Set<ResourceType> resourceTypes = Collections.singleton(ResourceType.parseResourceType("test/resourceType"));
        List<String> selectors = List.of("selector1");

        BundledRenderUnitCapability capability1 = new BundledRenderUnitCapabilityImpl.Builder()
                .withResourceTypes(resourceTypes)
                .withPath("/test/path")
                .withSelectors(selectors)
                .build();

        BundledRenderUnitCapability capability2 = new BundledRenderUnitCapabilityImpl.Builder()
                .withResourceTypes(resourceTypes)
                .withPath("/test/path")
                .withSelectors(selectors)
                .build();

        assertEquals("Capabilities should be equal", capability1, capability2);
        assertEquals("Hash codes should match", capability1.hashCode(), capability2.hashCode());
    }

    @Test
    public void testToString() {
        Set<ResourceType> resourceTypes = Collections.singleton(ResourceType.parseResourceType("test/resourceType"));
        List<String> selectors = List.of("selector1");

        BundledRenderUnitCapability capability = new BundledRenderUnitCapabilityImpl.Builder()
                .withResourceTypes(resourceTypes)
                .withPath("/test/path")
                .withSelectors(selectors)
                .build();

        String toString = capability.toString();
        assertTrue("toString should contain resource type", toString.contains("test/resourceType"));
        assertTrue("toString should contain path", toString.contains("/test/path"));
        assertTrue("toString should contain selector", toString.contains("selector1"));
    }
}
