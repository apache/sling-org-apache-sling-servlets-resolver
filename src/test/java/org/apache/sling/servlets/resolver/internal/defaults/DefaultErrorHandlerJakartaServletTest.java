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
package org.apache.sling.servlets.resolver.internal.defaults;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Enumeration;
import java.util.regex.Pattern;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.request.builder.Builders;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.wrappers.JakartaToJavaxRequestWrapper;
import org.apache.sling.api.wrappers.JakartaToJavaxResponseWrapper;
import org.apache.sling.api.wrappers.SlingJakartaHttpServletRequestWrapper;
import org.apache.sling.api.wrappers.SlingJakartaHttpServletResponseWrapper;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * SLING-10021 test 'Accept' content-type handling in the default error handler servlet
 */
public class DefaultErrorHandlerJakartaServletTest {

    protected void assertJsonErrorResponse(SlingJakartaHttpServletRequest req)
            throws javax.servlet.ServletException, IOException {
        MockErrorSlingHttpServletResponse res = new MockErrorSlingHttpServletResponse(
                Builders.newResponseBuilder().buildJakartaResponseResult(), false);

        DefaultErrorHandlerServlet errorServlet = new DefaultErrorHandlerServlet();
        errorServlet.init(new MockServletConfig());
        errorServlet.service(
                JakartaToJavaxRequestWrapper.toJavaxRequest(req), JakartaToJavaxResponseWrapper.toJavaxResponse(res));

        // verify we got json back
        assertEquals("application/json;charset=UTF-8", res.getContentType());
        String responseOutput = res.getOutput().toString();

        // check the json content matches what would be sent from the DefaultErrorHandlingServlet
        try (Reader reader = new StringReader(responseOutput);
                JsonReader jsonReader = Json.createReader(reader)) {
            JsonObject jsonObj = jsonReader.readObject();
            assertEquals(500, jsonObj.getInt("status"));
            assertEquals("/testuri", jsonObj.getString("requestUri"));
            assertEquals("org.apache.sling.test.ServletName", jsonObj.getString("servletName"));
            assertEquals("Test Error Message", jsonObj.getString("message"));
            assertTrue(jsonObj.getString("exception").contains("Test Exception"));
            assertEquals(Exception.class.getName(), jsonObj.getString("exceptionType"));
        }
    }

    @Test
    public void testJsonErrorResponse() throws IOException, javax.servlet.ServletException {
        final Resource resource = Mockito.mock(Resource.class);
        final SlingJakartaHttpServletRequest request =
                Builders.newRequestBuilder(resource).buildJakartaRequest();

        // mock a request that accepts a json response
        MockErrorSlingHttpServletRequest req =
                new MockErrorSlingHttpServletRequest(request, "application/json,*/*;q=0.9");
        assertJsonErrorResponse(req);
    }

    /**
     * SLING-10615 - Verify that if the SlingConstants.ERROR_EXCEPTION_TYPE happens to be a
     * Class object instead of a String, it still produces a valid error response
     */
    @Test
    public void testJsonErrorResponseWithClassExceptionTypeAttributeValue()
            throws IOException, javax.servlet.ServletException {
        final Resource resource = Mockito.mock(Resource.class);
        final SlingJakartaHttpServletRequest request =
                Builders.newRequestBuilder(resource).buildJakartaRequest();

        // mock a request that accepts a json response
        SlingJakartaHttpServletRequest req =
                new MockErrorSlingHttpServletRequest(request, "application/json,*/*;q=0.9") {

                    @Override
                    public Object getAttribute(String name) {
                        if (RequestDispatcher.ERROR_EXCEPTION_TYPE.equals(name)) {
                            return Exception.class;
                        }
                        return super.getAttribute(name);
                    }
                };
        assertJsonErrorResponse(req);
    }

