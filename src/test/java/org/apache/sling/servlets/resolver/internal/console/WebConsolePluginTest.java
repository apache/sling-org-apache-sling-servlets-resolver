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
package org.apache.sling.servlets.resolver.internal.console;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.servlets.resolver.internal.resolution.ResolutionCache;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import javax.json.Json;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class WebConsolePluginTest {
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private ResourceResolverFactory resourceResolverFactory;
    @Mock
    private ResolutionCache resolutionCache;
    @InjectMocks
    private WebConsolePlugin webConsolePlugin;

    private StringWriter stringWriter;
    private PrintWriter writer;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // mock http responses
        stringWriter = new StringWriter();
        writer = new PrintWriter(stringWriter);
        Mockito.when(response.getWriter()).thenReturn(writer);
    }

    @After
    public void closeUp() throws Exception {
        MockitoAnnotations.openMocks(this).close();
    }

    @Test
    public void printJsonFormat() throws Exception {
        // Mock resource resolver
        ResourceResolver resourceResolver = Mockito.mock(ResourceResolver.class);
        Resource resource = Mockito.mock(Resource.class);
        Servlet servlet = Mockito.mock(Servlet.class);
        Mockito.when(resource.adaptTo(Servlet.class)).thenReturn(servlet);
        Mockito.when(resourceResolver.resolve(Mockito.anyString())).thenReturn(resource);
        Mockito.when(resourceResolverFactory.getServiceResourceResolver(Mockito.anyMap())).thenReturn(resourceResolver);

        // Mock request calls
        Mockito.when(request.getParameter("url")).thenReturn("/test.1.json");
        Mockito.when(request.getParameter("method")).thenReturn("GET");

        // JSON output
        Mockito.when(request.getRequestURI()).thenReturn("/path/servletresolver.json");

        webConsolePlugin.service(request, response);

        Mockito.verify(response).setContentType("application/json");

        String jsonString = stringWriter.toString();
        String json = Json.createReader(new StringReader(jsonString)).readObject().toString();

        Map<String, Object> expectedJsonPaths = new HashMap(){{
            put("$.candidates", null);
            put("$.candidates.allowedServlets.length()", 1);
            put("$.candidates.deniedServlets.length()", 0);
            put("$.decomposedURL", null);
            put("$.decomposedURL.path", "/test");
            put("$.decomposedURL.extension", "json");
            put("$.decomposedURL.selectors", Arrays.asList("1"));
            put("$.decomposedURL.suffix", "");
            put("$.method", "GET");
        }};

        expectedJsonPaths.forEach((k, v) -> {
            if (v != null) {
                assertThat(json, hasJsonPath(k, equalTo(v)));
            } else {
                assertThat(json, hasJsonPath(k));
            }
        });
    }
}
