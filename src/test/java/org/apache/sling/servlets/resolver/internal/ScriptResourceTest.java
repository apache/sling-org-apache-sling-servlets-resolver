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
package org.apache.sling.servlets.resolver.internal;

import javax.servlet.Servlet;

import org.apache.sling.api.adapter.AdapterManager;
import org.apache.sling.api.adapter.SlingAdaptable;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceWrapper;
import org.apache.sling.api.scripting.SlingScript;
import org.apache.sling.servlets.resolver.internal.resource.ServletResource;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class ScriptResourceTest {

    @Test
    public void testAdaptToServletForAServletResource() {
        final String resourcePath = "/sling/test/test.html";
        ResourceResolver perThreadRR = mock(ResourceResolver.class);
        ResourceResolver sharedRR = mock(ResourceResolver.class);
        Resource resource = mock(Resource.class);
        when(resource.getPath()).thenReturn(resourcePath);

        Servlet servlet = mock(Servlet.class);
        ServletResource servletResource =
                spy(new ServletResource(mock(ResourceResolver.class), servlet, "/sling/test/test" + ".html"));

        when(perThreadRR.getResource(resourcePath)).thenReturn(servletResource);
        when(perThreadRR.isLive()).thenReturn(true);

        ScriptResource scriptResource = new ScriptResource(resource, () -> perThreadRR, sharedRR);

        AdapterManager adapterManager = mock(AdapterManager.class);
        when(adapterManager.getAdapter(scriptResource, Servlet.class)).thenReturn(mock(Servlet.class));
        SlingAdaptable.setAdapterManager(adapterManager);

        Servlet adaptedServlet = scriptResource.adaptTo(Servlet.class);
        assertEquals(servlet, adaptedServlet);
    }

    @Test
    public void testAdaptToServletForAWrappedServletResource() {
        final String resourcePath = "/sling/test/test.html";
        ResourceResolver perThreadRR = mock(ResourceResolver.class);
        ResourceResolver sharedRR = mock(ResourceResolver.class);
        Resource resource = mock(Resource.class);
        when(resource.getPath()).thenReturn(resourcePath);

        Servlet servlet = mock(Servlet.class);
        ServletResource servletResource =
                spy(new ServletResource(mock(ResourceResolver.class), servlet, "/sling/test/test" + ".html"));
        Resource wrappedResource = new ResourceWrapper(servletResource);

        when(perThreadRR.getResource(resourcePath)).thenReturn(wrappedResource);
        when(perThreadRR.isLive()).thenReturn(true);

        ScriptResource scriptResource = new ScriptResource(resource, () -> perThreadRR, sharedRR);

        AdapterManager adapterManager = mock(AdapterManager.class);
        when(adapterManager.getAdapter(scriptResource, Servlet.class)).thenReturn(mock(Servlet.class));
        SlingAdaptable.setAdapterManager(adapterManager);

        Servlet adaptedServlet = scriptResource.adaptTo(Servlet.class);
        assertEquals(servlet, adaptedServlet);
    }

    @Test
    public void testAdaptToServletForADoubleWrappedServletResource() {
        final String resourcePath = "/sling/test/test.html";
        ResourceResolver perThreadRR = mock(ResourceResolver.class);
        ResourceResolver sharedRR = mock(ResourceResolver.class);
        Resource resource = mock(Resource.class);
        when(resource.getPath()).thenReturn(resourcePath);

        Servlet servlet = mock(Servlet.class);
        ServletResource servletResource =
                new ServletResource(mock(ResourceResolver.class), servlet, "/sling/test/test.html");
        Resource wrappedResource = new ResourceWrapper(servletResource);
        Resource secondWrappedResource = new ResourceWrapper(wrappedResource);

        when(perThreadRR.getResource(resourcePath)).thenReturn(secondWrappedResource);
        when(perThreadRR.isLive()).thenReturn(true);

        ScriptResource scriptResource = new ScriptResource(resource, () -> perThreadRR, sharedRR);

        AdapterManager adapterManager = mock(AdapterManager.class);
        when(adapterManager.getAdapter(scriptResource, Servlet.class)).thenReturn(mock(Servlet.class));
        SlingAdaptable.setAdapterManager(adapterManager);

        Servlet adaptedServlet = scriptResource.adaptTo(Servlet.class);
        assertEquals(servlet, adaptedServlet);
    }

    @Test
    public void testAdaptToServletForANonServletResource() {
        final String resourcePath = "/sling/test/test.html";
        ResourceResolver perThreadRR = mock(ResourceResolver.class);
        ResourceResolver sharedRR = mock(ResourceResolver.class);
        Resource resource = mock(Resource.class);
        when(resource.getPath()).thenReturn(resourcePath);

        Servlet servlet = mock(Servlet.class);
        Resource wrappedResource = new ResourceWrapper(resource);

        when(perThreadRR.getResource(resourcePath)).thenReturn(wrappedResource);
        when(perThreadRR.isLive()).thenReturn(true);

        ScriptResource scriptResource = new ScriptResource(resource, () -> perThreadRR, sharedRR);

        AdapterManager adapterManager = mock(AdapterManager.class);
        when(adapterManager.getAdapter(scriptResource, Servlet.class)).thenReturn(servlet);
        SlingAdaptable.setAdapterManager(adapterManager);

        Servlet adaptedServlet = scriptResource.adaptTo(Servlet.class);
        assertEquals(servlet, adaptedServlet);
    }

    @Test
    public void testAdaptToSlingScript() {
        final String resourcePath = "/sling/test/test.html";
        ResourceResolver perThreadRR = mock(ResourceResolver.class);
        ResourceResolver sharedRR = mock(ResourceResolver.class);
        Resource resource = mock(Resource.class);
        when(resource.getPath()).thenReturn(resourcePath);

        SlingScript script = mock(SlingScript.class);

        Resource wrappedResource = new ResourceWrapper(resource);
        when(perThreadRR.getResource(resourcePath)).thenReturn(wrappedResource);
        when(perThreadRR.isLive()).thenReturn(true);

        ScriptResource scriptResource = new ScriptResource(resource, () -> perThreadRR, sharedRR);

        AdapterManager adapterManager = mock(AdapterManager.class);
        when(adapterManager.getAdapter(scriptResource, SlingScript.class)).thenReturn(script);
        SlingAdaptable.setAdapterManager(adapterManager);

        SlingScript adaptedScript = scriptResource.adaptTo(SlingScript.class);
        assertEquals(script, adaptedScript);
    }
}
