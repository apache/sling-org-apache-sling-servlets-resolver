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

import static org.junit.Assert.assertEquals;

import java.util.Hashtable;
import java.util.UUID;
import java.util.function.Predicate;

import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

// TODO test with absolute script/servlet paths
// TODO test that scripts are also covered by the exclusion

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class FilteredServletSelectionIT extends ServletResolverTestSupport {

    private int hiddenResourceCount = 0;
    private final static String isHidden = "IS_HIDDEN_" + UUID.randomUUID();
    private final static String notHidden = "NOT_HIDDEN_" + UUID.randomUUID();

    @Before
    public void setupTestServlets() throws Exception {
        // Register two servlets differing only in extensions,
        // to verify that one of them is hidden
        final TestServlet ts = new TestServlet("HidingTestServlet")
        .with(P_RESOURCE_TYPES, RT_DEFAULT)
        .with(P_METHODS, M_GET);

        ts.with(P_EXTENSIONS, notHidden).register(bundleContext);
        ts.with(P_EXTENSIONS, isHidden).register(bundleContext);

        // Register a predicate that hides one of the servlet resources
        final Predicate<String> hidingPredicate = new Predicate<String>() {
          @Override
          public boolean test(String t) {
              if(t.contains(isHidden)) {
                  hiddenResourceCount++;
                  return true;
              }
              return false;
          }
        };
        final Hashtable<String,String> props = new Hashtable<>();
        props.put("name","sling.servlet.resolver.resource.hiding");
        bundleContext.registerService(Predicate.class.getName(), hidingPredicate, props);
    }

    @Test
    public void testFilteredServlet() throws Exception {  
        assertTestServlet("/." + notHidden, "HidingTestServlet");
        assertEquals(0, hiddenResourceCount);
        assertTestServlet("/." + isHidden, HttpServletResponse.SC_NOT_FOUND);
        assertEquals(1, hiddenResourceCount);
    }
}