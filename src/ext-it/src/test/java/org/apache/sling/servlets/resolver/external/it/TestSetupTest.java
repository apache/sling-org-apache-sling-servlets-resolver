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
package org.apache.sling.servlets.resolver.external.it;

import static org.junit.Assert.assertTrue;

import java.util.regex.Pattern;

import org.junit.Test;

/** The idea is to run tests here which are independent from the existing
 *  test setup of the resourceresolver module. More recent dependencies,
 *  latest version of the pax exam tools, etc, while making as few changes
 *  as possible to the original module.
 */
public class TestSetupTest {
    @Test
    public void testTheTestSetup() {
        // We'll need the filename of the bundle to test
        final String filename = System.getProperty("bundle.filename");
        final Pattern regexp = Pattern.compile(".*org.apache.sling.servlets.resolver.*jar");
        assertTrue("Expecting " + filename + " to match " + regexp, regexp.matcher(filename).matches());
    }
}