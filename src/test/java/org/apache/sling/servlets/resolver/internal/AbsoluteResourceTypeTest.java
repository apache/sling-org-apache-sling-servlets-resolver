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

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.request.RequestProgressTracker;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import javax.servlet.Servlet;

import org.apache.sling.api.resource.PersistenceException;
import org.osgi.framework.Bundle;
import org.apache.sling.servlets.resolver.internal.resource.MockServletResource;

public class AbsoluteResourceTypeTest extends SlingServletResolverTestBase {

  private static final String absolutePath = "/absolute/resource/type.servlet";

  @Override
  protected void defineTestServlets(Bundle bundle) {
    // Create a mock servlet for testing
    final Servlet testServlet = mock(Servlet.class);
    final Map<String, Object> properties = new HashMap<>();
    properties.put(MockServletResource.PROP_SERVLET, testServlet);

    // Register the servlet at an absolute path with .servlet suffix
    try {
      Resource root = mockResourceResolver.getResource("/");
      // Create intermediate directories
      Resource absolute = mockResourceResolver.create(root, "absolute", null);
      Resource resource = mockResourceResolver.create(absolute, "resource", null);
      // Create the resource with .servlet suffix so it becomes a MockServletResource
      mockResourceResolver.create(resource, "type.servlet", properties);
    } catch (PersistenceException e) {
      fail("Failed to create test servlet resource: " + e.getMessage());
    }
  }

  private Servlet resolveFromPath(String path) throws Exception {
    // Setup test data
    final SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
    final Resource resource = mock(Resource.class);
    final RequestProgressTracker tracker = mock(RequestProgressTracker.class);
    final RequestPathInfo pathInfo = mock(RequestPathInfo.class);

    // Mock the request
    when(request.getRequestProgressTracker()).thenReturn(tracker);
    when(request.getResource()).thenReturn(resource);
    when(request.getRequestPathInfo()).thenReturn(pathInfo);
    when(pathInfo.getExtension()).thenReturn("html");
    when(pathInfo.getSelectors()).thenReturn(new String[0]);
    when(resource.getResourceType()).thenReturn(absolutePath);

    // Set up the shared script resolver to use our mock resource resolver
    Field sharedScriptResolverField = servletResolver.getClass().getDeclaredField("sharedScriptResolver");
    sharedScriptResolverField.setAccessible(true);
    @SuppressWarnings("unchecked")
    AtomicReference<ResourceResolver> sharedScriptResolver = (AtomicReference<ResourceResolver>) sharedScriptResolverField
        .get(servletResolver);
    sharedScriptResolver.set(mockResourceResolver);

    // Use reflection to call the private resolveServletInternal method
    Method resolveServletInternalMethod = servletResolver.getClass().getDeclaredMethod(
        "resolveServletInternal",
        SlingHttpServletRequest.class,
        Resource.class,
        String.class,
        ResourceResolver.class);
    resolveServletInternalMethod.setAccessible(true);

    return (Servlet)resolveServletInternalMethod.invoke(
        servletResolver,
        request,
        resource,
        path,
        mockResourceResolver);
  }

  @Test
  public void testAbsolutePath() throws Exception {
    final Servlet s = resolveFromPath(absolutePath);
    assertNotNull("Expecting a Servlet for valid absolute path", s);
  }

  @Test
  public void testAbsolutePathHiddenByPredicate() throws Exception {
    final Predicate<String> hideAbsolutePath = path -> absolutePath.equals(path);
    final Field f = servletResolver.getClass().getDeclaredField("resourceHidingPredicate");
    f.setAccessible(true);
    f.set(servletResolver, hideAbsolutePath);
    try {
      final Servlet s = resolveFromPath(absolutePath);
      assertNull("Expecting null when hidden by our Predicate", s);
    } finally {
      f.set(servletResolver, null);
    }
  }

  @Test
  public void testNonExistingPath() throws Exception {
    final Servlet s = resolveFromPath("/does/not/exist");
    assertNull("Expecting null for non-existent absolute path", s);
  }

  @Test
  public void testRelativePath() throws Exception {
    final Servlet s = resolveFromPath("relative/path");
    assertNull("Expecting null for relative path", s);
  }
}
