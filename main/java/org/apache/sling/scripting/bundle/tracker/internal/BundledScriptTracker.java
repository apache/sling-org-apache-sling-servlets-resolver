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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.GenericServlet;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestDispatcherOptions;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.scripting.bundle.tracker.ResourceType;
import org.jetbrains.annotations.NotNull;
import org.osgi.annotation.bundle.Capability;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.Version;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.namespace.extender.ExtenderNamespace;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.util.tracker.BundleTracker;
import org.osgi.util.tracker.BundleTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
        service = {}
)
@Capability(namespace = ExtenderNamespace.EXTENDER_NAMESPACE,
            name = BundledScriptTracker.NS_SLING_SCRIPTING_EXTENDER,
            version = "1.0.0")
public class BundledScriptTracker implements BundleTrackerCustomizer<List<ServiceRegistration<Servlet>>> {
    static final String NS_SLING_SCRIPTING_EXTENDER = "sling.scripting";

    static final String NS_SLING_RESOURCE_TYPE = "sling.resourceType";
    private static final Logger LOGGER = LoggerFactory.getLogger(BundledScriptTracker.class);
    static final String AT_SLING_SELECTORS = "sling.resourceType.selectors";
    static final String AT_SLING_EXTENSIONS = "sling.resourceType.extensions";
    private static final String REGISTERING_BUNDLE = "org.apache.sling.scripting.bundle.tracker.internal.BundledScriptTracker.registering_bundle";
    static final String AT_VERSION = "version";
    static final String AT_SCRIPT_ENGINE = "scriptEngine";
    static final String AT_SCRIPT_EXTENSION = "scriptExtension";
    static final String AT_EXTENDS = "extends";

    @Reference
    private BundledScriptFinder bundledScriptFinder;

    @Reference
    private ScriptContextProvider scriptContextProvider;


    private volatile BundleContext m_context;
    private volatile BundleTracker<List<ServiceRegistration<Servlet>>> m_tracker;
    private volatile Map<Set<String>, ServiceRegistration<Servlet>> m_dispatchers = new HashMap<>();

    @Activate
    protected void activate(BundleContext context) {
        m_context = context;
        m_tracker = new BundleTracker<>(context, Bundle.ACTIVE, this);
        m_tracker.open();
    }

    @Deactivate
    protected void deactivate() {
        m_tracker.close();
    }

