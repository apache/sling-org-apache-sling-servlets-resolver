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

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SlingServletResolverTest {

    @Test
    public void testIsInvalidPath() {
        assertFalse(SlingServletResolver.isInvalidPath("/../"));
        assertTrue(SlingServletResolver.isInvalidPath("/.../"));
        assertTrue(SlingServletResolver.isInvalidPath("/..../"));

        assertFalse(SlingServletResolver.isInvalidPath("/.."));
        assertTrue(SlingServletResolver.isInvalidPath("/..."));
        assertTrue(SlingServletResolver.isInvalidPath("/...."));

        assertFalse(SlingServletResolver.isInvalidPath("../"));
        assertTrue(SlingServletResolver.isInvalidPath(".../"));
        assertTrue(SlingServletResolver.isInvalidPath("..../"));

        assertFalse(SlingServletResolver.isInvalidPath(".."));
        assertTrue(SlingServletResolver.isInvalidPath("..."));
        assertTrue(SlingServletResolver.isInvalidPath("...."));

        assertFalse(SlingServletResolver.isInvalidPath("/../aa"));
        assertTrue(SlingServletResolver.isInvalidPath("/.../aa"));
        assertTrue(SlingServletResolver.isInvalidPath("/..../aa"));

        assertFalse(SlingServletResolver.isInvalidPath("aa/../"));
        assertTrue(SlingServletResolver.isInvalidPath("aa/.../"));
        assertTrue(SlingServletResolver.isInvalidPath("aa/..../"));

        assertFalse(SlingServletResolver.isInvalidPath("aa/../"));
        assertTrue(SlingServletResolver.isInvalidPath("aa/.../"));
        assertTrue(SlingServletResolver.isInvalidPath("aa/..../"));

        assertFalse(SlingServletResolver.isInvalidPath("aa/../aa"));
        assertTrue(SlingServletResolver.isInvalidPath("aa/.../aa"));
        assertTrue(SlingServletResolver.isInvalidPath("aa/..../aa"));

        assertFalse(SlingServletResolver.isInvalidPath("/aa/../aa"));
        assertTrue(SlingServletResolver.isInvalidPath("/aa/.../aa"));
        assertTrue(SlingServletResolver.isInvalidPath("/aa/..../aa"));

        assertFalse(SlingServletResolver.isInvalidPath("aa/.."));
        assertTrue(SlingServletResolver.isInvalidPath("aa/..."));
        assertTrue(SlingServletResolver.isInvalidPath("aa/...."));

        assertFalse(SlingServletResolver.isInvalidPath("aa/b.../"));
        assertFalse(SlingServletResolver.isInvalidPath("aa/...b/"));
        assertFalse(SlingServletResolver.isInvalidPath("aa/b...b/"));

        assertFalse(SlingServletResolver.isInvalidPath("aa/b.c.d.e.f/"));
        assertFalse(SlingServletResolver.isInvalidPath("aa/b.c.d...e.f/"));
    }
}
