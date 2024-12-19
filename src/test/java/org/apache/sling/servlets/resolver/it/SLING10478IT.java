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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;

import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;
import org.apache.sling.servlethelpers.MockSlingHttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for SLING-10478 - SlingServletResolver#handleError should not attempt to flush/close
 * an already closed response writer
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class SLING10478IT extends ServletResolverTestSupport {
    public static final String P_PREFIX = "sling.servlet.prefix";

    @Before
    public void setupTestServlets() throws Exception {
        new TestServlet("CloseWriterErrorHandler") {
            private static final long serialVersionUID = 5467958588168607027L;

            @Override
            public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.setCharacterEncoding("UTF-8");
                resp.setContentType("application/json");
                // closing the JsonGenerator also closes the response writer
                try (JsonGenerator generator = Json.createGenerator(resp.getWriter())) {
                    generator.writeStartObject();
                    generator.write("hello", "error");
                    generator.writeEnd();
                }
            }
        }.with(P_RESOURCE_TYPES, "sling/servlet/errorhandler")
                .with(P_EXTENSIONS, "close")
                .with(P_METHODS, "default")
                .with(P_PREFIX, -1)
                .register(bundleContext);

        new TestServlet("NoCloseWriterErrorHandler") {
            private static final long serialVersionUID = 5467958588168607027L;

            @Override
            public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.setCharacterEncoding("UTF-8");
                resp.setContentType("text/plain");

                resp.getWriter().print("hello error");

                // response writer remains unclosed
            }
        }.with(P_RESOURCE_TYPES, "sling/servlet/errorhandler")
                .with(P_EXTENSIONS, "noclose")
                .with(P_METHODS, "default")
                .with(P_PREFIX, -1)
                .register(bundleContext);
    }

    @Override
    protected MockSlingHttpServletResponse createMockSlingHttpServletResponse() {
        return new HandleErrorMockSlingHttpServletResponse();
    }

    /**
     * Test an error handling servlet that closes the response writer
     */
    @Test
    public void testCloseWriterErrorHandlerServlet() throws Exception {
        checkErrorHandlerServlet("/not_real_path.close");
    }

    /**
     * Test an error handling servlet that does not close the response writer
     */
    @Test
    public void testNoCloseWriterErrorHandlerServlet() throws Exception {
        checkErrorHandlerServlet("/not_real_path.noclose");
    }

    void checkErrorHandlerServlet(String path) throws Exception, InvocationTargetException {
        try {
            final String output = executeRequest(M_GET, path, HttpServletResponse.SC_NOT_FOUND)
                    .getOutputAsString();
            assertNotNull(output);
            assertTrue(output, output.contains("hello"));
        } catch (InvocationTargetException t) {
            Throwable cause = t.getCause();
            if (cause instanceof IllegalStateException && "Writer Already Closed".equals(cause.getMessage())) {
                fail("Did not expect a \"Writer Already Closed\" exception");
            } else {
                throw t;
            }
        }
    }

    /**
     * Subclass to simulate what the SlingHttpServletResponseImpl writer does
     */
    private static final class HandleErrorMockSlingHttpServletResponse extends MockSlingHttpServletResponse {
        private HandleErrorResponseWriter writer = null;

        @Override
        public PrintWriter getWriter() {
            if (writer == null) {
                writer = new HandleErrorResponseWriter(super.getWriter());
            }
            return writer;
        }

        @Override
        public void flushBuffer() {
            if (!writer.isOpen()) {
                throw new IllegalStateException("Writer Already Closed");
            }
            super.flushBuffer();
        }
    }

    /**
     * Subclass to simulate what the SlingHttpServletResponseImpl writer does
     */
    private static final class HandleErrorResponseWriter extends PrintWriter {

        private boolean open = true;

        HandleErrorResponseWriter(Writer out) {
            super(out);
        }

        @Override
        public void close() {
            this.open = false;
            super.close();
        }

        public boolean isOpen() {
            return open;
        }
    }
}
