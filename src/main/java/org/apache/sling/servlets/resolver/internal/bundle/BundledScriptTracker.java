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
package org.apache.sling.servlets.resolver.internal.bundle;

import javax.servlet.GenericServlet;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestDispatcherOptions;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.type.ResourceType;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.scripting.spi.bundle.BundledRenderUnit;
import org.apache.sling.scripting.spi.bundle.BundledRenderUnitCapability;
import org.apache.sling.scripting.spi.bundle.BundledRenderUnitFinder;
import org.apache.sling.scripting.spi.bundle.TypeProvider;
import org.apache.sling.servlets.resolver.internal.helper.SearchPathProvider;
import org.apache.sling.servlets.resolver.internal.resource.ServletMounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.util.converter.Converter;
import org.osgi.util.converter.Converters;
import org.osgi.util.tracker.BundleTracker;
import org.osgi.util.tracker.BundleTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true, service = BundledScriptTracker.class)
// component needs to be immediate as this is registered as a internal service
// which is picked up by the optional BundledScriptTrackerHC
@Capability(
        namespace = ExtenderNamespace.EXTENDER_NAMESPACE,
        name = BundledScriptTracker.NS_SLING_SCRIPTING_EXTENDER,
        version = "1.0.0")
public class BundledScriptTracker implements BundleTrackerCustomizer<List<ServiceRegistration<Servlet>>> {
    static final String NS_SLING_SCRIPTING_EXTENDER = "sling.scripting";

    static final Logger LOGGER = LoggerFactory.getLogger(BundledScriptTracker.class);
    private static final String REGISTERING_BUNDLE = "BundledScriptTracker.registering_bundle";
    public static final String NS_SLING_SERVLET = "sling.servlet";
    public static final String AT_VERSION = "version";
    public static final String AT_SCRIPT_ENGINE = "scriptEngine";
    public static final String AT_SCRIPT_EXTENSION = "scriptExtension";
    public static final String AT_EXTENDS = "extends";

    @Reference
    private BundledRenderUnitFinder bundledRenderUnitFinder;

    @Reference
    private ServletMounter mounter;

    private final AtomicReference<BundleContext> bundleContext = new AtomicReference<>();
    private final AtomicReference<BundleTracker<List<ServiceRegistration<Servlet>>>> tracker = new AtomicReference<>();
    private final AtomicReference<Map<Set<String>, ServiceRegistration<Servlet>>> dispatchers = new AtomicReference<>();

    private volatile List<String> searchPaths;

    private Set<String> registeredBundles = new HashSet<>();

    @Activate
    protected void activate(BundleContext context) {
        bundleContext.set(context);
        dispatchers.set(new HashMap<>());
        BundleTracker<List<ServiceRegistration<Servlet>>> bt = new BundleTracker<>(context, Bundle.ACTIVE, this);
        tracker.set(bt);
        bt.open();
    }

    @Deactivate
    protected void deactivate() {
        BundleTracker<List<ServiceRegistration<Servlet>>> bt = tracker.getAndSet(null);
        if (bt != null) {
            bt.close();
        }
        bundleContext.set(null);
        dispatchers.set(null);
    }

    @Reference(policy = ReferencePolicy.DYNAMIC, updated = "bindSearchPathProvider")
    protected void bindSearchPathProvider(final SearchPathProvider searchPathProvider) {
        final boolean reconfiguration = this.searchPaths != null;
        this.searchPaths = searchPathProvider.getSearchPaths();
        if (reconfiguration) {
            // reconfiguration
            BundleTracker<List<ServiceRegistration<Servlet>>> bt = tracker.get();
            if (bt != null) {
                bt.close();
                bt.open();
            }
        }
    }

    protected void unbindSearchPathProvider(final SearchPathProvider searchPathProvider) {
        // nothing to do
    }

