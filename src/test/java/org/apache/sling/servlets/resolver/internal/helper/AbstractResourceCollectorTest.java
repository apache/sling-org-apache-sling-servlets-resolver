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

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Map;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

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
    
    @Test
    public void testWithCachingEnabled() {
        
        Resource spy = Mockito.spy(context.resourceResolver().getResource("/parent"));
        
        List<Resource> children = AbstractResourceCollector.getChildrenList(spy, true);
        assertEquals(3,children.size());
        children = AbstractResourceCollector.getChildrenList(spy, true);
        Mockito.verify(spy,Mockito.times(1)).listChildren();
        
        Map<String,List<Resource>> map = (Map<String, List<Resource>>) 
        		context.resourceResolver().getPropertyMap().get(AbstractResourceCollector.CACHE_KEY_CHILDREN_LIST);
        assertEquals(1, map.values().size());
        AbstractResourceCollector.clearCache(context.resourceResolver());
        assertEquals(0, map.values().size());
    }
    
    
    @Test
    public void testWithCachingDisabled() {
        
        Resource spy = Mockito.spy(context.resourceResolver().getResource("/parent"));
        
        List<Resource> children = AbstractResourceCollector.getChildrenList(spy, false);
        assertEquals(3,children.size());
        children = AbstractResourceCollector.getChildrenList(spy, false);
        Mockito.verify(spy,Mockito.times(2)).listChildren(); 
    }
    
    @Test
    public void testWithCachingWithAlreadyUsedCacheKey() {
        
        Resource spy = Mockito.spy(context.resourceResolver().getResource("/parent"));
        String payload = "some payload";
        context.resourceResolver().getPropertyMap().put(AbstractResourceCollector.CACHE_KEY_CHILDREN_LIST, payload);
        
        List<Resource> children = AbstractResourceCollector.getChildrenList(spy, true);
        assertEquals(3,children.size());
        children = AbstractResourceCollector.getChildrenList(spy, true);
        Mockito.verify(spy,Mockito.times(2)).listChildren();
        assertEquals(payload, context.resourceResolver().getPropertyMap().get(AbstractResourceCollector.CACHE_KEY_CHILDREN_LIST));
        
        AbstractResourceCollector.clearCache(context.resourceResolver());
        assertEquals(payload, context.resourceResolver().getPropertyMap().get(AbstractResourceCollector.CACHE_KEY_CHILDREN_LIST));
    }
}
