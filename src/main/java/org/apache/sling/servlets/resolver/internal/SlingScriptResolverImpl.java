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

import org.apache.sling.api.SlingException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.scripting.SlingScript;
import org.apache.sling.api.scripting.SlingScriptResolver;
import org.apache.sling.api.servlets.ServletResolver;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

/**
 * The <code>SlingServletResolver</code> has two functions: It resolves scripts
 * by implementing the {@link SlingScriptResolver} interface and it resolves a
 * servlet for a request by implementing the {@link ServletResolver} interface.
 *
 * The resolver uses an own session to find the scripts.
 * @deprecated The API is deprecated
 */
@Deprecated
@Component(service = { SlingScriptResolver.class },
           configurationPid = ResolverConfig.PID,
           property = {
                   Constants.SERVICE_DESCRIPTION + "=Apache Sling Script Resolver",
                   Constants.SERVICE_VENDOR + "=The Apache Software Foundation"
           })
public class SlingScriptResolverImpl
    implements SlingScriptResolver {

    /**
     * The allowed execution paths.
     */
    private String[] executionPaths;

    @Activate
    private void activate(final ResolverConfig config) {
        this.executionPaths = SlingServletResolver.getExecutionPaths(config.servletresolver_paths());
    }

    /**
     * @see org.apache.sling.api.scripting.SlingScriptResolver#findScript(org.apache.sling.api.resource.ResourceResolver, java.lang.String)
     */
    @Override
    public SlingScript findScript(final ResourceResolver resourceResolver, final String name)
    throws SlingException {

        // is the path absolute
        SlingScript script = null;
        if (name.startsWith("/")) {

            final String path = ResourceUtil.normalize(name);
            if ( SlingServletResolver.isPathAllowed(path, this.executionPaths) ) {
                final Resource resource = resourceResolver.getResource(path);
                if ( resource != null ) {
                    script = resource.adaptTo(SlingScript.class);
                }
            }
        } else {

            // relative script resolution against search path
            final String[] path = resourceResolver.getSearchPath();
            for (int i = 0; script == null && i < path.length; i++) {
                final String scriptPath = ResourceUtil.normalize(path[i] + name);
                if ( SlingServletResolver.isPathAllowed(scriptPath, this.executionPaths) ) {
                    final Resource resource = resourceResolver.getResource(scriptPath);
                    if (resource != null) {
                        script = resource.adaptTo(SlingScript.class);
                    }
                }
            }

        }

        // log result
        if (script != null) {
            SlingServletResolver.LOGGER.debug("findScript: Using script {} for {}", script.getScriptResource().getPath(), name);
        } else {
            SlingServletResolver.LOGGER.info("findScript: No script {} found in path", name);
        }

        // and finally return the script (or null)
        return script;
    }
}