    @Override
    public List<ServiceRegistration<Servlet>> addingBundle(Bundle bundle, BundleEvent event) {
        BundleWiring bundleWiring = bundle.adapt(BundleWiring.class);
        Bundle bcBundle = null;
        BundleContext bc = bundleContext.get();
        if (bc != null) {
            bcBundle = bc.getBundle();
        }
        if (bundleWiring.getRequiredWires("osgi.extender").stream()
                .map(BundleWire::getProvider)
                .map(BundleRevision::getBundle)
                .anyMatch(bcBundle::equals)) {
            LOGGER.debug("Inspecting bundle {} for {} capability.", bundle.getSymbolicName(), NS_SLING_SERVLET);
            List<BundleCapability> capabilities = bundleWiring.getCapabilities(NS_SLING_SERVLET);
            Map<BundleCapability, BundledRenderUnitCapability> cache = new HashMap<>();
            capabilities.forEach(bundleCapability -> {
                BundledRenderUnitCapability bundledRenderUnitCapability =
                        BundledRenderUnitCapabilityImpl.fromBundleCapability(bundleCapability);
                cache.put(bundleCapability, bundledRenderUnitCapability);
            });
            Set<TypeProvider> requiresChain = collectRequiresChain(bundleWiring, cache);
            if (!capabilities.isEmpty()) {
                Instant registerStart = Instant.now();
                Set<BundledRenderUnitCapability> bundledRenderUnitCapabilities = new HashSet<>(cache.values());
                bundledRenderUnitCapabilities = reduce(bundledRenderUnitCapabilities);
                List<ServiceRegistration<Servlet>> serviceRegistrations = bundledRenderUnitCapabilities.stream()
                        .flatMap(bundledRenderUnitCapability -> registerServicesWithinBundle(
                                bundle, bundleWiring, cache, requiresChain, bundledRenderUnitCapability))
                        .collect(Collectors.toList());
                refreshDispatcher(serviceRegistrations);
                long duration = Duration.between(registerStart, Instant.now()).toMillis();
                LOGGER.info(
                        "Took {}ms to register {} scripts from bundle {}.",
                        duration,
                        serviceRegistrations.size(),
                        bundle.getSymbolicName());
                registeredBundles.add(bundle.getSymbolicName());
                return serviceRegistrations;
            } else {
                return Collections.emptyList();
            }
        } else {
            return Collections.emptyList();
        }
    }

