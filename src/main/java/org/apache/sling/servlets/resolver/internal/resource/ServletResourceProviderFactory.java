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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.servlet.Servlet;
import org.apache.commons.io.FilenameUtils;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.servlets.HttpConstants;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.util.converter.Converters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.sling.api.servlets.ServletResolverConstants.SLING_SERVLET_EXTENSIONS;
import static org.apache.sling.api.servlets.ServletResolverConstants.SLING_SERVLET_METHODS;
import static org.apache.sling.api.servlets.ServletResolverConstants.SLING_SERVLET_NAME;
import static org.apache.sling.api.servlets.ServletResolverConstants.SLING_SERVLET_PATHS;
import static org.apache.sling.api.servlets.ServletResolverConstants.SLING_SERVLET_PREFIX;
import static org.apache.sling.api.servlets.ServletResolverConstants.SLING_SERVLET_RESOURCE_SUPER_TYPE;
import static org.apache.sling.api.servlets.ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES;
import static org.apache.sling.api.servlets.ServletResolverConstants.SLING_SERVLET_SELECTORS;
import static org.osgi.service.component.ComponentConstants.COMPONENT_NAME;

public class ServletResourceProviderFactory {

    /**
     * The extension appended to servlets to register into the resource tree to
     * simplify handling in the resolution process (value is ".servlet").
     */
    public static final String SERVLET_PATH_EXTENSION = ".servlet";

    private static final String[] DEFAULT_SERVLET_METHODS = {HttpConstants.METHOD_GET, HttpConstants.METHOD_HEAD};

    private static final String ALL_METHODS = "*";

    /** default log */
    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * The root path to use for servlets registered with relative paths.
     */
    private final String servletRoot;

    /**
     * The index of the search path to be used as servlet root path
     */
    private final int servletRootIndex;

    /**
     * The search paths
     */
    private final List<String> searchPath;

    static String ensureServletNameExtension(final String servletPath) {
        if (servletPath.endsWith(SERVLET_PATH_EXTENSION)) {
            return servletPath;
        }

        return servletPath.concat(SERVLET_PATH_EXTENSION);
    }

    /**
     * Constructor
     * @param servletRoot The default value for the servlet root
     */
    public ServletResourceProviderFactory(String servletRoot, final List<String> searchPath) {
        this.searchPath = searchPath;
        // check if servletRoot specifies a number
        boolean isNumber = false;
        int index = -1;
        if (!servletRoot.startsWith("/")) {
            try {
                index = Integer.valueOf(servletRoot);
                isNumber = true;
            } catch (NumberFormatException nfe) {
                // ignore
            }
        }
        if (!isNumber) {
            // ensure the root starts and ends with a slash
            if (!servletRoot.startsWith("/")) {
                servletRoot = "/".concat(servletRoot);
            }
            if (!servletRoot.endsWith("/")) {
                servletRoot = servletRoot.concat("/");
            }

            this.servletRoot = servletRoot;
            this.servletRootIndex = -1;
        } else {
            this.servletRoot = null;
            this.servletRootIndex = index;
        }
    }

    /**
     * Create a servlet resource provider for the servlet
     * @param ref The service reference for the servlet
     * @param servlet The servlet object itself
     * @return A servlet resource provider
     */
    public ServletResourceProvider create(final ServiceReference<Servlet> ref, final Servlet servlet) {

        final Set<String> pathSet = new HashSet<>();

        // check whether explicit paths are set
        addByPath(pathSet, ref);

        // now, we handle resource types, extensions and methods
        addByType(pathSet, ref);

        if (pathSet.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug(
                        "create({}): ServiceReference has no registration settings, ignoring",
                        getServiceReferenceInfo(ref));
            }
            return null;
        }

