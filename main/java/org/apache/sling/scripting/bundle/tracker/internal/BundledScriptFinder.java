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
import java.util.Map;
import java.util.Set;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.commons.compiler.source.JavaEscapeHelper;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;
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
    private static final Set<String> DEFAULT_METHODS = new HashSet<>(Arrays.asList("GET", "HEAD"));

    @Reference
    private ScriptEngineManager scriptEngineManager;

    Executable getScript(SlingHttpServletRequest request, LinkedHashSet<TypeProvider> typeProviders, boolean precompiledScripts) {
        List<String> scriptMatches;
        for (TypeProvider provider : typeProviders) {
            scriptMatches = buildScriptMatches(request, provider.getResourceType());
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
                                    throw new RuntimeException("Cannot correctly instantiate class " + className + ".");
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

    private List<String> buildScriptMatches(SlingHttpServletRequest request, ResourceTypeParser.ResourceType resourceType) {
        List<String> matches = new ArrayList<>();
        String method = request.getMethod();
        boolean defaultMethod = DEFAULT_METHODS.contains(method);
        String extension = request.getRequestPathInfo().getExtension();
        String[] selectors = request.getRequestPathInfo().getSelectors();
        if (selectors.length > 0) {
            for (int i = selectors.length - 1; i >= 0; i--) {
                String scriptForMethod = resourceType.getType() +
                        (StringUtils.isNotEmpty(resourceType.getVersion()) ? SLASH + resourceType.getVersion() + SLASH : SLASH) +
                        method + DOT + String.join(SLASH, Arrays.copyOf(selectors, i + 1));
                String scriptNoMethod = resourceType.getType() +
                        (StringUtils.isNotEmpty(resourceType.getVersion()) ? SLASH + resourceType.getVersion() + SLASH : SLASH) +
                        String.join
                                (SLASH, Arrays.copyOf(selectors, i + 1));
                if (StringUtils.isNotEmpty(extension)) {
                    if (defaultMethod) {
                        matches.add(scriptNoMethod + DOT + extension);
                    }
                    matches.add(scriptForMethod + DOT + extension);
                }
                if (defaultMethod) {
                    matches.add(scriptNoMethod);
                }
                matches.add(scriptForMethod);
            }
        }
        String scriptForMethod = resourceType.getType() +
                (StringUtils.isNotEmpty(resourceType.getVersion()) ? SLASH + resourceType.getVersion() + SLASH : SLASH) + method;
        String scriptNoMethod = resourceType.getType() +
                (StringUtils.isNotEmpty(resourceType.getVersion()) ? SLASH + resourceType.getVersion() + SLASH : SLASH) +
                resourceType.getResourceLabel();
        if (StringUtils.isNotEmpty(extension)) {
            if (defaultMethod) {
                matches.add(scriptNoMethod + DOT + extension);
            }
            matches.add(scriptForMethod + DOT + extension);
        }
        if (defaultMethod) {
            matches.add(scriptNoMethod);
        }
        matches.add(scriptForMethod);
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
            Map<String, Object> attributes = capability.getAttributes();
            if (typeProvider.getResourceType().getType().equals(attributes.get(BundledScriptTracker.NS_SLING_RESOURCE_TYPE)) && Arrays.equals(selectors,
                    PropertiesUtil.toStringArray(attributes.get(BundledScriptTracker.AT_SLING_SELECTORS), new String[]{}))) {
                String version = typeProvider.getResourceType().getVersion();
                Version capabilityVersion = (Version) attributes.get(BundledScriptTracker.AT_VERSION);
                if (version != null && capabilityVersion!= null && !version.equals(capabilityVersion.toString())) {
                    continue;
                }
                Set<String> capabilityRequestExtensions = new HashSet<>(
                        Arrays.asList(PropertiesUtil.toStringArray(attributes.get(BundledScriptTracker.AT_SLING_EXTENSIONS), new String[0]))
                );
                Set<String> capabilityRequestMethods = new HashSet<>(
                        Arrays.asList(
                                PropertiesUtil.toStringArray(attributes.get(ServletResolverConstants.SLING_SERVLET_METHODS), new String[0]))
                );
                if (
                    ((capabilityRequestExtensions.isEmpty() && "html".equals(requestExtension)) || capabilityRequestExtensions.contains(requestExtension)) &&
                    ((capabilityRequestMethods.isEmpty() && ("GET".equals(requestMethod) || "HEAD".equals(requestMethod))) || capabilityRequestMethods.contains(requestMethod)) &&
                    StringUtils.isEmpty(scriptEngineName)
                ) {
                    scriptEngineName = (String) attributes.get(BundledScriptTracker.AT_SCRIPT_ENGINE);
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