    Stream<? extends ServiceRegistration<Servlet>> registerServicesWithinBundle(
            Bundle bundle,
            BundleWiring bundleWiring,
            Map<BundleCapability, BundledRenderUnitCapability> cache,
            Set<TypeProvider> requiresChain,
            BundledRenderUnitCapability bundledRenderUnitCapability) {
        Hashtable<String, Object> properties = new Hashtable<>();
        BundledRenderUnit executable = null;
        TypeProvider baseTypeProvider = new TypeProviderImpl(bundledRenderUnitCapability, bundle);
        LinkedHashSet<TypeProvider> inheritanceChain = new LinkedHashSet<>();
        inheritanceChain.add(baseTypeProvider);
        if (!bundledRenderUnitCapability.getResourceTypes().isEmpty()) {
            LinkedHashSet<String> resourceTypesRegistrationValueSet = new LinkedHashSet<>();
            for (ResourceType resourceType : bundledRenderUnitCapability.getResourceTypes()) {
                resourceTypesRegistrationValueSet.add(resourceType.toString());
            }
            String[] resourceTypesRegistrationValue = resourceTypesRegistrationValueSet.stream()
                    .filter(rt -> {
                        if (!rt.startsWith("/")) {
                            for (String prefix : this.searchPaths) {
                                if (resourceTypesRegistrationValueSet.contains(prefix.concat(rt))) {
                                    return false;
                                }
                            }
                        }
                        return true;
                    })
                    .toArray(String[]::new);
            properties.put(ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES, resourceTypesRegistrationValue);

            String extension = bundledRenderUnitCapability.getExtension();
            if (!StringUtils.isEmpty(extension)) {
                properties.put(ServletResolverConstants.SLING_SERVLET_EXTENSIONS, extension);
            }

            if (!bundledRenderUnitCapability.getSelectors().isEmpty()) {
                properties.put(
                        ServletResolverConstants.SLING_SERVLET_SELECTORS,
                        bundledRenderUnitCapability.getSelectors().toArray());
            }

            if (StringUtils.isNotEmpty(bundledRenderUnitCapability.getMethod())) {
                properties.put(ServletResolverConstants.SLING_SERVLET_METHODS, bundledRenderUnitCapability.getMethod());
            }

            String extendedResourceTypeString = bundledRenderUnitCapability.getExtendedResourceType();
            if (StringUtils.isNotEmpty(extendedResourceTypeString)) {
                collectInheritanceChain(inheritanceChain, bundleWiring, extendedResourceTypeString, cache);
                inheritanceChain.stream()
                        .filter(typeProvider ->
                                typeProvider.getBundledRenderUnitCapability().getResourceTypes().stream()
                                        .anyMatch(resourceType ->
                                                resourceType.getType().equals(extendedResourceTypeString)))
                        .findFirst()
                        .ifPresent(typeProvider -> {
                            for (ResourceType type : typeProvider
                                    .getBundledRenderUnitCapability()
                                    .getResourceTypes()) {
                                if (type.getType().equals(extendedResourceTypeString)) {
                                    properties.put(
                                            ServletResolverConstants.SLING_SERVLET_RESOURCE_SUPER_TYPE,
                                            type.toString());
                                }
                            }
                        });
            }
            Set<TypeProvider> aggregate = Stream.concat(inheritanceChain.stream(), requiresChain.stream())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (properties.containsKey(ServletResolverConstants.SLING_SERVLET_RESOURCE_SUPER_TYPE)
                    && baseTypeProvider.getBundledRenderUnitCapability().getScriptEngineName() != null) {
                executable = bundledRenderUnitFinder.findUnit(
                        bundle.getBundleContext(), new HashSet<>(Arrays.asList(baseTypeProvider)), aggregate);
            } else {
                executable = bundledRenderUnitFinder.findUnit(bundle.getBundleContext(), inheritanceChain, aggregate);
            }
        } else if (StringUtils.isNotEmpty(bundledRenderUnitCapability.getPath())
                && StringUtils.isNotEmpty(bundledRenderUnitCapability.getScriptEngineName())) {
            Set<TypeProvider> aggregate = Stream.concat(inheritanceChain.stream(), requiresChain.stream())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            executable = bundledRenderUnitFinder.findUnit(bundle.getBundleContext(), baseTypeProvider, aggregate);
        }
        List<ServiceRegistration<Servlet>> regs = new ArrayList<>();

        if (executable != null) {
            String executablePath = executable.getPath();
            final String executableParentPath = ResourceUtil.getParent(executablePath);
            if (executablePath.equals(bundledRenderUnitCapability.getPath())) {
                properties.put(ServletResolverConstants.SLING_SERVLET_PATHS, executablePath);
            } else {
                if (!bundledRenderUnitCapability.getResourceTypes().isEmpty()
                        && bundledRenderUnitCapability.getSelectors().isEmpty()
                        && StringUtils.isEmpty(bundledRenderUnitCapability.getExtension())
                        && StringUtils.isEmpty(bundledRenderUnitCapability.getMethod())) {
                    String scriptName = FilenameUtils.getName(executable.getPath());
                    String scriptNameNoExtension = scriptName.substring(0, scriptName.lastIndexOf('.'));
                    boolean noMatch = bundledRenderUnitCapability.getResourceTypes().stream()
                            .noneMatch(resourceType -> {
                                String resourceTypePath = resourceType.toString();
                                String label;
                                int lastSlash = resourceTypePath.lastIndexOf('/');
                                if (lastSlash > -1) {
                                    label = resourceTypePath.substring(lastSlash + 1);
                                } else {
                                    label = resourceTypePath;
                                }
                                return label.equals(scriptNameNoExtension);
                            });
                    if (noMatch) {
                        List<String> paths = new ArrayList<>();
                        paths.add(executablePath);
                        bundledRenderUnitCapability.getResourceTypes().forEach(resourceType -> {
                            String resourceTypePath = resourceType.toString();
                            String label;
                            int lastSlash = resourceTypePath.lastIndexOf('/');
                            if (lastSlash > -1) {
                                label = resourceTypePath.substring(lastSlash + 1);
                            } else {
                                label = resourceTypePath;
                            }
                            if (StringUtils.isNotEmpty(executableParentPath)
                                    && executableParentPath.equals(resourceTypePath)) {
                                paths.add(resourceTypePath + "/" + label + ".servlet");
                            }
                        });
                        properties.put(ServletResolverConstants.SLING_SERVLET_PATHS, paths.toArray(new String[0]));
                    }
                    if (!properties.containsKey(ServletResolverConstants.SLING_SERVLET_PATHS)) {
                        String[] rts = Converters.standardConverter()
                                .convert(properties.get(ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES))
                                .to(String[].class);
                        for (String resourceType : rts) {
                            String path;
                            if (resourceType.startsWith("/")) {
                                path = resourceType + "/" + resourceType.substring(resourceType.lastIndexOf('/') + 1)
                                        + "." + FilenameUtils.getExtension(scriptName);
                            } else {
                                path = this.searchPaths.get(0) + resourceType + "/"
                                        + resourceType.substring(resourceType.lastIndexOf('/') + 1) + "."
                                        + FilenameUtils.getExtension(scriptName);
                            }
                            properties.put(ServletResolverConstants.SLING_SERVLET_PATHS, path);
                        }
                    }
                }
                if (!properties.containsKey(ServletResolverConstants.SLING_SERVLET_PATHS)) {
                    bundledRenderUnitCapability.getResourceTypes().forEach(resourceType -> {
                        if (StringUtils.isNotEmpty(executableParentPath)
                                && (executableParentPath + "/").startsWith(resourceType.toString() + "/")) {
                            properties.put(ServletResolverConstants.SLING_SERVLET_PATHS, executablePath);
                        }
                    });
                }
            }
            properties.put(
                    ServletResolverConstants.SLING_SERVLET_NAME,
                    String.format("%s (%s)", BundledScriptServlet.class.getSimpleName(), executablePath));
            properties.put(
                    Constants.SERVICE_DESCRIPTION,
                    BundledScriptServlet.class.getName() + "{" + bundledRenderUnitCapability + "}");
            regs.add(register(
                    bundle.getBundleContext(), new BundledScriptServlet(inheritanceChain, executable), properties));
        } else {
            LOGGER.debug(String.format(
                    "Unable to locate an executable for capability %s.", bundledRenderUnitCapability.toString()));
        }

        return regs.stream();
    }

