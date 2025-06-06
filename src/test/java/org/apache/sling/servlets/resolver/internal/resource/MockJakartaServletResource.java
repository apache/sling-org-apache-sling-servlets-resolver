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
package org.apache.sling.servlets.resolver.internal.resource;

import jakarta.servlet.Servlet;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;

public class MockJakartaServletResource extends org.apache.sling.servlets.resolver.internal.resource.ServletResource {

    public static final String PROP_SERVLET = ":servlet";

    private final Servlet servlet;

    public MockJakartaServletResource(ResourceResolver resourceResolver, Servlet servlet, String path) {
        super(resourceResolver, servlet, path);
        this.servlet = servlet;
    }

    @Override
    public <T> T adaptTo(Class<T> type) {
        if (type == ValueMap.class) {
            ValueMapDecorator vm = (ValueMapDecorator) super.adaptTo(type);
            // add the servlet to the ValueMap so we don't lose track of it
            //  when resource objects are created during traversal
            vm.put(PROP_SERVLET, servlet);
            return type.cast(vm);
        }
        return super.adaptTo(type);
    }
}
