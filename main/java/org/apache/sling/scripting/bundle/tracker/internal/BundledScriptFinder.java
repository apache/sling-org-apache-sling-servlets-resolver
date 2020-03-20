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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.commons.compiler.source.JavaEscapeHelper;
import org.apache.sling.scripting.bundle.tracker.ResourceType;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(
        service = BundledScriptFinder.class
)
public class BundledScriptFinder {

    private static final String NS_JAVAX_SCRIPT_CAPABILITY = "javax.script";
    private static final String SLASH = "/";
    private static final String DOT = ".";

    @Reference
    private ScriptEngineManager scriptEngineManager;

    Executable getScript(SlingHttpServletRequest request, LinkedHashSet<TypeProvider> typeProviders, boolean precompiledScripts) {
        List<String> scriptMatches;
        for (TypeProvider provider : typeProviders) {
            scriptMatches = buildScriptMatches(request, provider.getResourceTypes());
            String scriptEngineName = getScriptEngineName(request, provider);
            if (StringUtils.isNotEmpty(scriptEngineName)) {
                ScriptEngine scriptEngine = scriptEngineManager.getEngineByName(scriptEngineName);
                if (scriptEngine != null) {
                    for (String match : scriptMatches) {
                        URL bundledScriptURL;
                        List<String> scriptEngineExtensions = getScriptEngineExtensions(scriptEngineName);
                        for (String scriptEngineExtension : scriptEngineExtensions) {
                            if (precompiledScripts) {
                                String className = JavaEscapeHelper.makeJavaPackage(match + DOT + scriptEngineExtension);
                                try {
                                    Class clazz = provider.getBundle().loadClass(className);
                                    return new PrecompiledScript(provider.getBundle(), scriptEngine,
                                            clazz.getDeclaredConstructor().newInstance());
                                } catch (ClassNotFoundException e) {
                                    // do nothing here
                                } catch (Exception e) {
                                    throw new IllegalStateException("Cannot correctly instantiate class " + className + ".");
                                }
                            } else {
                                bundledScriptURL =
                                        provider.getBundle()
                                                .getEntry(NS_JAVAX_SCRIPT_CAPABILITY + SLASH + match + DOT + scriptEngineExtension);
                                if (bundledScriptURL != null) {
                                    return new Script(provider.getBundle(), bundledScriptURL, scriptEngine);
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private List<String> buildScriptMatches(SlingHttpServletRequest request, Set<ResourceType> resourceTypes) {
        List<String> matches = new ArrayList<>();
        String method = request.getMethod();
        String extension = request.getRequestPathInfo().getExtension();
        String[] selectors = request.getRequestPathInfo().getSelectors();
        for (ResourceType resourceType : resourceTypes) {
            if (selectors.length > 0) {
                for (int i = selectors.length - 1; i >= 0; i--) {
                    String base =
                            resourceType.getType() +
                                    (StringUtils.isNotEmpty(resourceType.getVersion()) ? SLASH + resourceType.getVersion() + SLASH :
                                            SLASH) +
                                    String.join(SLASH, Arrays.copyOf(selectors, i + 1));
                    if (StringUtils.isNotEmpty(extension)) {
                        matches.add(base + DOT + extension + DOT + method);
                        matches.add(base + DOT + extension);
                    }
                    matches.add(base + DOT + method);
                    matches.add(base);
                }
            }
            String base = resourceType.getType() +
                    (StringUtils.isNotEmpty(resourceType.getVersion()) ? SLASH + resourceType.getVersion() : StringUtils.EMPTY);

            if (StringUtils.isNotEmpty(extension)) {
                matches.add(base + SLASH + resourceType.getResourceLabel() + DOT + extension + DOT + method);
                matches.add(base + SLASH + resourceType.getResourceLabel() + DOT + extension);
            }
            matches.add(base + SLASH + resourceType.getResourceLabel() + DOT + method);
            matches.add(base + SLASH + resourceType.getResourceLabel());
            matches.add(base + SLASH + method);
            if (StringUtils.isNotEmpty(extension)) {
                matches.add(base + SLASH + extension);
            }
        }
        return Collections.unmodifiableList(matches);
    }

    private String getScriptEngineName(SlingHttpServletRequest request, TypeProvider typeProvider) {
        String scriptEngineName = null;
        Bundle bundle = typeProvider.getBundle();
        BundleWiring bundleWiring = bundle.adapt(BundleWiring.class);
        List<BundleCapability> capabilities = bundleWiring.getCapabilities(BundledScriptTracker.NS_SLING_RESOURCE_TYPE);
        String[] selectors = request.getRequestPathInfo().getSelectors();
        String requestExtension = request.getRequestPathInfo().getExtension();
        String requestMethod = request.getMethod();
        for (BundleCapability capability : capabilities) {
            ResourceTypeCapability resourceTypeCapability = ResourceTypeCapability.fromBundleCapability(capability);
            for (ResourceType resourceType : typeProvider.getResourceTypes()) {
                if (
                        resourceTypeCapability.getResourceTypes().contains(resourceType) &&
                        Arrays.equals(selectors, resourceTypeCapability.getSelectors().toArray()) &&
                        ((resourceTypeCapability.getExtensions().isEmpty() && "html".equals(requestExtension)) ||
                                resourceTypeCapability.getExtensions().contains(requestExtension)) &&
                        ((resourceTypeCapability.getMethods().isEmpty() &&
                                ("GET".equals(requestMethod) || "HEAD".equals(requestMethod))) ||
                                resourceTypeCapability.getMethods().contains(requestMethod))
                ) {
                    scriptEngineName = resourceTypeCapability.getScriptEngineName();
                    if (scriptEngineName != null) {
                        break;
                    }
                }
            }
        }
        return scriptEngineName;
    }

    private List<String> getScriptEngineExtensions(String scriptEngineName) {
        for (ScriptEngineFactory factory : scriptEngineManager.getEngineFactories()) {
            Set<String> factoryNames = new HashSet<>(factory.getNames());
            if (factoryNames.contains(scriptEngineName)) {
                return factory.getExtensions();
            }
        }
        return Collections.emptyList();
    }
}