    private final AtomicLong idCounter = new AtomicLong(0);

    private ServiceRegistration<Servlet> register(
            BundleContext context, Servlet servlet, Hashtable<String, Object> properties) { // NOSONAR
        if (mounter.mountProviders()) {
            return context.registerService(Servlet.class, servlet, properties);
        } else {
            final Long id = idCounter.getAndIncrement();
            properties.put(Constants.SERVICE_ID, id);
            properties.put(BundledHooks.class.getName(), "true");
            @SuppressWarnings("unchecked")
            final ServiceReference<Servlet> reference = (ServiceReference<Servlet>) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class[] {ServiceReference.class}, new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            if (method.equals(ServiceReference.class.getMethod("getProperty", String.class))) {
                                return properties.get(args[0]);
                            } else if (method.equals(ServiceReference.class.getMethod("getPropertyKeys"))) {
                                return properties.keySet().toArray(new String[0]);
                            } else if (method.equals(ServiceReference.class.getMethod("getBundle"))) {
                                return context.getBundle();
                            } else if (method.equals(ServiceReference.class.getMethod("getUsingBundles"))) {
                                BundleContext bc = bundleContext.get();
                                if (bc != null) {
                                    return new Bundle[] {bc.getBundle()};
                                } else {
                                    return new Bundle[0];
                                }
                            } else if (method.equals(
                                    ServiceReference.class.getMethod("isAssignableTo", Bundle.class, String.class))) {
                                return Servlet.class.getName().equals(args[1]);
                            } else if (method.equals(ServiceReference.class.getMethod("compareTo", Object.class))) {
                                return compareTo(args[0]);
                            } else if (method.getName().equals("equals")
                                    && Arrays.equals(method.getParameterTypes(), new Class[] {Object.class})) {
                                return args[0] instanceof ServiceReference && compareTo(args[0]) == 0;
                            } else if (method.getName().equals("hashCode") && method.getParameterCount() == 0) {
                                return id.intValue();
                            } else if (method.getName().equals("toString")) {
                                return "Internal reference: " + id.toString();
                            } else {
                                throw new UnsupportedOperationException(method.toGenericString());
                            }
                        }

                        private int compareTo(Object arg) {
                            ServiceReference<?> other = (ServiceReference<?>) arg;
                            Long id;
                            if ("true".equals(other.getProperty(BundledHooks.class.getName()))) {
                                id = (Long) properties.get(Constants.SERVICE_ID);
                            } else {
                                id = -1L;
                            }

                            Long otherId = (Long) other.getProperty(Constants.SERVICE_ID);

                            if (id.equals(otherId)) {
                                return 0; // same service
                            }

                            Object rankObj = properties.get(Constants.SERVICE_RANKING);
                            Object otherRankObj = other.getProperty(Constants.SERVICE_RANKING);

                            // If no rank, then spec says it defaults to zero.
                            rankObj = (rankObj == null) ? Integer.valueOf(0) : rankObj;
                            otherRankObj = (otherRankObj == null) ? Integer.valueOf(0) : otherRankObj;

                            // If rank is not Integer, then spec says it defaults to zero.
                            Integer rank = (rankObj instanceof Integer) ? (Integer) rankObj : Integer.valueOf(0);
                            Integer otherRank =
                                    (otherRankObj instanceof Integer) ? (Integer) otherRankObj : Integer.valueOf(0);

                            // Sort by rank in ascending order.
                            if (rank.compareTo(otherRank) < 0) {
                                return -1; // lower rank
                            } else if (rank.compareTo(otherRank) > 0) {
                                return 1; // higher rank
                            }

                            // If ranks are equal, then sort by service id in descending order.
                            return (id.compareTo(otherId) < 0) ? 1 : -1;
                        }
                    });

            mounter.bindServlet(servlet, reference);

            @SuppressWarnings("unchecked")
            ServiceRegistration<Servlet> newProxyInstance = (ServiceRegistration<Servlet>) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class[] {ServiceRegistration.class}, new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            if (method.equals(ServiceRegistration.class.getMethod("getReference"))) {
                                return reference;
                            } else if (method.equals(
                                    ServiceRegistration.class.getMethod("setProperties", Dictionary.class))) {
                                return null;
                            } else if (method.equals(ServiceRegistration.class.getMethod("unregister"))) {
                                mounter.unbindServlet(reference);
                                return null;
                            } else if (method.getName().equals("equals")
                                    && Arrays.equals(method.getParameterTypes(), new Class[] {Object.class})) {
                                return args[0] instanceof ServiceRegistration
                                        && reference.compareTo(((ServiceRegistration<?>) args[0]).getReference()) == 0;
                            } else if (method.getName().equals("hashCode") && method.getParameterCount() == 0) {
                                return id.intValue();
                            } else if (method.getName().equals("toString")) {
                                return "Internal registration: " + id;
                            } else {
                                throw new UnsupportedOperationException(method.toGenericString());
                            }
                        }
                    });
            return newProxyInstance;
        }
    }

    private void refreshDispatcher(List<ServiceRegistration<Servlet>> regs) {
        BundleContext bc = bundleContext.get();
        Map<Bundle, List<ServiceRegistration<Servlet>>> tracked;
        BundleTracker<List<ServiceRegistration<Servlet>>> bt = tracker.get();
        if (bt != null) {
            tracked = bt.getTracked();
        } else {
            tracked = Collections.emptyMap();
        }
        Map<Set<String>, ServiceRegistration<Servlet>> oldDispatchers = dispatchers.get();
        Map<Set<String>, ServiceRegistration<Servlet>> newDispatchers = new HashMap<>();
        final Converter c = Converters.standardConverter();
        Stream.concat(tracked.values().stream(), Stream.of(regs))
                .flatMap(List::stream)
                .filter(ref -> getResourceTypeVersion(ref.getReference()) != null)
                .map(this::toProperties)
                .collect(Collectors.groupingBy(BundledScriptTracker::getResourceTypes))
                .forEach((rt, propList) -> {
                    Hashtable<String, Object> properties = new Hashtable<>(); // NOSONAR
                    properties.put(
                            ServletResolverConstants.SLING_SERVLET_NAME,
                            String.format("%s (%s)", DispatcherServlet.class.getSimpleName(), rt));
                    properties.put(ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES, rt.toArray());
                    Set<String> methods = propList.stream()
                            .map(props -> props.getOrDefault(
                                    ServletResolverConstants.SLING_SERVLET_METHODS, new String[] {"GET", "HEAD"}))
                            .map(v -> c.convert(v).to(String[].class))
                            .map(Arrays::asList)
                            .flatMap(List::stream)
                            .collect(Collectors.toSet());
                    Set<String> extensions = propList.stream()
                            .map(props -> props.getOrDefault(
                                    ServletResolverConstants.SLING_SERVLET_EXTENSIONS, new String[] {"html"}))
                            .map(v -> c.convert(v).to(String[].class))
                            .map(Arrays::asList)
                            .flatMap(List::stream)
                            .collect(Collectors.toSet());
                    properties.put(
                            ServletResolverConstants.SLING_SERVLET_EXTENSIONS, extensions.toArray(new String[0]));
                    if (!methods.equals(new HashSet<>(Arrays.asList("GET", "HEAD")))) {
                        properties.put(ServletResolverConstants.SLING_SERVLET_METHODS, methods.toArray(new String[0]));
                    }

                    ServiceRegistration<Servlet> reg = oldDispatchers.remove(rt);
                    if (reg == null) {
                        Optional<BundleContext> registeringBundle = propList.stream()
                                .map(props -> {
                                    Bundle bundle = (Bundle) props.get(REGISTERING_BUNDLE);
                                    if (bundle != null) {
                                        return bundle.getBundleContext();
                                    }
                                    return null;
                                })
                                .findFirst();
                        properties.put(
                                Constants.SERVICE_DESCRIPTION,
                                DispatcherServlet.class.getName() + "{"
                                        + ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES + "="
                                        + rt + "; " + ServletResolverConstants.SLING_SERVLET_EXTENSIONS
                                        + "=" + extensions + "; " + ServletResolverConstants.SLING_SERVLET_METHODS
                                        + "=" + methods + "}");
                        properties.put(BundledHooks.class.getName(), "true");

                        reg = register(registeringBundle.orElse(bc), new DispatcherServlet(rt), properties);
                    } else {
                        if (!new HashSet<>(Arrays.asList(Converters.standardConverter()
                                        .convert(reg.getReference()
                                                .getProperty(ServletResolverConstants.SLING_SERVLET_METHODS))
                                        .to(String[].class)))
                                .equals(methods)) {
                            reg.setProperties(properties);
                        }
                    }
                    newDispatchers.put(rt, reg);
                });
        oldDispatchers.values().forEach(ServiceRegistration::unregister);
        dispatchers.set(newDispatchers);
    }

    private Map<String, Object> toProperties(ServiceRegistration<?> reg) {
        Map<String, Object> result = new HashMap<>();
        ServiceReference<?> ref = reg.getReference();

        set(ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES, ref, result);
        set(ServletResolverConstants.SLING_SERVLET_EXTENSIONS, ref, result);
        set(ServletResolverConstants.SLING_SERVLET_SELECTORS, ref, result);
        set(ServletResolverConstants.SLING_SERVLET_METHODS, ref, result);
        result.put(REGISTERING_BUNDLE, reg.getReference().getBundle());

        return result;
    }

    private void set(String key, ServiceReference<?> ref, Map<String, Object> props) {
        Object value = ref.getProperty(key);
        if (value != null) {
            props.put(key, value);
        }
    }

    @Override
    public void modifiedBundle(Bundle bundle, BundleEvent event, List<ServiceRegistration<Servlet>> regs) {
        LOGGER.warn("Unexpected modified event {} for bundle {}.", event, bundle);
    }

    @Override
    public void removedBundle(Bundle bundle, BundleEvent event, List<ServiceRegistration<Servlet>> regs) {
        LOGGER.debug("Bundle {} removed", bundle.getSymbolicName());
        regs.forEach(ServiceRegistration::unregister);
        refreshDispatcher(Collections.emptyList());
        registeredBundles.remove(bundle.getSymbolicName());
    }

    public Set<String> getRegisteredBundles() {
        return Collections.unmodifiableSet(registeredBundles);
    }

    private class DispatcherServlet extends GenericServlet {
        private static final long serialVersionUID = -1917128676758775458L;
        private final Set<String> resourceType;

        DispatcherServlet(Set<String> rt) {
            this.resourceType = rt;
        }

        @Override
        public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {

            SlingHttpServletRequest slingRequest = (SlingHttpServletRequest) req;

            Map<Bundle, List<ServiceRegistration<Servlet>>> tracked;
            BundleTracker<List<ServiceRegistration<Servlet>>> bt = tracker.get();
            if (bt != null) {
                tracked = bt.getTracked();
            } else {
                tracked = Collections.emptyMap();
            }
            BundleContext bc = bundleContext.get();
            final Bundle bcBundle = bc == null ? null : bc.getBundle();

            final Converter c = Converters.standardConverter();
            Optional<ServiceRegistration<Servlet>> target = tracked.values().stream()
                    .flatMap(List::stream)
                    .filter(reg -> !reg.getReference().getBundle().equals(bcBundle))
                    .filter(reg -> getResourceTypeVersion(reg.getReference()) != null)
                    .filter(reg -> {
                        Map<String, Object> props = toProperties(reg);
                        return getResourceTypes(props).equals(resourceType)
                                && Arrays.asList(c.convert(props.get(ServletResolverConstants.SLING_SERVLET_METHODS))
                                                .defaultValue(new String[] {"GET", "HEAD"})
                                                .to(String[].class))
                                        .contains(slingRequest.getMethod())
                                && Arrays.asList(c.convert(props.get(ServletResolverConstants.SLING_SERVLET_EXTENSIONS))
                                                .defaultValue(new String[] {"html"})
                                                .to(String[].class))
                                        .contains(
                                                slingRequest
                                                                        .getRequestPathInfo()
                                                                        .getExtension()
                                                                == null
                                                        ? "html"
                                                        : slingRequest
                                                                .getRequestPathInfo()
                                                                .getExtension());
                    })
                    .min((left, right) -> {
                        boolean la = Arrays.asList(c.convert(toProperties(left)
                                                .get(ServletResolverConstants.SLING_SERVLET_SELECTORS))
                                        .to(String[].class))
                                .containsAll(Arrays.asList(
                                        slingRequest.getRequestPathInfo().getSelectors()));
                        boolean ra = Arrays.asList(c.convert(toProperties(right)
                                                .get(ServletResolverConstants.SLING_SERVLET_SELECTORS))
                                        .to(String[].class))
                                .containsAll(Arrays.asList(
                                        slingRequest.getRequestPathInfo().getSelectors()));
                        if ((la && ra) || (!la && !ra)) {
                            Version rightVersion = getResourceTypeVersion(right.getReference());
                            if (rightVersion == null) {
                                rightVersion = Version.emptyVersion;
                            }
                            Version leftVersion = getResourceTypeVersion(left.getReference());
                            if (leftVersion == null) {
                                leftVersion = Version.emptyVersion;
                            }
                            return rightVersion.compareTo(leftVersion);
                        } else if (la) {
                            return -1;
                        } else {
                            return 1;
                        }
                    });

            if (target.isPresent()) {
                String[] targetRT = c.convert(target.get()
                                .getReference()
                                .getProperty(ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES))
                        .to(String[].class);
                if (targetRT.length == 0) {
                    ((SlingHttpServletResponse) res).sendError(HttpServletResponse.SC_NOT_FOUND);
                } else {
                    String rt = targetRT[0];
                    RequestDispatcherOptions options = new RequestDispatcherOptions();
                    options.setForceResourceType(rt);

                    RequestDispatcher dispatcher =
                            slingRequest.getRequestDispatcher(slingRequest.getResource(), options);
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

    private static @Nullable Version getResourceTypeVersion(ServiceReference<?> ref) {
        String[] values = Converters.standardConverter()
                .convert(ref.getProperty(ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES))
                .to(String[].class);
        if (values.length > 0) {
            String resourceTypeValue = values[0];
            ResourceType resourceType = ResourceType.parseResourceType(resourceTypeValue);
            return resourceType.getVersion();
        }
        return null;
    }

    private static Set<String> getResourceTypes(Map<String, Object> props) {
        Set<String> resourceTypes = new HashSet<>();
        String[] values = Converters.standardConverter()
                .convert(props.get(ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES))
                .to(String[].class);
        for (String resourceTypeValue : values) {
            resourceTypes.add(ResourceType.parseResourceType(resourceTypeValue).getType());
        }
        return resourceTypes;
    }

    private void collectInheritanceChain(
            @NotNull Set<TypeProvider> providers,
            @NotNull BundleWiring wiring,
            @NotNull String extendedResourceType,
            @NotNull Map<BundleCapability, BundledRenderUnitCapability> cache) {
        for (BundleWire wire : wiring.getRequiredWires(NS_SLING_SERVLET)) {
            BundledRenderUnitCapability wiredCapability =
                    cache.computeIfAbsent(wire.getCapability(), BundledRenderUnitCapabilityImpl::fromBundleCapability);
            if (wiredCapability.getSelectors().isEmpty()) {
                for (ResourceType resourceType : wiredCapability.getResourceTypes()) {
                    if (extendedResourceType.equals(resourceType.getType())) {
                        Bundle providingBundle = wire.getProvider().getBundle();
                        providers.add(new TypeProviderImpl(wiredCapability, providingBundle));
                        String wiredExtends = wiredCapability.getExtendedResourceType();
                        if (StringUtils.isNotEmpty(wiredExtends)) {
                            collectInheritanceChain(providers, wire.getProviderWiring(), wiredExtends, cache);
                        }
                    }
                }
            }
        }
    }

    private Set<TypeProvider> collectRequiresChain(
            @NotNull BundleWiring wiring, Map<BundleCapability, BundledRenderUnitCapability> cache) {
        Set<TypeProvider> requiresChain = new LinkedHashSet<>();
        for (BundleWire wire : wiring.getRequiredWires(NS_SLING_SERVLET)) {
            BundledRenderUnitCapability wiredCapability =
                    cache.computeIfAbsent(wire.getCapability(), BundledRenderUnitCapabilityImpl::fromBundleCapability);
            if (wiredCapability.getSelectors().isEmpty()) {
                Bundle providingBundle = wire.getProvider().getBundle();
                requiresChain.add(new TypeProviderImpl(wiredCapability, providingBundle));
            }
        }
        return requiresChain;
    }

    /**
     * Given a {@code capabilities} set, this method will merge a capability providing a non-null {@link
     * BundledRenderUnitCapability#getExtendedResourceType()} and just a resource type information with the other capabilities describing
     * the same resource type.
     *
     * @param capabilities the original capabilities set
     * @return a new set with merged capabilities or the original set, if no merges had to be performed
     */
    private Set<BundledRenderUnitCapability> reduce(Set<BundledRenderUnitCapability> capabilities) {
        Set<BundledRenderUnitCapability> extenders = capabilities.stream()
                .filter(cap -> cap.getExtendedResourceType() != null
                        && !cap.getResourceTypes().isEmpty()
                        && cap.getSelectors().isEmpty()
                        && cap.getMethod() == null
                        && cap.getExtension() == null
                        && cap.getScriptEngineName() == null)
                .collect(Collectors.toSet());
        if (extenders.isEmpty()) {
            return capabilities;
        }
        Set<BundledRenderUnitCapability> originalCapabilities = new HashSet<>(capabilities);
        Set<BundledRenderUnitCapability> newSet = new HashSet<>();
        originalCapabilities.removeAll(extenders);
        if (originalCapabilities.isEmpty()) {
            return extenders;
        }
        Iterator<BundledRenderUnitCapability> extendersIterator = extenders.iterator();
        while (extendersIterator.hasNext()) {
            BundledRenderUnitCapability extender = extendersIterator.next();
            Iterator<BundledRenderUnitCapability> mergeCandidates = originalCapabilities.iterator();
            boolean processedExtender = false;
            while (mergeCandidates.hasNext()) {
                BundledRenderUnitCapability mergeCandidate = mergeCandidates.next();
                if (extender.getResourceTypes().equals(mergeCandidate.getResourceTypes())) {
                    BundledRenderUnitCapability mergedCapability = BundledRenderUnitCapabilityImpl.builder()
                            .fromCapability(mergeCandidate)
                            .withExtendedResourceType(extender.getExtendedResourceType())
                            .build();
                    newSet.add(mergedCapability);
                    mergeCandidates.remove();
                    processedExtender = true;
                }
            }
            if (processedExtender) {
                extendersIterator.remove();
            }
        }
        // add extenders for which we couldn't merge their properties
        newSet.addAll(extenders);
        newSet.addAll(originalCapabilities);
        return newSet;
    }
}
