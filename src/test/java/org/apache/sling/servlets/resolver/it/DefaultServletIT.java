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
import static org.junit.Assert.assertNotNull;

import java.lang.reflect.Method;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.servlethelpers.MockSlingHttpServletRequest;
import org.apache.sling.servlethelpers.MockSlingHttpServletResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class DefaultServletIT extends ServletResolverTestSupport {

    @Inject
    private BundleContext bundleContext;

    private String getContent(String path) throws Exception {
        final ResourceResolver TODO_NEED_ONE = null;
        final MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(TODO_NEED_ONE);
        final MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        // Get SlingRequestProcessor.processRequest method and execute request
        // This module depends on an older version of the sling.engine module and I don't want
        // to change it just for these tests, so using reflection to get the processor, as we're
        // running with a more recent version of sling.engine in the pax exam environment
        final String slingRequestProcessorClassName = "org.apache.sling.engine.SlingRequestProcessor";
        final ServiceReference<?> ref = bundleContext.getServiceReference(slingRequestProcessorClassName);
        assertNotNull("Expecting service:" + slingRequestProcessorClassName, ref);

        final Object processor = bundleContext.getService(ref);
        try {
            // void processRequest(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse resource, ResourceResolver resourceResolver)
            final Method processMethod = processor.getClass().getMethod(
                "processRequest", 
                HttpServletRequest.class, HttpServletResponse.class, ResourceResolver.class);
            assertNotNull("Expecting processRequest method", processMethod);
            processMethod.invoke(processor, request, response, null);
        } finally {
            bundleContext.ungetService(ref);
        }

        return response.getOutputAsString();
    }

    @Test
    public void testDefaultServlet() throws Exception {
        final String TODO_SHOULD_NOT_BE_EMPTY = "";
        assertEquals(TODO_SHOULD_NOT_BE_EMPTY, getContent("/.json"));
    }

}