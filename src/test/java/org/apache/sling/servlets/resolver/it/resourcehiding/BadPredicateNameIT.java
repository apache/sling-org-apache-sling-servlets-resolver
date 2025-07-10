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

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class BadPredicateNameIT extends ResourceHidingITBase {

    @Before
    public void setupPredicate() {
        registerPredicate(path -> true, "invalid.name.that.causes.the.predicate.to.be.ignored");
    }

    @After
    public void checkNothingHidden() {
        assertEquals(0, hiddenResourcesCount);
    }

    @Test
    public void testExtApresent() throws Exception {
        assertTestServlet("/." + EXT_A, EXT_A);
    }

    @Test
    public void testExtBpresent() throws Exception {
        assertTestServlet("/." + EXT_B, EXT_B);
    }

    @Test
    public void testSelApresent() throws Exception {
        assertTestServlet("/." + SEL_A + "." + EXT_A, SEL_A);
    }
}
