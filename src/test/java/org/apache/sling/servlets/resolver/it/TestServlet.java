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

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.framework.BundleContext;

public class TestServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final String name;
    private final Dictionary<String, Object> properties = new Hashtable<>();

    public static final String SERVED_BY_PREFIX = "SERVED_BY_";

    // Use a specific HTTP status to identify this servlet.
    // I've waited all my career to find a good use for status 418, and
    // interestingly it's not part of the HttpServletResponse constants.
    public static final int IM_A_TEAPOT = 418;

    public TestServlet(String name) {
        this.name = name;
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.sendError(IM_A_TEAPOT, SERVED_BY_PREFIX + name);
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        doGet(req, resp);
    }

    TestServlet with(String key, Object value) {
        properties.put(key, value);
        return this;
    }

    void register(BundleContext context) {
        context.registerService(Servlet.class.getName(), this, properties);
    }
}