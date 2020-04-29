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

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "Apache Sling Servlet/Script Resolver and Error Handler",
    description = "The Sling Servlet and Script Resolver has "
        + "multiple tasks: One it is used as the ServletResolver to select the Servlet "
        + "or Script to call to handle the request. Second it acts as the "
        + "SlingScriptResolver and finally it manages error handling by implementing "
        + "the ErrorHandler interface using the same algorithm to select error handling "
        + "servlets and scripts as is used to resolve request processing servlets and " + "scripts.")
public @interface ResolverConfig {

    String PID = "org.apache.sling.servlets.resolver.SlingServletResolver";

    /**
     * The default servlet root is the first search path (which is usually
     * /apps)
     */
    @AttributeDefinition(name = "Servlet Registration Root Path", description = "The default root path assumed when "
            + "registering a servlet whose servlet registration properties define a relative "
            + "resource type/path. It can either be a string starting with \"/\" (specifying a path prefix to be used) "
            + "or a number which specifies the resource resolver's search path entry index. The default value "
            + "is 0 (usually stands for \"/apps\" in the search paths). The number can be -1 which always "
            + "points to the last search path entry.")
    String servletresolver_servletRoot() default "0";

    /** The default cache size for the script resolution. */
    @AttributeDefinition(name = "Cache Size", description = "This property configures the size of the "
            + "cache used for script resolution. A value lower than 5 disables the cache.")
    int servletresolver_cacheSize() default 200;

    @AttributeDefinition(name = "Execution Paths", description = "The paths to search for executable scripts. If no path is configured "
            + "this is treated like the default (/ = root) which allows to execute all scripts. By configuring some "
            + "paths the execution of scripts can be limited. If a configured value ends with a slash, the whole sub tree "
            + "is allowed. Without a slash an exact matching script is allowed.")
    String[] servletresolver_paths() default "/";

    @AttributeDefinition(name = "Default Extensions", description = "The list of extensions for which the default behavior "
            + "will be used. This means that the last path segment of the resource type can be used as the script name.")
    String[] servletresolver_defaultExtensions() default "html";

    @AttributeDefinition(name = "Mount Providers", description = "Should servlets be mounted as resource providers?" +
        " If true (the default), servlets will be represented in the content tree using resource provider -" +
        " otherwise, servlets will be decorated back into the content tree using a decorator.")
    boolean servletresolver_mountProviders() default true;
}