    @Test
    public void testHtmlErrorResponse() throws IOException, javax.servlet.ServletException {
        final Resource resource = Mockito.mock(Resource.class);
        final SlingJakartaHttpServletRequest request =
                Builders.newRequestBuilder(resource).buildJakartaRequest();

        // mock a request that accepts an html response
        SlingJakartaHttpServletRequest req = new MockErrorSlingHttpServletRequest(
                request, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        MockErrorSlingHttpServletResponse res = new MockErrorSlingHttpServletResponse(
                Builders.newResponseBuilder().buildJakartaResponseResult(), false);

        DefaultErrorHandlerServlet errorServlet = new DefaultErrorHandlerServlet();
        errorServlet.init(new MockServletConfig());
        errorServlet.service(
                JakartaToJavaxRequestWrapper.toJavaxRequest(req), JakartaToJavaxResponseWrapper.toJavaxResponse(res));

        // verify we got html back
        assertEquals("text/html;charset=UTF-8", res.getContentType());
        String responseOutput = res.getOutput().toString();

        // check the html content matches what would be sent from the DefaultErrorHandlingServlet
        Pattern regex = Pattern.compile(
                "The requested URL \\/testuri resulted in an error in org.apache.sling.test.ServletName\\.",
                Pattern.MULTILINE);
        assertTrue("Expected error message", regex.matcher(responseOutput).find());
        assertTrue(responseOutput.contains("Test Exception"));
    }

    /**
     * Mock impl to simulate enough of a servlet context to satisfy what is used
     * by DefaultErrorHandlerServlet
     */
    private static final class MockServletConfig implements javax.servlet.ServletConfig {

        @Override
        public String getServletName() {
            throw new UnsupportedOperationException();
        }

        @Override
        public javax.servlet.ServletContext getServletContext() {
            final javax.servlet.ServletContext ctx = Mockito.mock(javax.servlet.ServletContext.class);
            Mockito.when(ctx.getServerInfo()).thenReturn("Test Server Info");
            return ctx;
        }

        @Override
        public String getInitParameter(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Enumeration<String> getInitParameterNames() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Mock impl to simulate an error response
     */
    private static final class MockErrorSlingHttpServletResponse extends SlingJakartaHttpServletResponseWrapper {

        private PrintWriter writer;
        private StringWriter strWriter;
        private boolean committed;

        public MockErrorSlingHttpServletResponse(
                final SlingJakartaHttpServletResponse response, final boolean committed) {
            super(response);
            this.committed = committed;
        }

        @Override
        public boolean isCommitted() {
            return this.committed;
        }

        @Override
        public void reset() {
            // no-op
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            // the super MockWriter throws away the buffer during
            //   the flush() calls, so we can't use it to verify
            //   content in the response.
            if (this.writer == null) {
                strWriter = new StringWriter();
                this.strWriter.getBuffer();
                this.writer = new PrintWriter(strWriter);
            }
            return this.writer;
        }

        public StringBuffer getOutput() {
            return strWriter.getBuffer();
        }
    }

    /**
     * Mock impl to simulate an error request
     */
    private static class MockErrorSlingHttpServletRequest extends SlingJakartaHttpServletRequestWrapper {
        private String accept;

        private MockErrorSlingHttpServletRequest(final SlingJakartaHttpServletRequest request, final String accept) {
            super(request);
            this.accept = accept;
        }

        @Override
        public String getHeader(String name) {
            if ("Accept".equals(name)) {
                return accept;
            }
            return super.getHeader(name);
        }

        @Override
        public Object getAttribute(String name) {
            if (RequestDispatcher.ERROR_STATUS_CODE.equals(name)) {
                return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            } else if (RequestDispatcher.ERROR_MESSAGE.equals(name)) {
                return "Test Error Message";
            } else if (RequestDispatcher.ERROR_REQUEST_URI.equals(name)) {
                return "/testuri";
            } else if (RequestDispatcher.ERROR_SERVLET_NAME.equals(name)) {
                return "org.apache.sling.test.ServletName";
            } else if (RequestDispatcher.ERROR_EXCEPTION.equals(name)) {
                return new Exception("Test Exception");
            } else if (RequestDispatcher.ERROR_EXCEPTION_TYPE.equals(name)) {
                return Exception.class.getName();
            }
            return super.getAttribute(name);
        }
    }
}
