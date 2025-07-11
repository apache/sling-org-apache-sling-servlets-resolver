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
package org.apache.sling.servlets.resolver.it.resourcehiding;

import java.util.UUID;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.servlets.resolver.api.ResourcePredicate;
import org.apache.sling.servlets.resolver.it.ServletResolverTestSupport;
import org.apache.sling.servlets.resolver.it.TestServlet;
import org.junit.Before;

/** Base for all our hiding tests, so that they all use the same set of servlets  */
public class ResourceHidingITBase extends ServletResolverTestSupport {

    protected final static String EXT_A = "EXT_A" + UUID.randomUUID();
    protected final static String EXT_B = "EXT_B" + UUID.randomUUID();
    protected final static String SEL_A = "SEL_A" + UUID.randomUUID();
    protected int hiddenResourcesCount = 0;

    @Before
    public void reset() {
        hiddenResourcesCount = 0;
    }

    @Before
    public void setupTestServletsAndResourceHiding() throws Exception {
        // Register two servlets differing only in extensions
        new TestServlet(EXT_A)
        .with(P_RESOURCE_TYPES, RT_DEFAULT)
        .with(P_METHODS, M_GET)
        .with(P_EXTENSIONS, EXT_A)
        .register(bundleContext);

        new TestServlet(EXT_B)
        .with(P_RESOURCE_TYPES, RT_DEFAULT)
        .with(P_METHODS, M_GET)
        .with(P_EXTENSIONS, EXT_B)
        .register(bundleContext);

        // And one more specific servlet, that will fall back
        // to EXT_A when hidden
        new TestServlet(SEL_A)
        .with(P_RESOURCE_TYPES, RT_DEFAULT)
        .with(P_METHODS, M_GET)
        .with(P_EXTENSIONS, EXT_A)
        .with(P_SELECTORS, SEL_A)
        .register(bundleContext);
    }

    protected void registerPredicate(ResourcePredicate p) {
        final ResourcePredicate wrappedPredicate = new ResourcePredicate() {
            @Override
            public boolean test(Resource r) {
                final boolean result = p.test(r);
                if(result) {
                    hiddenResourcesCount++;
                }
                return result;
            }
        };
        bundleContext.registerService(ResourcePredicate.class.getName(), wrappedPredicate, null);
    }
}