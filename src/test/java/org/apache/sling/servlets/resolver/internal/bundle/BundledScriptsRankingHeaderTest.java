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

import java.util.Dictionary;
import java.util.Hashtable;

import org.junit.Test;
import org.osgi.framework.Bundle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies how {@link BundledScriptTracker#getBundledScriptsRanking(Bundle)} interprets the optional
 * {@code Sling-Bundled-Scripts-Ranking} manifest header.
 */
public class BundledScriptsRankingHeaderTest {

    /** No header present: behave like today, i.e. no ranking is derived. */
    @Test
    public void testAbsentHeaderYieldsNull() {
        assertNull(BundledScriptTracker.getBundledScriptsRanking(bundleWithHeader(null)));
    }

    /** A valid integer header value is returned as the ranking. */
    @Test
    public void testValidHeaderIsParsed() {
        assertEquals(Integer.valueOf(100), BundledScriptTracker.getBundledScriptsRanking(bundleWithHeader("100")));
    }

    /** Negative rankings are valid. */
    @Test
    public void testNegativeHeaderIsParsed() {
        assertEquals(Integer.valueOf(-5), BundledScriptTracker.getBundledScriptsRanking(bundleWithHeader("-5")));
    }

    /** Surrounding whitespace is tolerated. */
    @Test
    public void testWhitespaceIsTrimmed() {
        assertEquals(Integer.valueOf(42), BundledScriptTracker.getBundledScriptsRanking(bundleWithHeader("  42 ")));
    }

    /** A non-integer value is treated as if the header was absent (a warning is logged). */
    @Test
    public void testNonIntegerHeaderIsTreatedAsAbsent() {
        assertNull(BundledScriptTracker.getBundledScriptsRanking(bundleWithHeader("not-a-number")));
    }

    /** An empty value cannot be coerced to an int and is therefore treated as absent. */
    @Test
    public void testEmptyHeaderIsTreatedAsAbsent() {
        assertNull(BundledScriptTracker.getBundledScriptsRanking(bundleWithHeader("")));
    }

    private static Bundle bundleWithHeader(final String value) {
        final Dictionary<String, String> headers = new Hashtable<>();
        if (value != null) {
            headers.put(BundledScriptTracker.HEADER_SCRIPTS_RANKING, value);
        }
        final Bundle bundle = mock();
        when(bundle.getHeaders()).thenReturn(headers);
        when(bundle.getSymbolicName()).thenReturn("com.example.test");
        return bundle;
    }
}
