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
import java.util.List;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.sling.api.SlingConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.request.builder.Builders;
import org.apache.sling.api.request.builder.SlingHttpServletResponseResult;
import org.apache.sling.api.request.builder.SlingJakartaHttpServletResponseResult;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.wrappers.JavaxToJakartaRequestWrapper;
import org.apache.sling.api.wrappers.JavaxToJakartaResponseWrapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;

/**
 * Verifies empty error responses when neither HTML nor JSON is acceptable.
 */
@SuppressWarnings("deprecation")
@RunWith(Parameterized.class)
public class DefaultErrorHandlerEmptyResponseTest {

    @Parameters(name = "Accept={0}")
    public static List<Object[]> data() {
        return List.of(
                new Object[] {"text/plain"}, new Object[] {"application/xml"}, new Object[] {"*/*;q=0"}, new Object[] {
                    "text/*;q=0,application/*;q=0,*/*"
                });
    }

    private final String accept;
    private DefaultErrorHandlerServlet errorServlet;

    public DefaultErrorHandlerEmptyResponseTest(String accept) {
        this.accept = accept;
    }

    @Before
    public void setUp() throws ServletException {
        ServletContext context = Mockito.mock(ServletContext.class);
        Mockito.when(context.getServerInfo()).thenReturn("Test Server Info");
        ServletConfig config = Mockito.mock(ServletConfig.class);
        Mockito.when(config.getServletContext()).thenReturn(context);
        errorServlet = new DefaultErrorHandlerServlet();
        errorServlet.init(config);
    }

    /**
     * Returns an empty Jakarta response when Accept negotiation selects neither HTML nor JSON,
     * even if the response content type is already HTML. Sets the error status and zero content
     * length without obtaining a writer.
     */
    @Test
    public void testEmptyErrorResponse() throws IOException {
        SlingJakartaHttpServletRequest request = Mockito.spy(
                Builders.newRequestBuilder(Mockito.mock(Resource.class)).buildJakartaRequest());
        Mockito.when(request.getHeader("Accept")).thenReturn(accept);
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        SlingJakartaHttpServletResponseResult response =
                Mockito.spy(Builders.newResponseBuilder().buildJakartaResponseResult());
        response.setContentType("text/html");

        errorServlet.service(request, response);

        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, response.getStatus());
        assertEquals("", response.getOutputAsString());
        assertEquals(0, response.getContentLength());
        Mockito.verify(response, Mockito.never()).getWriter();
    }

    /**
     * Verifies the same empty-body, error-status, and content-length behavior through the
     * javax-to-Jakarta wrappers, without obtaining a response writer.
     */
    @Test
    public void testEmptyErrorResponseWithJavaxWrapper() throws IOException {
        SlingHttpServletRequest request = Mockito.spy(
                Builders.newRequestBuilder(Mockito.mock(Resource.class)).build());
        Mockito.when(request.getHeader("Accept")).thenReturn(accept);
        request.setAttribute(SlingConstants.ERROR_STATUS, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        SlingHttpServletResponseResult response =
                Mockito.spy(Builders.newResponseBuilder().build());
        response.setContentType("text/html");

        errorServlet.service(
                JavaxToJakartaRequestWrapper.toJakartaRequest(request),
                JavaxToJakartaResponseWrapper.toJakartaResponse(response));

        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, response.getStatus());
        assertEquals("", response.getOutputAsString());
        assertEquals(0, response.getContentLength());
        Mockito.verify(response, Mockito.never()).getWriter();
    }
}
