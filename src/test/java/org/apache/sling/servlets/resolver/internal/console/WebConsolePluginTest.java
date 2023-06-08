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
import org.apache.sling.servlets.resolver.internal.ResolverConfig;
import org.apache.sling.servlets.resolver.internal.resolution.ResolutionCache;
import org.hamcrest.CoreMatchers;
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
import static org.junit.Assert.assertEquals;

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
        ResolverConfig resolverConfig = Mockito.mock(ResolverConfig.class);
        Mockito.when(resolverConfig.servletresolver_defaultExtensions()).thenReturn(new String[]{"html"});
        Mockito.when(resolverConfig.servletresolver_paths()).thenReturn(new String[]{"/path"});
        webConsolePlugin.activate(resolverConfig);
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
    public void printJsonFormatFull() throws Exception {
        // Mock resource resolver
        ResourceResolver resourceResolver = Mockito.mock(ResourceResolver.class);
        Resource resource = Mockito.mock(Resource.class);
        Servlet servlet = Mockito.mock(Servlet.class);
        Mockito.when(resource.adaptTo(Servlet.class)).thenReturn(servlet);
        Mockito.when(resource.getPath()).thenReturn("/path");
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

        Map<String, Object> expectedJsonPaths = new HashMap<String, Object>(){{
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

    @Test
    public void printJsonFormatEmptyURL() throws Exception {
        // Mock resource resolver
        ResourceResolver resourceResolver = Mockito.mock(ResourceResolver.class);
        Resource resource = Mockito.mock(Resource.class);
        Mockito.when(resource.adaptTo(Servlet.class)).thenReturn(null);
        Mockito.when(resourceResolver.resolve(Mockito.anyString())).thenReturn(resource);
        Mockito.when(resourceResolverFactory.getServiceResourceResolver(Mockito.anyMap())).thenReturn(resourceResolver);

        // Mock request calls
        Mockito.when(request.getParameter("url")).thenReturn("");
        Mockito.when(request.getParameter("method")).thenReturn("GET");

        // JSON output
        Mockito.when(request.getRequestURI()).thenReturn("/path/servletresolver.json");

        webConsolePlugin.service(request, response);

        Mockito.verify(response).setContentType("application/json");

        String jsonString = stringWriter.toString();
        String json = Json.createReader(new StringReader(jsonString)).readObject().toString();

        Map<String, Object> expectedJsonPaths = new HashMap<String, Object>(){{
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

    @Test
    public void printJsonFormatDenyPaths() throws Exception {
        // Mock resource resolver
        ResourceResolver resourceResolver = Mockito.mock(ResourceResolver.class);
        Resource resource = Mockito.mock(Resource.class);
        Servlet servlet = Mockito.mock(Servlet.class);
        Mockito.when(resource.adaptTo(Servlet.class)).thenReturn(servlet);
        Mockito.when(resource.getPath()).thenReturn("/denied");
        Mockito.when(resourceResolver.resolve(Mockito.anyString())).thenReturn(resource);
        Mockito.when(resourceResolverFactory.getServiceResourceResolver(Mockito.anyMap())).thenReturn(resourceResolver);

        // Mock request calls
        Mockito.when(request.getParameter("url")).thenReturn("/denied");
        Mockito.when(request.getParameter("method")).thenReturn("GET");

        // JSON output
        Mockito.when(request.getRequestURI()).thenReturn("/path/servletresolver.json");

        webConsolePlugin.service(request, response);

        Mockito.verify(response).setContentType("application/json");

        String jsonString = stringWriter.toString();
        String json = Json.createReader(new StringReader(jsonString)).readObject().toString();

        Map<String, Object> expectedJsonPaths = new HashMap<String, Object>(){{
            put("$.candidates", null);
            put("$.candidates.allowedServlets.length()", 0);
            put("$.candidates.deniedServlets.length()", 1);
        }};

        expectedJsonPaths.forEach((k, v) -> {
            if (v != null) {
                assertThat(json, hasJsonPath(k, equalTo(v)));
            } else {
                assertThat(json, hasJsonPath(k));
            }
        });
    }

    @Test
    public void printHTMLFormatFull() throws Exception {
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

        // HTML output
        Mockito.when(request.getRequestURI()).thenReturn("/path/servletresolver");

        webConsolePlugin.service(request, response);

        String htmlString = stringWriter.toString();

        final String expectedInputHTML = "<tr class='content'>\n" +
                "<td class='content'>URL</td>\n" +
                "<td class='content' colspan='2'><input type='text' name='url' value='/test.1.json' class='input' " +
                "size='50'>\n" +
                "</td></tr>\n" +
                "</tr>\n" +
                "<tr class='content'>\n" +
                "<td class='content'>Method</td>\n" +
                "<td class='content' colspan='2'><select name='method'>\n" +
                "<option value='GET'>GET</option>\n" +
                "<option value='POST'>POST</option>\n" +
                "</select>\n" +
                "&nbsp;&nbsp;<input type='submit' value='Resolve' class='submit'>\n" +
                "</td></tr>".replace("\\n", System.lineSeparator());
        assertThat(htmlString, CoreMatchers.containsString(expectedInputHTML));

        final String expectedDecomposedURLHTML = "<tr class='content'>\n" +
                "<td class='content'>Decomposed URL</td>\n" +
                "<td class='content' colspan='2'><dl>\n" +
                "<dt>Path</dt>\n" +
                "<dd>\n" +
                "/test<br/><em>Note that in a real Sling request, the path might vary depending on the existence of " +
                "resources that partially match it. <br/>This utility does not take this into account and uses the " +
                "first dot to split between path and selectors/extension. <br/>As a workaround, you can replace dots " +
                "with underline characters, for example, when testing such a URL.</em></dd><dt>Selectors</dt>\n" +
                "<dd>\n" +
                "[1]</dd><dt>Extension</dt>\n" +
                "<dd>\n" +
                "json</dd></dl>\n" +
                "</dd><dt>Suffix</dt>\n" +
                "<dd>\n" +
                "null</dd></dl>\n" +
                "</td></tr>".replace("\\n", System.lineSeparator());
        assertThat(htmlString, CoreMatchers.containsString(expectedDecomposedURLHTML));

        final String expectedCandidatesHTML = "<tr class='content'>\n" +
                "<td class='content'>Candidates</td>\n" +
                "<td class='content' colspan='2'>Candidate servlets and scripts in order of preference for method " +
                "GET:<br/>\n" +
                "<ol class='servlets'>\n".replace("\\n", System.lineSeparator());
        assertThat(htmlString, CoreMatchers.containsString(expectedCandidatesHTML));
    }

    @Test
    public void printHTMLFormatEmptyURL() throws Exception {
        // Mock resource resolver
        ResourceResolver resourceResolver = Mockito.mock(ResourceResolver.class);
        Resource resource = Mockito.mock(Resource.class);
        Mockito.when(resource.adaptTo(Servlet.class)).thenReturn(null);
        Mockito.when(resourceResolver.resolve(Mockito.anyString())).thenReturn(resource);
        Mockito.when(resourceResolverFactory.getServiceResourceResolver(Mockito.anyMap())).thenReturn(resourceResolver);

        // Mock request calls
        Mockito.when(request.getParameter("url")).thenReturn("");
        Mockito.when(request.getParameter("method")).thenReturn("GET");

        // HTML output
        Mockito.when(request.getRequestURI()).thenReturn("/path/servletresolver");

        webConsolePlugin.service(request, response);

        String htmlString = stringWriter.toString();
        assertEquals("<form method='get'><table class='content' cellpadding='0' cellspacing='0' width='100%'>\n" +
                "<tr class='content'>\n" +
                "<th colspan='3' class='content container'>Servlet Resolver Test</th>\n" +
                "</tr>\n" +
                "<tr class='content'>\n" +
                "<td colspan='3' class='content'>To check which servlet is responsible for rendering a response, " +
                "enter a request path into the field and click &apos;Resolve&apos; to resolve it.</th>\n" +
                "</tr>\n" +
                "<tr class='content'>\n" +
                "<td class='content'>URL</td>\n" +
                "<td class='content' colspan='2'><input type='text' name='url' value='' class='input' size='50'>\n" +
                "</td></tr>\n" +
                "</tr>\n" +
                "<tr class='content'>\n" +
                "<td class='content'>Method</td>\n" +
                "<td class='content' colspan='2'><select name='method'>\n" +
                "<option value='GET'>GET</option>\n" +
                "<option value='POST'>POST</option>\n" +
                "</select>\n" +
                "&nbsp;&nbsp;<input type='submit' value='Resolve' class='submit'>\n" +
                "</td></tr>\n" +
                "</table>\n" +
                "</form>".replace("\\n", System.lineSeparator()), htmlString);
    }

    @Test
    public void printHTMLFormatDenyPaths() throws Exception {
        // Mock resource resolver
        ResourceResolver resourceResolver = Mockito.mock(ResourceResolver.class);
        Resource resource = Mockito.mock(Resource.class);
        Servlet servlet = Mockito.mock(Servlet.class);
        Mockito.when(resource.adaptTo(Servlet.class)).thenReturn(servlet);
        Mockito.when(resource.getPath()).thenReturn("/denied");
        Mockito.when(resourceResolver.resolve(Mockito.anyString())).thenReturn(resource);
        Mockito.when(resourceResolverFactory.getServiceResourceResolver(Mockito.anyMap())).thenReturn(resourceResolver);

        // Mock request calls
        Mockito.when(request.getParameter("url")).thenReturn("/denied");
        Mockito.when(request.getParameter("method")).thenReturn("GET");

        // HTML output
        Mockito.when(request.getRequestURI()).thenReturn("/path/servletresolver");
        webConsolePlugin.service(request, response);

        String htmlString = stringWriter.toString();
        final String expectedDeniedElement = "<ol class='servlets'>" + System.lineSeparator() +
                "<li><del>";
        assertThat(htmlString, CoreMatchers.containsString(expectedDeniedElement));
    }
}
