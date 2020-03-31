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
package org.apache.sling.scripting.bundle.tracker.internal;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.commons.compiler.source.JavaEscapeHelper;
import org.apache.sling.scripting.bundle.tracker.ResourceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.Bundle;
import org.osgi.service.component.annotations.Component;

@Component(
        service = BundledScriptFinder.class
)
public class BundledScriptFinder {

    private static final String NS_JAVAX_SCRIPT_CAPABILITY = "javax.script";
    private static final String SLASH = "/";
    private static final String DOT = ".";

    Executable getScript(Set<TypeProvider> providers) {
        for (TypeProvider provider : providers) {
            ServletCapability capability = provider.getServletCapability();
            for (String match : buildScriptMatches(capability.getResourceTypes(),
                    capability.getSelectors().toArray(new String[0]), capability.getMethod(), capability.getExtension())) {
                String scriptExtension = capability.getScriptExtension();
                String scriptEngineName = capability.getScriptEngineName();
                if (StringUtils.isNotEmpty(scriptExtension) && StringUtils.isNotEmpty(scriptEngineName)) {
                    Executable executable =
                            getExecutable(provider.getBundle(), provider.isPrecompiled(), match, scriptEngineName, scriptExtension);
                    if (executable != null) {
                        return executable;
                    }
                }
            }
        }
        return null;
    }

    Executable getScript(@NotNull Bundle bundle, boolean precompiled, @NotNull String path, @NotNull String scriptEngineName) {
        if (precompiled) {
            String className = JavaEscapeHelper.makeJavaPackage(path);
            try {
                Class<?> clazz = bundle.loadClass(className);
                return new PrecompiledScript(bundle, path, clazz, scriptEngineName);
            } catch (ClassNotFoundException ignored) {
                // do nothing here
            }
        } else {
            URL bundledScriptURL = bundle.getEntry(NS_JAVAX_SCRIPT_CAPABILITY + (path.startsWith("/") ? "" : SLASH) + path);
            if (bundledScriptURL != null) {
                return new Script(bundle, path, bundledScriptURL, scriptEngineName);
            }
        }
        return null;
    }

    @Nullable
    private Executable getExecutable(@NotNull Bundle bundle, boolean precompiled, @NotNull String match,
                                     @NotNull String scriptEngineName, @NotNull String scriptExtension) {
        String path = match + DOT + scriptExtension;
        return getScript(bundle, precompiled, path, scriptEngineName);
    }

    private List<String> buildScriptMatches(Set<ResourceType> resourceTypes, String[] selectors, String method, String extension) {
        List<String> matches = new ArrayList<>();
        for (ResourceType resourceType : resourceTypes) {
            if (selectors.length > 0) {
                for (int i = selectors.length - 1; i >= 0; i--) {
                    String base =
                            resourceType.getType() +
                                    (StringUtils.isNotEmpty(resourceType.getVersion()) ? SLASH + resourceType.getVersion() + SLASH :
                                            SLASH) +
                                    String.join(SLASH, Arrays.copyOf(selectors, i + 1));
                    if (StringUtils.isNotEmpty(extension)) {
                        if (StringUtils.isNotEmpty(method)) {
                            matches.add(base + DOT + extension + DOT + method);
                        }
                        matches.add(base + DOT + extension);
                    }
                    if (StringUtils.isNotEmpty(method)) {
                        matches.add(base + DOT + method);
                    }
                    matches.add(base);
                }
            }
            String base = resourceType.getType() +
                    (StringUtils.isNotEmpty(resourceType.getVersion()) ? SLASH + resourceType.getVersion() : StringUtils.EMPTY);

            if (StringUtils.isNotEmpty(extension)) {
                if (StringUtils.isNotEmpty(method)) {
                    matches.add(base + SLASH + resourceType.getResourceLabel() + DOT + extension + DOT + method);
                }
                matches.add(base + SLASH + resourceType.getResourceLabel() + DOT + extension);
            }
            if (StringUtils.isNotEmpty(method)) {
                matches.add(base + SLASH + resourceType.getResourceLabel() + DOT + method);
            }
            matches.add(base + SLASH + resourceType.getResourceLabel());
            if (StringUtils.isNotEmpty(method)) {
                matches.add(base + SLASH + method);
            }
            if (StringUtils.isNotEmpty(extension)) {
                matches.add(base + SLASH + extension);
            }
        }
        return Collections.unmodifiableList(matches);
    }
}