    @Override
    public List<ServiceRegistration<Servlet>> addingBundle(Bundle bundle, BundleEvent event) {
        BundleWiring bundleWiring = bundle.adapt(BundleWiring.class);
        if (bundleWiring.getRequiredWires("osgi.extender").stream().map(BundleWire::getProvider).map(BundleRevision::getBundle)
                .anyMatch(m_context.getBundle()::equals)) {
            LOGGER.debug("Inspecting bundle {} for {} capability.", bundle.getSymbolicName(), NS_SLING_RESOURCE_TYPE);
            List<BundleCapability> capabilities = bundleWiring.getCapabilities(NS_SLING_RESOURCE_TYPE);
            if (!capabilities.isEmpty()) {
                List<ServiceRegistration<Servlet>> serviceRegistrations = capabilities.stream().flatMap(cap ->
                {
                    Hashtable<String, Object> properties = new Hashtable<>();
                    properties.put(ServletResolverConstants.SLING_SERVLET_NAME, BundledScriptServlet.class.getName());
                    properties.put(Constants.SERVICE_DESCRIPTION, BundledScriptServlet.class.getName() + cap.getAttributes());
                    ResourceTypeCapability resourceTypeCapability = ResourceTypeCapability.fromBundleCapability(cap);
                    String[] resourceTypesRegistrationValue = new String[resourceTypeCapability.getResourceTypes().size()];
                    int rtIndex = 0;
                    for (ResourceType resourceType : resourceTypeCapability.getResourceTypes()) {
                        resourceTypesRegistrationValue[rtIndex++] = resourceType.toString();
                    }
                    properties.put(ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES, resourceTypesRegistrationValue);

                    String extension = resourceTypeCapability.getExtension();
                    if (StringUtils.isEmpty(extension)) {
                        extension = "html";
                    }
                    properties.put(ServletResolverConstants.SLING_SERVLET_EXTENSIONS, extension);

                    if (!resourceTypeCapability.getSelectors().isEmpty()) {
                        properties.put(ServletResolverConstants.SLING_SERVLET_SELECTORS, resourceTypeCapability.getSelectors().toArray());
                    }

                    if (StringUtils.isNotEmpty(resourceTypeCapability.getMethod())) {
                        properties.put(ServletResolverConstants.SLING_SERVLET_METHODS, resourceTypeCapability.getMethod());
                    }

                    List<ServiceRegistration<Servlet>> regs = new ArrayList<>();
                    LinkedHashSet<TypeProvider> wiredProviders = new LinkedHashSet<>();
                    wiredProviders.add(new TypeProvider(resourceTypeCapability, bundle));
                    String extendedResourceTypeString = resourceTypeCapability.getExtendedResourceType();
                    if (StringUtils.isNotEmpty(extendedResourceTypeString)) {
                        collectProvidersChain(wiredProviders, bundleWiring, extendedResourceTypeString);
                        wiredProviders.stream().filter(typeProvider -> typeProvider.getResourceTypeCapability().getResourceTypes().stream().anyMatch(resourceType -> resourceType.getType().equals(extendedResourceTypeString))).findFirst().ifPresent(typeProvider -> {
                            for (ResourceType type : typeProvider.getResourceTypeCapability().getResourceTypes()) {
                                if (type.getType().equals(extendedResourceTypeString)) {
                                    properties.put(ServletResolverConstants.SLING_SERVLET_RESOURCE_SUPER_TYPE, type.toString());
                                }
                            }
                        });
                    }
                    Executable executable = bundledScriptFinder.getScript(wiredProviders);
                    if (executable != null) {
                        properties.put(ServletResolverConstants.SLING_SERVLET_PATHS, executable.getPath());
                        regs.add(
                                bundle.getBundleContext().registerService(
                                        Servlet.class,
                                        new BundledScriptServlet(scriptContextProvider, wiredProviders, executable),
                                        properties
                                )
                        );
                    } else {
                        LOGGER.error(String.format("Unable to locate an executable for capability %s.", cap));
                    }

                    return regs.stream();
                }).collect(Collectors.toList());
                refreshDispatcher(serviceRegistrations);
                return serviceRegistrations;
            } else {
                return Collections.emptyList();
            }
        } else {
            return Collections.emptyList();
        }
    }

    private void refreshDispatcher(List<ServiceRegistration<Servlet>> regs) {
        Map<Set<String>, ServiceRegistration<Servlet>> dispatchers = new HashMap<>();
        Stream.concat(m_tracker.getTracked().values().stream(), Stream.of(regs)).flatMap(List::stream)
            .filter(ref -> getResourceTypeVersion(ref.getReference()) != null)
            .map(this::toProperties)
            .collect(Collectors.groupingBy(BundledScriptTracker::getResourceTypes)).forEach((rt, propList) -> {
            Hashtable<String, Object> properties = new Hashtable<>();
            properties.put(ServletResolverConstants.SLING_SERVLET_NAME, DispatcherServlet.class.getName());
            properties.put(ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES, rt.toArray());
            Set<String> methods = propList.stream()
                    .map(props -> props.getOrDefault(ServletResolverConstants.SLING_SERVLET_METHODS, new String[]{"GET", "HEAD"}))
                    .map(PropertiesUtil::toStringArray).map(Arrays::asList).flatMap(List::stream).collect(Collectors.toSet());
            Set<String> extensions = propList.stream().map(props -> props.getOrDefault(ServletResolverConstants
                    .SLING_SERVLET_EXTENSIONS, new String[]{"html"})).map(PropertiesUtil::toStringArray).map(Arrays::asList).flatMap
                    (List::stream).collect(Collectors.toSet());
            properties.put(ServletResolverConstants.SLING_SERVLET_EXTENSIONS, extensions.toArray(new String[0]));
            if (!methods.equals(new HashSet<>(Arrays.asList("GET", "HEAD")))) {
                properties.put(ServletResolverConstants.SLING_SERVLET_METHODS, methods.toArray(new String[0]));
            }
            ServiceRegistration<Servlet> reg = m_dispatchers.remove(rt);
            if (reg == null) {
                Optional<BundleContext> registeringBundle = propList.stream().map(props -> {
                    Bundle bundle = (Bundle) props.get(REGISTERING_BUNDLE);
                    if (bundle != null) {
                        return bundle.getBundleContext();
                    }
                    return null;
                }).findFirst();
                properties.put(Constants.SERVICE_DESCRIPTION,
                        DispatcherServlet.class.getName() + "{" + ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES +
                        "=" + rt + "; " +
                        ServletResolverConstants.SLING_SERVLET_EXTENSIONS + "=" + extensions + "; " +
                        ServletResolverConstants.SLING_SERVLET_METHODS + "=" + methods  + "}");
                reg = registeringBundle.orElse(m_context).registerService(Servlet.class, new DispatcherServlet(rt), properties);
            } else {
                if (!new HashSet<>(Arrays.asList(PropertiesUtil
                        .toStringArray(reg.getReference().getProperty(ServletResolverConstants.SLING_SERVLET_METHODS), new String[0])))
                        .equals(methods)) {
                    reg.setProperties(properties);
                }
            }
            dispatchers.put(rt, reg);
        });
        m_dispatchers.values().forEach(ServiceRegistration::unregister);
        m_dispatchers = dispatchers;
    }

