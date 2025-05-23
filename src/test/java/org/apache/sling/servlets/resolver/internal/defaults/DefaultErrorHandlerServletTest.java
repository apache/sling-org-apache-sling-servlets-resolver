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

import javax.servlet.http.HttpServletResponse;

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
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import org.apache.sling.api.SlingConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.builder.Builders;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.wrappers.JavaxToJakartaRequestWrapper;
import org.apache.sling.api.wrappers.JavaxToJakartaResponseWrapper;
import org.apache.sling.api.wrappers.SlingHttpServletRequestWrapper;
import org.apache.sling.api.wrappers.SlingHttpServletResponseWrapper;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * SLING-10021 test 'Accept' content-type handling in the default error handler servlet
 */
@SuppressWarnings("deprecation")
public class DefaultErrorHandlerServletTest {

    protected void assertJsonErrorResponse(SlingHttpServletRequest req) throws ServletException, IOException {
        MockErrorSlingHttpServletResponse res = new MockErrorSlingHttpServletResponse(
                Builders.newResponseBuilder().build(), false);

        DefaultErrorHandlerServlet errorServlet = new DefaultErrorHandlerServlet();
        errorServlet.init(new MockServletConfig());
        errorServlet.service(
                JavaxToJakartaRequestWrapper.toJakartaRequest(req),
                JavaxToJakartaResponseWrapper.toJakartaResponse(res));

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
    public void testJsonErrorResponse() throws IOException, ServletException {
        final Resource resource = Mockito.mock(Resource.class);
        final SlingHttpServletRequest request =
                Builders.newRequestBuilder(resource).build();

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
    public void testJsonErrorResponseWithClassExceptionTypeAttributeValue() throws IOException, ServletException {
        final Resource resource = Mockito.mock(Resource.class);
        final SlingHttpServletRequest request =
                Builders.newRequestBuilder(resource).build();

        // mock a request that accepts a json response
        SlingHttpServletRequest req = new MockErrorSlingHttpServletRequest(request, "application/json,*/*;q=0.9") {

            @Override
            public Object getAttribute(String name) {
                if (SlingConstants.ERROR_EXCEPTION_TYPE.equals(name)) {
                    return Exception.class;
                }
                return super.getAttribute(name);
            }
        };
        assertJsonErrorResponse(req);
    }

    @Test
    public void testHtmlErrorResponse() throws IOException, ServletException {
        final Resource resource = Mockito.mock(Resource.class);
        final SlingHttpServletRequest request =
                Builders.newRequestBuilder(resource).build();

        // mock a request that accepts an html response
        SlingHttpServletRequest req = new MockErrorSlingHttpServletRequest(
                request, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        MockErrorSlingHttpServletResponse res = new MockErrorSlingHttpServletResponse(
                Builders.newResponseBuilder().build(), false);

        DefaultErrorHandlerServlet errorServlet = new DefaultErrorHandlerServlet();
        errorServlet.init(new MockServletConfig());
        errorServlet.service(
                JavaxToJakartaRequestWrapper.toJakartaRequest(req),
                JavaxToJakartaResponseWrapper.toJakartaResponse(res));

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
    private static final class MockServletConfig implements ServletConfig {

        @Override
        public String getServletName() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServletContext getServletContext() {
            final ServletContext ctx = Mockito.mock(ServletContext.class);
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
    private static final class MockErrorSlingHttpServletResponse extends SlingHttpServletResponseWrapper {

        private PrintWriter writer;
        private StringWriter strWriter;
        private boolean committed;

        public MockErrorSlingHttpServletResponse(final SlingHttpServletResponse response, final boolean committed) {
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
    private static class MockErrorSlingHttpServletRequest extends SlingHttpServletRequestWrapper {
        private String accept;

        private MockErrorSlingHttpServletRequest(final SlingHttpServletRequest request, final String accept) {
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
            if (SlingConstants.ERROR_STATUS.equals(name)) {
                return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            } else if (SlingConstants.ERROR_MESSAGE.equals(name)) {
                return "Test Error Message";
            } else if (SlingConstants.ERROR_REQUEST_URI.equals(name)) {
                return "/testuri";
            } else if (SlingConstants.ERROR_SERVLET_NAME.equals(name)) {
                return "org.apache.sling.test.ServletName";
            } else if (SlingConstants.ERROR_EXCEPTION.equals(name)) {
                return new Exception("Test Exception");
            } else if (SlingConstants.ERROR_EXCEPTION_TYPE.equals(name)) {
                return Exception.class.getName();
            }
            return super.getAttribute(name);
        }
    }
}