        if (log.isDebugEnabled()) {
            log.debug("create({}): Registering servlet for paths {}", getServiceReferenceInfo(ref), pathSet);
        }
        String resourceSuperType = Converters.standardConverter()
                .convert(ref.getProperty(SLING_SERVLET_RESOURCE_SUPER_TYPE))
                .to(String.class);
        Set<String> resourceSuperTypeMarkers = new HashSet<>();
        if (resourceSuperType != null
                && !resourceSuperType.isEmpty()
                && !ServletResource.DEFAULT_RESOURCE_SUPER_TYPE.equals(resourceSuperType)) {
            for (String rt : Converters.standardConverter()
                    .convert(ref.getProperty(SLING_SERVLET_RESOURCE_TYPES))
                    .to(String[].class)) {
                if (!rt.startsWith("/")) {
                    rt = getPrefix(ref).concat(ResourceUtil.resourceTypeToPath(rt));
                }
                resourceSuperTypeMarkers.add(rt);
                pathSet.add(rt);
            }
        }
        return new ServletResourceProvider(servlet, pathSet, resourceSuperTypeMarkers, resourceSuperType);
    }

    /**
     * Get the mount prefix.
     */
    private String getPrefix(final ServiceReference<Servlet> ref) {
        Object value = ref.getProperty(SLING_SERVLET_PREFIX);
        if (value == null) {
            if (this.servletRoot != null) {
                return this.servletRoot;
            }
            value = this.servletRootIndex;
        }
        int index = -1;
        if (value instanceof Number) {
            index = ((Number) value).intValue();
        } else {
            String s = value.toString();
            if (!s.startsWith("/")) {
                boolean isNumber = false;
                try {
                    index = Integer.valueOf(s);
                    isNumber = true;
                } catch (NumberFormatException nfe) {
                    // ignore
                }
                if (!isNumber) {
                    if (log.isDebugEnabled()) {
                        log.debug(
                                "getPrefix({}): Configuration property is ignored {}",
                                getServiceReferenceInfo(ref),
                                value);
                    }
                    if (this.servletRoot != null) {
                        return this.servletRoot;
                    }
                    index = this.servletRootIndex;
                }
            } else {
                return s;
            }
        }
        if (index == -1 || index >= this.searchPath.size()) {
            index = this.searchPath.size() - 1;
        }
        return this.searchPath.get(index);
    }

    /**
     * Add a servlet by path.
     * @param pathSet
     * @param ref
     */
    private void addByPath(Set<String> pathSet, ServiceReference<Servlet> ref) {
        String[] paths = Converters.standardConverter()
                .convert(ref.getProperty(SLING_SERVLET_PATHS))
                .to(String[].class);
        for (String path : paths) {
            if (!path.startsWith("/")) {
                path = getPrefix(ref).concat(path);
            }

            // add the unmodified path
            pathSet.add(path);

            String[] types = Converters.standardConverter()
                    .convert(ref.getProperty(SLING_SERVLET_RESOURCE_TYPES))
                    .to(String[].class);
            final String ext = FilenameUtils.getExtension(path);
            if ((types.length == 0) || ext == null || ext.isEmpty()) {
                // ensure we have another entry which has the .servlet ext. if there wasn't one to begin with
                // Radu says: this will make sure that scripts are equal to servlets in the resolution process
                pathSet.add(ensureServletNameExtension(path));
            }
        }
    }

    /**
     * Add a servlet by type
     * @param pathSet
     * @param ref
     */
    private void addByType(Set<String> pathSet, ServiceReference<Servlet> ref) {
        String[] types = Converters.standardConverter()
                .convert(ref.getProperty(SLING_SERVLET_RESOURCE_TYPES))
                .to(String[].class);
        String[] paths = Converters.standardConverter()
                .convert(ref.getProperty(SLING_SERVLET_PATHS))
                .to(String[].class);
        boolean hasPathRegistration = true;
        if (paths.length == 0) {
            hasPathRegistration = false;
        }
        if (types.length == 0) {
            if (log.isDebugEnabled()) {
                log.debug("addByType({}): no resource types declared", getServiceReferenceInfo(ref));
            }
            return;
        }

        // check for selectors
        String[] selectors = Converters.standardConverter()
                .convert(ref.getProperty(SLING_SERVLET_SELECTORS))
                .to(String[].class);
        if (selectors.length == 0) {
            selectors = new String[] {null};
        }

        // we have types and expect extensions and/or methods
        String[] extensions = Converters.standardConverter()
                .convert(ref.getProperty(SLING_SERVLET_EXTENSIONS))
                .to(String[].class);

        // handle the methods property specially (SLING-430)
        String[] methods = Converters.standardConverter()
                .convert(ref.getProperty(SLING_SERVLET_METHODS))
                .to(String[].class);
        if (methods.length == 0) {

            // SLING-512 only, set default methods if no extensions are declared
            if (extensions.length == 0 && !hasPathRegistration) {
                if (log.isDebugEnabled()) {
                    log.debug("addByType({}): No methods declared, assuming GET/HEAD", getServiceReferenceInfo(ref));
                }
                methods = DEFAULT_SERVLET_METHODS;
            }

        } else if (methods.length == 1 && ALL_METHODS.equals(methods[0])) {
            if (log.isDebugEnabled()) {
                log.debug("addByType({}): Assuming all methods for '*'", getServiceReferenceInfo(ref));
            }
            methods = null;
        }

        for (String type : types) {

            // ensure namespace prefixes are converted to slashes
            type = ResourceUtil.resourceTypeToPath(type);

            // make absolute if relative
            if (!type.startsWith("/")) {
                type = this.getPrefix(ref) + type;
            }

            // ensure trailing slash for full path building
            if (!type.endsWith("/")) {
                type += "/";
            }

            // add entries for each selector combined with each ext and method
            for (String selector : selectors) {

                String selPath = type;
                if (selector != null && selector.length() > 0) {
                    selPath += selector.replace('.', '/') + ".";
                }

                boolean pathAdded = false;
                if (extensions.length > 0) {
                    if (methods != null && methods.length > 0) {
                        // both methods and extensions declared
                        for (String ext : extensions) {
                            for (String method : methods) {
                                pathSet.add(selPath + ext + "." + method + SERVLET_PATH_EXTENSION);
                                pathAdded = true;
                            }
                        }
                    } else {
                        // only extensions declared
                        for (String ext : extensions) {
                            pathSet.add(selPath + ext + SERVLET_PATH_EXTENSION);
                            pathAdded = true;
                        }
                    }
                } else if (methods != null) {
                    // only methods declared
                    for (String method : methods) {
                        pathSet.add(selPath + method + SERVLET_PATH_EXTENSION);
                        pathAdded = true;
                    }
                }

                // if neither methods nor extensions were added
                if (!pathAdded && !hasPathRegistration) {
                    pathSet.add(selPath.substring(0, selPath.length() - 1) + SERVLET_PATH_EXTENSION);
                }
            }
        }
    }

    public static String getServiceReferenceInfo(final ServiceReference<Servlet> reference) {
        final StringBuilder sb = new StringBuilder("service ");
        sb.append(String.valueOf(reference.getProperty(Constants.SERVICE_ID)));
        final Object servletName = reference.getProperty(SLING_SERVLET_NAME);
        final Object pid = reference.getProperty(Constants.SERVICE_PID);
        Object componentName = reference.getProperty(COMPONENT_NAME);
        if (pid != null && pid.equals(componentName)) {
            componentName = null;
        }
        if (servletName != null || pid != null || componentName != null) {
            sb.append(" (");
            boolean needsComma = false;
            if (servletName != null) {
                sb.append("name=");
                sb.append(servletName);
                needsComma = true;
            }
            if (pid != null) {
                if (needsComma) {
                    sb.append(", ");
                }
                sb.append("pid=");
                sb.append(pid);
                needsComma = true;
            }
            if (componentName != null) {
                if (needsComma) {
                    sb.append(", ");
                }
                sb.append("component=");
                sb.append(componentName);
            }
            sb.append(")");
        }
        sb.append(" from ");
        final Bundle bundle = reference.getBundle();
        if (bundle == null) {
            sb.append("uninstalled bundle");
        } else {
            sb.append("bundle ");
            if (bundle.getSymbolicName() == null) {
                sb.append(String.valueOf(bundle.getBundleId()));
            } else {
                sb.append(bundle.getSymbolicName());
                sb.append(":");
                sb.append(bundle.getVersion());
                sb.append(" (");
                sb.append(String.valueOf(bundle.getBundleId()));
                sb.append(") ");
            }
        }
        final String[] ocs = (String[]) reference.getProperty("objectClass");
        if (ocs != null) {
            sb.append("[");
            for (int i = 0; i < ocs.length; i++) {
                sb.append(ocs[i]);
                if (i < ocs.length - 1) {
                    sb.append(", ");
                }
            }
            sb.append("]");
        }
        return sb.toString();
    }
}