    private Hashtable<String, Object> toProperties(ServiceRegistration<?> reg) {
        Hashtable<String, Object> result = new Hashtable<>();
        ServiceReference<?> ref = reg.getReference();

        set(ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES, ref, result);
        set(ServletResolverConstants.SLING_SERVLET_EXTENSIONS, ref, result);
        set(ServletResolverConstants.SLING_SERVLET_SELECTORS, ref, result);
        set(ServletResolverConstants.SLING_SERVLET_METHODS, ref, result);
        result.put(REGISTERING_BUNDLE, reg.getReference().getBundle());

        return result;
    }

    private void set(String key, ServiceReference<?> ref, Hashtable<String, Object> props) {
        Object value = ref.getProperty(key);
        if (value != null) {
            props.put(key, value);
        }
    }

    @Override
    public void modifiedBundle(Bundle bundle, BundleEvent event, List<ServiceRegistration<Servlet>> regs) {
        LOGGER.warn("Unexpected modified event {} for bundle {}.", event.toString(), bundle.toString());
    }

    @Override
    public void removedBundle(Bundle bundle, BundleEvent event, List<ServiceRegistration<Servlet>> regs) {
        LOGGER.debug("Bundle {} removed", bundle.getSymbolicName());
        regs.forEach(ServiceRegistration::unregister);
        refreshDispatcher(Collections.emptyList());
    }

    private class DispatcherServlet extends GenericServlet {
        private final Set<String> m_rt;

        DispatcherServlet(Set<String> rt) {
            m_rt = rt;
        }

