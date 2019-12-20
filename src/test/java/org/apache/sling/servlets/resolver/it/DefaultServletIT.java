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
package org.apache.sling.servlets.resolver.it;

import static org.junit.Assert.assertTrue;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.BundleContext;

/** Using the SlingRequestProcessor for testing requests would be
 *  better, but this module has a number of specific settings
 *  in its pom for the sling.engine module that I prefer not touching
 *  now as I'm just adding these tests.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class DefaultServletIT extends ServletResolverTestSupport {
    @Inject
    private BundleContext bundleContext;

    @Test
    public void testDefaultServlet() {
        // TODO for now this just tests the pax exam setup
        assertTrue(bundleContext.getBundles().length > 0);
    }

}