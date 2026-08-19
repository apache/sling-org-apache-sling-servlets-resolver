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
package org.apache.sling.servlets.resolver.internal.helper;

import java.util.List;
import java.util.Map;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class AbstractResourceCollectorTest {

    @Rule
    public SlingContext context = new SlingContext();

    @Before
    public void setup() {
        context.create().resource("/parent");
        context.create().resource("/parent/child1");
        context.create().resource("/parent/child2");
        context.create().resource("/parent/child3");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGetChildrenWithCachingEnabled() {

        Resource spy = Mockito.spy(context.resourceResolver().getResource("/parent"));

        List<Resource> children = AbstractResourceCollector.getChildrenList(spy, true);
        assertEquals(3, children.size());
        children = AbstractResourceCollector.getChildrenList(spy, true);
        Mockito.verify(spy, Mockito.times(1)).listChildren();

        Map<String, List<Resource>> map = (Map<String, List<Resource>>)
                context.resourceResolver().getPropertyMap().get(AbstractResourceCollector.CACHE_KEY_CHILDREN_LIST);
        assertEquals(1, map.values().size());
        AbstractResourceCollector.clearCache(context.resourceResolver());
        assertEquals(0, map.values().size());
    }

    @Test
    public void testGetChildrenWithCachingDisabled() {

        Resource spy = Mockito.spy(context.resourceResolver().getResource("/parent"));

        List<Resource> children = AbstractResourceCollector.getChildrenList(spy, false);
        assertEquals(3, children.size());
        children = AbstractResourceCollector.getChildrenList(spy, false);
        Mockito.verify(spy, Mockito.times(2)).listChildren();
    }

    @Test
    public void testGetChildrenWithCachingWithAlreadyUsedCacheKey() {

        Resource spy = Mockito.spy(context.resourceResolver().getResource("/parent"));
        String payload = "some payload";
        context.resourceResolver().getPropertyMap().put(AbstractResourceCollector.CACHE_KEY_CHILDREN_LIST, payload);

        List<Resource> children = AbstractResourceCollector.getChildrenList(spy, true);
        assertEquals(3, children.size());
        children = AbstractResourceCollector.getChildrenList(spy, true);
        Mockito.verify(spy, Mockito.times(2)).listChildren();
        assertEquals(
                payload,
                context.resourceResolver().getPropertyMap().get(AbstractResourceCollector.CACHE_KEY_CHILDREN_LIST));

        AbstractResourceCollector.clearCache(context.resourceResolver());
        assertEquals(
                payload,
                context.resourceResolver().getPropertyMap().get(AbstractResourceCollector.CACHE_KEY_CHILDREN_LIST));
    }

    @Test
    public void testGetResourceOrNullWithCachingEnabled() {
        ResourceResolver spyResolver = Mockito.spy(context.resourceResolver());

        // not yet initialized
        Resource res1 = AbstractResourceCollector.getResourceOrNull(spyResolver, "/parent/child1", true);
        assertNotNull(res1);
        Mockito.verify(spyResolver, Mockito.times(1)).getResource("/parent/child1");
        // cache hit
        Resource res2 = AbstractResourceCollector.getResourceOrNull(spyResolver, "/parent/child1", true);
        assertNotNull(res2);
        Mockito.verify(spyResolver, Mockito.times(1)).getResource("/parent/child1");
        assertEquals(res1, res2);

        // cache miss
        Resource res3 = AbstractResourceCollector.getResourceOrNull(spyResolver, "/parent/child2", true);
        assertNotNull(res3);
        Mockito.verify(spyResolver, Mockito.times(1)).getResource("/parent/child2");

        // cache miss
        assertNull(AbstractResourceCollector.getResourceOrNull(spyResolver, "/parent/nonExistingChild", true));
        assertNull(AbstractResourceCollector.getResourceOrNull(spyResolver, "/parent/nonExistingChild", true));
        Mockito.verify(spyResolver, Mockito.times(1)).getResource("/parent/nonExistingChild");

        // when the cache is cleared, it should read again via the ResourceResolver
        AbstractResourceCollector.clearCache(spyResolver);
        Mockito.reset(spyResolver);
        Resource res4 = AbstractResourceCollector.getResourceOrNull(spyResolver, "/parent/child1", true);
        assertNotNull(res4);
        Mockito.verify(spyResolver, Mockito.times(1)).getResource("/parent/child1");
    }

    @Test
    public void testGetResourceOrNullWithAlreadyUsedCacheKey() {
        ResourceResolver spyResolver = Mockito.spy(context.resourceResolver());

        String payload = "some payload";
        spyResolver.getPropertyMap().put(AbstractResourceCollector.CACHE_KEY_RESOURCES, payload);

        // not yet initialized
        AbstractResourceCollector.getResourceOrNull(spyResolver, "/parent/child1", true);
        AbstractResourceCollector.getResourceOrNull(spyResolver, "/parent/child1", true);
        Mockito.verify(spyResolver, Mockito.times(2)).getResource("/parent/child1");

        assertEquals(payload, spyResolver.getPropertyMap().get(AbstractResourceCollector.CACHE_KEY_RESOURCES));
        AbstractResourceCollector.clearCache(spyResolver);
        assertEquals(payload, spyResolver.getPropertyMap().get(AbstractResourceCollector.CACHE_KEY_RESOURCES));
    }

    @Test
    public void testGetResourceOrNullCachingDisabled() {
        ResourceResolver spyResolver = Mockito.spy(context.resourceResolver());

        Resource res1 = AbstractResourceCollector.getResourceOrNull(spyResolver, "/parent/child1", false);
        assertNotNull(res1);
        Mockito.verify(spyResolver, Mockito.times(1)).getResource("/parent/child1");
        Resource res2 = AbstractResourceCollector.getResourceOrNull(spyResolver, "/parent/child1", false);
        assertNotNull(res2);
        Mockito.verify(spyResolver, Mockito.times(2)).getResource("/parent/child1");
    }
}