        @Override
        public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {

            SlingHttpServletRequest slingRequest = (SlingHttpServletRequest) req;

            Optional<ServiceRegistration<Servlet>> target = m_tracker.getTracked().values().stream().flatMap(List::stream)
                    .filter(
                            reg -> !reg.getReference().getBundle().equals(m_context.getBundle())
                    )
                    .filter(reg -> getResourceTypeVersion(reg.getReference()) != null)
                    .filter(reg ->
                    {
                        Hashtable<String, Object> props = toProperties(reg);
                        return getResourceTypes(props).equals(m_rt) &&
                                Arrays.asList(PropertiesUtil
                                        .toStringArray(props.get(ServletResolverConstants.SLING_SERVLET_METHODS),
                                                new String[]{"GET", "HEAD"}))
                                        .contains(slingRequest.getMethod()) &&
                                Arrays.asList(PropertiesUtil
                                        .toStringArray(props.get(ServletResolverConstants.SLING_SERVLET_EXTENSIONS), new String[]{"html"}))
                                        .contains(slingRequest.getRequestPathInfo().getExtension() == null ? "html" :
                                                slingRequest.getRequestPathInfo().getExtension());
                    }).min((left, right) ->
                    {
                        boolean la = Arrays.asList(PropertiesUtil
                                .toStringArray(toProperties(left).get(ServletResolverConstants.SLING_SERVLET_SELECTORS), new String[0]))
                                .containsAll(Arrays.asList(slingRequest.getRequestPathInfo().getSelectors()));
                        boolean ra = Arrays.asList(PropertiesUtil
                                .toStringArray(toProperties(right).get(ServletResolverConstants.SLING_SERVLET_SELECTORS), new String[0]))
                                .containsAll(Arrays.asList(slingRequest.getRequestPathInfo().getSelectors()));
                        if ((la && ra) || (!la && !ra)) {
                            return new Version(getResourceTypeVersion(right.getReference()))
                                    .compareTo(new Version(getResourceTypeVersion(left.getReference())));
                        } else if (la) {
                            return -1;
                        } else {
                            return 1;
                        }

                    });

            if (target.isPresent()) {
                String[] targetRT =
                        PropertiesUtil.toStringArray(target.get().getReference().getProperty(ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES));
                if (targetRT == null || targetRT.length == 0) {
                    ((SlingHttpServletResponse) res).sendError(HttpServletResponse.SC_NOT_FOUND);
                } else {
                    String rt = targetRT[0];
                    RequestDispatcherOptions options = new RequestDispatcherOptions();
                    options.setForceResourceType(rt);

                    RequestDispatcher dispatcher = slingRequest.getRequestDispatcher(slingRequest.getResource(), options);
                    if (dispatcher != null) {
                        if (slingRequest.getAttribute(SlingConstants.ATTR_INCLUDE_SERVLET_PATH) == null) {
                            final String contentType = slingRequest.getResponseContentType();
                            if (contentType != null) {
                                res.setContentType(contentType);
                                if (contentType.startsWith("text/")) {
                                    res.setCharacterEncoding("UTF-8");
                                }
                            }
                        }
                        dispatcher.include(req, res);
                    } else {
                        ((SlingHttpServletResponse) res).sendError(HttpServletResponse.SC_NOT_FOUND);
                    }
                }
            } else {
                ((SlingHttpServletResponse) res).sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        }
    }

    private static String getResourceTypeVersion(ServiceReference<?> ref) {
        String[] values = PropertiesUtil.toStringArray(ref.getProperty(ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES));
        String resourceTypeValue = values[0];
        ResourceType resourceType = ResourceType.parseResourceType(resourceTypeValue);
        return resourceType.getVersion();
    }

    private static Set<String> getResourceTypes(Hashtable<String, Object> props) {
        Set<String> resourceTypes = new HashSet<>();
        String[] values = PropertiesUtil.toStringArray(props.get(ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES));
        for (String resourceTypeValue : values) {
            resourceTypes.add(ResourceType.parseResourceType(resourceTypeValue).getType());
        }
        return resourceTypes;
    }

    private void collectProvidersChain(@NotNull Set<TypeProvider> providers, @NotNull BundleWiring wiring,
                                       @NotNull String extendedResourceType) {
        for (BundleWire wire : wiring.getRequiredWires(NS_SLING_RESOURCE_TYPE)) {
            ResourceTypeCapability wiredCapability = ResourceTypeCapability.fromBundleCapability(wire.getCapability());
            if (wiredCapability.getSelectors().isEmpty()) {
                for (ResourceType resourceType : wiredCapability.getResourceTypes()) {
                    if (extendedResourceType.equals(resourceType.getType())) {
                        Bundle providingBundle = wire.getProvider().getBundle();
                        providers.add(new TypeProvider(wiredCapability, providingBundle));
                        for (BundleWire providedWire : wire.getProvider().getWiring().getRequiredWires(NS_SLING_RESOURCE_TYPE)) {
                            ResourceTypeCapability resourceTypeCapability =
                                    ResourceTypeCapability.fromBundleCapability(providedWire.getCapability());
                            String capabilityExtends = resourceTypeCapability.getExtendedResourceType();
                            if (resourceTypeCapability.getSelectors().isEmpty() && StringUtils.isNotEmpty(capabilityExtends)) {
                                for (ResourceType providedResourceType : resourceTypeCapability.getResourceTypes()) {
                                    if (providedResourceType.getType().equals(extendedResourceType)) {
                                        collectProvidersChain(providers, providedWire.getProvider()
                                                .getBundle().adapt(BundleWiring.class), capabilityExtends);
                                    }
                                }
                            } else {
                                providers.add(new TypeProvider(resourceTypeCapability, providedWire.getProvider().getBundle()));
                            }
                        }
                    }
                }
            }
        }
    }
}
