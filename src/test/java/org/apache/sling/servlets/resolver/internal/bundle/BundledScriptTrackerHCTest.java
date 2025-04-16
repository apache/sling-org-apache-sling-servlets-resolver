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

import java.util.Set;

import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.util.collections.Sets;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BundledScriptTrackerHCTest {

    @Test
    public void test_filterForExistingBundles() {
        BundleContext bc = Mockito.mock(BundleContext.class);
        Mockito.doReturn(new Bundle[] {
                    mockBundle("a"), mockBundle("b"), mockBundle("c"), mockBundle("d"), mockBundle("e")
                })
                .when(bc)
                .getBundles();

        Set<String> expectedSymbolicNames = Sets.newSet("a", "b", "z");
        Set<String> res = BundledScriptTrackerHC.filterForExistingBundles(bc, expectedSymbolicNames);
        assertNotNull(res);
        assertEquals(2, res.size());
        assertTrue(res.contains("a"));
        assertTrue(res.contains("b"));
    }

    private static Bundle mockBundle(String symbolicName) {
        Bundle b = Mockito.mock(Bundle.class);
        Mockito.when(b.getSymbolicName()).thenReturn(symbolicName);
        return b;
    }
}
