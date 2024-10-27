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

import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.NonExistingResource;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <code>DefaultServlet</code> is a very simple default resource handler.
 * <p>
 * The default servlet is not registered to handle any concrete resource type.
 * Rather it is used internally on demand.
 */
public class DefaultServlet extends SlingSafeMethodsServlet {

    private static final long serialVersionUID = 3806788918045433043L;
    
    private static final Logger LOG = LoggerFactory.getLogger(DefaultServlet.class);

    @Override
    protected void doGet(SlingHttpServletRequest request,
            SlingHttpServletResponse response) throws IOException {

        Resource resource = request.getResource();

        // cannot handle the request for missing resources
        if (resource instanceof NonExistingResource) {
            String msg = String.format("Resource not found at path %s", resource.getPath());
            LOG.error(msg);
            response.sendError(HttpServletResponse.SC_NOT_FOUND,msg);
        } else {
            String msg = String.format("Cannot find servlet to handle resource %s", resource.getPath());
            LOG.error(msg);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,msg);
        }
    }

}
