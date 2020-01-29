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
import javax.servlet.ServletConfig;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.engine.RequestUtil;
import org.apache.sling.servlets.resolver.internal.resource.SlingServletConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements the SLING-8110 extended selection mechanism for path-mounted
 * servlets, which can take the extension, selectors and HTTP method into account
 * if a specific service property is set to activate this mode.
 */
class PathBasedServletAcceptor {
    public static final Logger LOGGER = LoggerFactory.getLogger(PathBasedServletAcceptor.class);

    // TODO should be in ServletResolverConstants
    private static final String STRICT_PATHS_SERVICE_PROPERTY = "sling.servlet.paths.strict";

    boolean accept(SlingHttpServletRequest request, Servlet servlet) {
        // Get OSGi service properties from the SlingServletConfig
        final ServletConfig rawCfg = servlet.getServletConfig();
        if(!(rawCfg instanceof SlingServletConfig)) {
            LOGGER.error("Did not get a SlingServletConfig for {}", RequestUtil.getServletName(servlet));
            return true;
        }
        final SlingServletConfig config = (SlingServletConfig)rawCfg;
        final String servletName = RequestUtil.getServletName(servlet);

        // If the servlet properties have the "extpaths" option, check extension, selector etc.
        boolean accepted = true;
        final Object extpaths = config.getServiceProperty(STRICT_PATHS_SERVICE_PROPERTY);
        if(extpaths != null && Boolean.valueOf(extpaths.toString())) {
            accepted = 
                accept(servletName, config, ServletResolverConstants.SLING_SERVLET_EXTENSIONS, request.getRequestPathInfo().getExtension())
                && accept(servletName, config, ServletResolverConstants.SLING_SERVLET_SELECTORS, request.getRequestPathInfo().getSelectors())
                && accept(servletName, config, ServletResolverConstants.SLING_SERVLET_METHODS, request.getMethod());
        }

        LOGGER.debug("accepted={} for {}", accepted, servletName);

        return accepted;
    }

    private boolean accept(String servletName, SlingServletConfig config, String servicePropertyKey, String ... requestValues) {
        final String [] propValues = toStringArray(config.getServiceProperty(servicePropertyKey));
        if(propValues == null) {
            LOGGER.debug("Property {} is null or empty, not checking that value for {}", servicePropertyKey, servletName);
            return true;
        }

        // requestValues must match at least one value in propValue
        boolean accepted = false;
        for(String rValue : requestValues) {
            for(String pValue : propValues) {
                if(rValue != null && rValue.equals(pValue)) {
                    accepted = true;
                    break;
                }
            }
        }
        LOGGER.debug("accepted={} for property {} and servlet {}", accepted, servicePropertyKey, servletName);
        return accepted;
    }

    private static String [] toStringArray(final Object value) {
        if(value instanceof String) {
            return new String[] { (String)value };
        } else if(value instanceof String []) {
            return (String[]) value;
        }
        return null;
    }
}
