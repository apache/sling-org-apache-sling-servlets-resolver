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

import java.util.Arrays;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;

import org.apache.commons.lang3.ArrayUtils;
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

    // Used to indicate "accept only an empty set of selectors or extensions - should not be
    // a valid selector or extension to avoid collisions
    private static final String EMPTY_VALUE = ".EMPTY.";

    static class InvalidPropertyException extends RuntimeException {
        InvalidPropertyException(String reason) {
            super(reason);
        }
    }

    boolean accept(SlingHttpServletRequest request, Servlet servlet) {
        // Get OSGi service properties from the SlingServletConfig
        final ServletConfig rawCfg = servlet.getServletConfig();
        if(!(rawCfg instanceof SlingServletConfig)) {
            LOGGER.debug("Did not get a SlingServletConfig for {}", RequestUtil.getServletName(servlet));
            return true;
        }
        final SlingServletConfig config = (SlingServletConfig)rawCfg;
        final String servletName = RequestUtil.getServletName(servlet);

        // If the servlet properties have the "extpaths" option, check extension, selector etc.
        boolean accepted = true;
        final Object strictPaths = config.getServiceProperty(STRICT_PATHS_SERVICE_PROPERTY);
        if(strictPaths != null && Boolean.valueOf(strictPaths.toString())) {
            accepted = 
                accept(servletName, config, ServletResolverConstants.SLING_SERVLET_EXTENSIONS, true, request.getRequestPathInfo().getExtension())
                && accept(servletName, config, ServletResolverConstants.SLING_SERVLET_SELECTORS, true, request.getRequestPathInfo().getSelectors())
                && accept(servletName, config, ServletResolverConstants.SLING_SERVLET_METHODS, false, request.getMethod());
        }

        LOGGER.debug("accepted={} for {}", accepted, servletName);

        return accepted;
    }

    private boolean accept(String servletName, SlingServletConfig config, String servicePropertyKey, boolean emptyValueApplies, String ... requestValues) {
        final String [] propValues = toStringArray(config.getServiceProperty(servicePropertyKey));
        if(propValues == null) {
            LOGGER.debug("Property {} is null or empty, not checking that value for {}", servicePropertyKey, servletName);
            return true;
        }

        boolean accepted = false;
        if(propValues.length == 1 && EMPTY_VALUE.equals(propValues[0])) {
            // If supported for this service property, request value must be empty
            if(!emptyValueApplies) {
                throw new InvalidPropertyException("Special value " + EMPTY_VALUE
                + "  is not valid for the " + servicePropertyKey + " service property");
            } else {
                accepted = requestValues.length == 0 || (requestValues.length == 1 && requestValues[0] == null);
            }
        } else {
            // requestValues must match at least one value in propValue
            for(String rValue : requestValues) {
                for(String pValue : propValues) {
                    if(rValue != null && rValue.equals(pValue)) {
                        accepted = true;
                        break;
                    }
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
        } else if(value instanceof Object []) {
            final Object [] objArray = (Object[])value;
            return Arrays.copyOf(objArray, objArray.length, String[].class);
        }
        return null;
    }
}
