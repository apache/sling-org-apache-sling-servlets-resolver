/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 ~ Licensed to the Apache Software Foundation (ASF) under one
 ~ or more contributor license agreements.  See the NOTICE file
 ~ distributed with this work for additional information
 ~ regarding copyright ownership.  The ASF licenses this file
 ~ to you under the Apache License, Version 2.0 (the
 ~ "License"); you may not use this file except in compliance
 ~ with the License.  You may obtain a copy of the License at
 ~
 ~   http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing,
 ~ software distributed under the License is distributed on an
 ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 ~ KIND, either express or implied.  See the License for the
 ~ specific language governing permissions and limitations
 ~ under the License.
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
package org.apache.sling.servlets.resolver.internal.bundle;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.GenericServlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.sling.api.SlingConstants;
import org.apache.sling.api.SlingException;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.type.ResourceType;
import org.apache.sling.scripting.spi.bundle.BundledRenderUnit;
import org.apache.sling.scripting.spi.bundle.TypeProvider;
import org.jetbrains.annotations.NotNull;

public class BundledScriptServlet extends GenericServlet {
    private static final long serialVersionUID = 6320375093932073555L;
    private final BundledRenderUnit executable; // NOSONAR
    private final String servletInfo;
    private final Set<ResourceType> types; // NOSONAR


    public BundledScriptServlet(@NotNull Set<TypeProvider> wiredTypeProviders,
                         @NotNull BundledRenderUnit executable) {
        this.executable = executable;
        this.servletInfo = "Script " + executable.getPath();
        this.types = wiredTypeProviders.stream().map(typeProvider -> typeProvider.getBundledRenderUnitCapability().getResourceTypes()
        ).flatMap(Collection::stream).collect(Collectors.toSet());
    }

    @Override
    public String getServletInfo() {
        return servletInfo;
    }

    @Override
    public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
        if ((req instanceof SlingHttpServletRequest) && (res instanceof SlingHttpServletResponse)) {
            SlingHttpServletRequest request = (SlingHttpServletRequest) req;
            SlingHttpServletResponse response = (SlingHttpServletResponse) res;

            if (request.getAttribute(SlingConstants.ATTR_INCLUDE_SERVLET_PATH) == null) {
                final String contentType = request.getResponseContentType();
                if (contentType != null) {
                    response.setContentType(contentType);
                    if (contentType.startsWith("text/")) {
                        response.setCharacterEncoding("UTF-8");
                    }
                }
            }

            RequestWrapper requestWrapper = new RequestWrapper(request, types);
            try {
                executable.eval(requestWrapper, response);
            } catch (RuntimeException see) {

                // log in the request progress tracker
                logScriptError(request, see);
    
                throw see;
                
            } catch (Exception e) {
    
                // log in the request progress tracker
                logScriptError(request, e);
    
                throw new SlingException("Cannot get DefaultSlingScript: "
                    + e.getMessage(), e);
            }
        } else {
            throw new ServletException("Not a Sling HTTP request/response");
        }
    }

    /**
     * Logs the error caused by executing the script in the request progress
     * tracker.
     */
    private void logScriptError(SlingHttpServletRequest request,
            Throwable throwable) {
        String message = throwable.getMessage();
        if (message != null) {
            message = throwable.getMessage().replace('\n', '/');
        } else {
            message = throwable.toString();
        }
        request.getRequestProgressTracker().log("SCRIPT ERROR: {0}", message);
    }

    @NotNull
    public BundledRenderUnit getBundledRenderUnit() {
        return executable;
    }

    public InputStream getInputStream() {
        return executable.getInputStream();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + executable.getPath() + ")";
    }
}
