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

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.request.RequestUtil;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.servlets.resolver.internal.resource.JavaxSlingServletConfig;
import org.apache.sling.servlets.resolver.internal.resource.SlingServletConfig;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements the SLING-8110 extended selection mechanism for path-mounted
 * servlets, which can take the extension, selectors and HTTP method into account
 * if a specific service property is set to activate this mode.
 */
class PathBasedServletAcceptor {
    public static final Logger LOGGER = LoggerFactory.getLogger(PathBasedServletAcceptor.class);

    // Used to indicate "accept only an empty set of selectors or extensions - should not be
    // a valid selector or extension to avoid collisions
    private static final String EMPTY_VALUE = ".EMPTY.";

    private static final String[] EMPTY_STRINGS = new String[0];

    static class InvalidPropertyException extends RuntimeException {
        private static final long serialVersionUID = -119036154771528511L;

        InvalidPropertyException(String reason) {
            super(reason);
        }
    }

    SlingServletConfig getSlingServletConfig(final ServletConfig cfg, final Servlet servlet) {
        if (cfg instanceof SlingServletConfig) {
            return (SlingServletConfig) cfg;
        }
        final javax.servlet.Servlet s;
        if (servlet instanceof ServletWrapperUtil.JakartaScriptOptingServletWrapper) {
            s = ((ServletWrapperUtil.JakartaScriptOptingServletWrapper) servlet).servlet;
        } else if (servlet instanceof ServletWrapperUtil.JakartaScriptServletWrapper) {
            s = ((ServletWrapperUtil.JakartaScriptServletWrapper) servlet).servlet;
        } else {
            s = null;
        }
        if (s != null && s.getServletConfig() instanceof JavaxSlingServletConfig) {
            return ((JavaxSlingServletConfig) s.getServletConfig()).getSlingServletConfig();
        }
        return null;
    }

    boolean accept(SlingJakartaHttpServletRequest request, Servlet servlet) {
        final String servletName = RequestUtil.getServletName(servlet);
        // Get OSGi service properties from the SlingServletConfig
        final SlingServletConfig config = getSlingServletConfig(servlet.getServletConfig(), servlet);
        if (config == null) {
            LOGGER.debug("Did not get a SlingServletConfig for {}", servletName);
            return true;
        }

        // If the servlet properties have the "extpaths" option, check extension, selector etc.
        boolean accepted = true;
        final Object strictPaths = config.getServiceProperty(ServletResolverConstants.SLING_SERVLET_PATHS_STRICT);
        if (strictPaths != null && Boolean.valueOf(strictPaths.toString())) {
            accepted = accept(
                            servletName,
                            config,
                            ServletResolverConstants.SLING_SERVLET_EXTENSIONS,
                            true,
                            request.getRequestPathInfo().getExtension())
                    && accept(
                            servletName,
                            config,
                            ServletResolverConstants.SLING_SERVLET_SELECTORS,
                            true,
                            request.getRequestPathInfo().getSelectors())
                    && accept(
                            servletName,
                            config,
                            ServletResolverConstants.SLING_SERVLET_METHODS,
                            false,
                            request.getMethod());
        }

        LOGGER.debug("accepted={} for {}", accepted, servletName);

        return accepted;
    }

    private boolean accept(
            String servletName,
            SlingServletConfig config,
            String servicePropertyKey,
            boolean emptyValueApplies,
            String... requestValues) {
        final String[] propValues = toStringArray(config.getServiceProperty(servicePropertyKey));
        if (propValues.length == 0) {
            LOGGER.debug(
                    "Property {} is null or empty, not checking that value for {}", servicePropertyKey, servletName);
            return true;
        }

        boolean accepted = false;
        if (propValues.length == 1 && EMPTY_VALUE.equals(propValues[0])) {
            // If supported for this service property, request value must be empty
            if (!emptyValueApplies) {
                throw new InvalidPropertyException("Special value " + EMPTY_VALUE + "  is not valid for the "
                        + servicePropertyKey + " service property");
            } else {
                accepted = requestValues.length == 0 || (requestValues.length == 1 && requestValues[0] == null);
            }
        } else {
            // requestValues must match at least one value in propValue
            for (String rValue : requestValues) {
                for (String pValue : propValues) {
                    if (rValue != null && rValue.equals(pValue)) {
                        accepted = true;
                        break;
                    }
                }
            }
        }

        LOGGER.debug("accepted={} for property {} and servlet {}", accepted, servicePropertyKey, servletName);
        return accepted;
    }

    private static @NotNull String[] toStringArray(final Object value) {
        if (value instanceof String) {
            return new String[] {(String) value};
        } else if (value instanceof String[]) {
            return (String[]) value;
        } else if (value instanceof Object[]) {
            final Object[] objArray = (Object[]) value;
            return Arrays.copyOf(objArray, objArray.length, String[].class);
        }
        return EMPTY_STRINGS;
    }
}
