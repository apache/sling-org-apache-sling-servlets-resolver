/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.servlets.resolver.internal.defaults;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Enumeration;
import java.util.regex.Pattern;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingConstants;
import org.apache.sling.commons.testing.sling.MockSlingHttpServletRequest;
import org.apache.sling.commons.testing.sling.MockSlingHttpServletResponse;
import org.junit.Test;

/**
 * SLING-10021 test 'Accept' content-type handling in the default error handler servlet
 */
public class DefaultErrorHandlerServletTest {

    @Test
    public void testJsonErrorResponse() throws IOException, ServletException {
        // mock a request that accepts a json response
        MockSlingHttpServletRequest req = new MockErrorSlingHttpServletRequest("application/json,/;q=0.9");
        MockSlingHttpServletResponse res = new MockErrorSlingHttpServletResponse(false);

        DefaultErrorHandlerServlet errorServlet = new DefaultErrorHandlerServlet();
        errorServlet.init(new MockServletConfig());
        errorServlet.service(req, res);

        // verify we got json back
        assertEquals("application/json", res.getContentType());
        String responseOutput = res.getOutput().toString();

        // check the json content matches what would be sent from the DefaultErrorHandlingServlet
        try (Reader reader = new StringReader(responseOutput);
                JsonReader jsonReader = Json.createReader(reader)) {
            JsonObject jsonObj = jsonReader.readObject();
            assertEquals(500, jsonObj.getInt("status"));
            assertEquals("/testuri", jsonObj.getString("requestUri"));
            assertEquals("org.apache.sling.test.ServletName", jsonObj.getString("servletName"));
            assertEquals("Test Error Message", jsonObj.getString("message"));
        }

    }

    @Test
    public void testHtmlErrorResponse() throws IOException, ServletException {
        // mock a request that accepts an html response
        MockSlingHttpServletRequest req = new MockErrorSlingHttpServletRequest("text/html,application/xhtml+xml,application/xml;q=0.9,/;q=0.8");
        MockSlingHttpServletResponse res = new MockErrorSlingHttpServletResponse(false);

        DefaultErrorHandlerServlet errorServlet = new DefaultErrorHandlerServlet();
        errorServlet.init(new MockServletConfig());
        errorServlet.service(req, res);

        // verify we got json back
        assertEquals("text/html", res.getContentType());
        String responseOutput = res.getOutput().toString();

        // check the html content matches what would be sent from the DefaultErrorHandlingServlet
        Pattern regex = Pattern.compile("The requested URL \\/testuri resulted in an error in org.apache.sling.test.ServletName\\.", Pattern.MULTILINE);
        assertTrue("Expected error message", regex.matcher(responseOutput).find());
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
        public ServletContext getServletContext() {
            return new org.apache.sling.servlethelpers.MockServletContext() {
                @Override
                public String getServerInfo() {
                    return "Test Server Info";
                }
            };
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
    private static final class MockErrorSlingHttpServletResponse extends MockSlingHttpServletResponse {

        private PrintWriter writer;
        private StringWriter strWriter;
        private boolean committed;

        public MockErrorSlingHttpServletResponse(boolean committed) {
            super();
            this.committed = committed;
        }

        @Override
        public boolean isCommitted() {
            return this.committed;
        }

        @Override
        public void reset() {
            //no-op
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

        @Override
        public StringBuffer getOutput() {
            return strWriter.getBuffer();
        }

    }

    /**
     * Mock impl to simulate an error request
     */
    private static final class MockErrorSlingHttpServletRequest extends MockSlingHttpServletRequest {
        private String accept;

        private MockErrorSlingHttpServletRequest(String accept) {
            super(null, null, null, null, null);
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
            }
            return super.getAttribute(name);
        }

    }


}
