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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import org.apache.sling.api.request.RequestUtil;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.ServletResolver;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.servlets.resolver.internal.ResolverConfig;
import org.apache.sling.servlets.resolver.internal.ServletWrapperUtil;
import org.apache.sling.servlets.resolver.internal.resolution.ResolutionCache;
import org.apache.sling.spi.resource.provider.ResourceProvider;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.sling.api.servlets.ServletResolverConstants.SLING_SERVLET_NAME;
import static org.osgi.framework.Constants.SERVICE_ID;
import static org.osgi.framework.Constants.SERVICE_PID;
import static org.osgi.service.component.ComponentConstants.COMPONENT_NAME;

/**
 * The <code>SlingServletResolver</code> resolves a
 * servlet for a request by implementing the {@link ServletResolver} interface.
 *
 * The resolver uses an own session to find the scripts.
 *
 */
@Component(
        configurationPid = ResolverConfig.PID,
        immediate = true,
        service = {ServletMounter.class})
public class ServletMounter {

    /** Logger */
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final String REF_SERVLET = "Servlet";

    private static final String REF_CACHE = "Cache";

    private final ServletContext servletContext;

    private final Map<ServiceReference<Servlet>, ServletReg> servletsByReference = new HashMap<>();

    private volatile boolean active = true;

    private final ServletResourceProviderFactory servletResourceProviderFactory;

    private final MergingServletResourceProvider provider;

    private final Set<ServiceRegistration<?>> providerRegs;

    private final ConcurrentHashMap<ResolutionCache, ResolutionCache> resolutionCaches = new ConcurrentHashMap<>();

    private final BundleContext context;

    private final boolean pathProviders;

    /**
     * Activate this component.
     */
    @Activate
    public ServletMounter(
            final BundleContext context,
            @Reference final ResourceResolverFactory resourceResolverFactory,
            @Reference(target = "(name=org.apache.sling)") ServletContext servletContext,
            final ResolverConfig config) {
        this.servletContext = servletContext;
        this.context = context;
        servletResourceProviderFactory = new ServletResourceProviderFactory(
                config.servletresolver_servletRoot(), resourceResolverFactory.getSearchPath());

        if (config.servletresolver_mountPathProviders()) {
            provider = new MergingServletResourceProvider();
            providerRegs = new HashSet<>();
            pathProviders = true;
            for (String path : resourceResolverFactory.getSearchPath()) {
                final Dictionary<String, Object> params = new Hashtable<>();
                params.put(ResourceProvider.PROPERTY_ROOT, path);
                params.put(Constants.SERVICE_DESCRIPTION, "ServletResourceProvider for Servlets");
                params.put(ResourceProvider.PROPERTY_MODE, ResourceProvider.MODE_PASSTHROUGH);
                providerRegs.add(context.registerService(ResourceProvider.class, provider, params));
            }
        } else if (!config.servletresolver_mountProviders()) {
            provider = new MergingServletResourceProvider();
            providerRegs = new HashSet<>();
            pathProviders = false;
            providerRegs.add(context.registerService(MergingServletResourceProvider.class, provider, null));
        } else {
            provider = null;
            providerRegs = null;
            pathProviders = false;
        }
    }

    /**
     * Deactivate this component.
     */
    @Deactivate
    protected void deactivate() {
        this.active = false;
        // Copy the list of servlets first, to minimize the need for
        // synchronization
        final Collection<ServiceReference<Servlet>> refs;
        synchronized (this.servletsByReference) {
            refs = new ArrayList<>(servletsByReference.keySet());
        }
        if (provider != null) {
            provider.clear();
        }
        // destroy all servlets
        destroyAllServlets(refs);

        if (providerRegs != null) {
            for (ServiceRegistration<?> reg : providerRegs) {
                try {
                    reg.unregister();
                } catch (IllegalStateException ex) {
                    // Can happen during shutdown
                }
            }
            providerRegs.clear();
        }

        // sanity check: clear array (it should be empty now anyway)
        synchronized (this.servletsByReference) {
            this.servletsByReference.clear();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Reference(
            name = REF_SERVLET,
            service = javax.servlet.Servlet.class,
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC,
            target = "(|(" + ServletResolverConstants.SLING_SERVLET_PATHS + "=*)("
                    + ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES + "=*))")
    public void bindServlet(
            final javax.servlet.Servlet servlet, final ServiceReference<javax.servlet.Servlet> reference) {
        if (this.active) {
            createServlet(ServletWrapperUtil.toJakartaServlet(servlet), servlet, (ServiceReference) reference);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void unbindServlet(final ServiceReference<javax.servlet.Servlet> reference) {
        destroyServlet((ServiceReference) reference);
    }

    @Reference(
            name = "JakartaServlet",
            service = Servlet.class,
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC,
            target = "(|(" + ServletResolverConstants.SLING_SERVLET_PATHS + "=*)("
                    + ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES + "=*))")
    public void bindJakartaServlet(final Servlet servlet, final ServiceReference<Servlet> reference) {
        if (this.active) {
            createServlet(servlet, null, reference);
        }
    }

    public void unbindJakartaServlet(final ServiceReference<jakarta.servlet.Servlet> reference) {
        destroyServlet(reference);
    }

    public boolean mountProviders() {
        return provider == null;
    }

    @Reference(
            name = REF_CACHE,
            service = ResolutionCache.class,
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC)
    protected void bindResolutionCache(ResolutionCache cache) {
        if (this.provider != null) {
            cache.flushCache();
            resolutionCaches.put(cache, cache);
        }
    }

    protected void unbindResolutionCache(ResolutionCache cache) {
        if (this.provider != null) {
            resolutionCaches.remove(cache);
        }
    }

    private boolean createServlet(
            final Servlet servlet,
            final javax.servlet.Servlet javaxServlet,
            final ServiceReference<Servlet> reference) {
        // check for a name, this is required
        final String name = getName(reference);

        // check for Sling properties in the service registration
        final ServletResourceProvider srProvider = servletResourceProviderFactory.create(reference, servlet);
        if (srProvider == null) {
            // this is expected if the servlet is not destined for Sling
            return false;
        }

        // initialize now
        try {
            final SlingServletConfig servletConfig = new SlingServletConfig(servletContext, reference, name);
            if (javaxServlet != null) {
                javaxServlet.init(new JavaxSlingServletConfig(servletConfig));
            } else {
                servlet.init(servletConfig);
            }
            logger.debug("bindServlet: Servlet {} initialized", name);
        } catch (final ServletException ce) {
            logger.error(
                    "bindServlet: Servlet " + ServletResourceProviderFactory.getServiceReferenceInfo(reference)
                            + " failed to initialize",
                    ce);
            return false;
        } catch (final Throwable t) { // NOSONAR
            logger.error(
                    "bindServlet: Unexpected problem initializing servlet "
                            + ServletResourceProviderFactory.getServiceReferenceInfo(reference),
                    t);
            return false;
        }

        boolean registered = false;
        final Bundle bundle = reference.getBundle();
        if (bundle != null) {
            final BundleContext bundleContext = bundle.getBundleContext();
            if (bundleContext != null) {
                final List<ServiceRegistration<ResourceProvider<Object>>> regs = new ArrayList<>();
                try {
                    if (this.provider != null) {
                        this.provider.add(srProvider, reference);
                        if (pathProviders) {
                            outer:
                            for (final String path : srProvider.getServletPaths()) {
                                String root =
                                        path.indexOf('/', 1) != -1 ? path.substring(0, path.indexOf('/', 1) + 1) : path;
                                for (ServiceRegistration<?> reg : providerRegs) {
                                    if (root.equals(reg.getReference().getProperty(ResourceProvider.PROPERTY_ROOT))) {
                                        continue outer;
                                    }
                                }
                                final Dictionary<String, Object> params = new Hashtable<>();
                                params.put(ResourceProvider.PROPERTY_ROOT, root);
                                params.put(Constants.SERVICE_DESCRIPTION, "ServletResourceProvider for Servlets");
                                params.put(ResourceProvider.PROPERTY_MODE, ResourceProvider.MODE_PASSTHROUGH);
                                providerRegs.add(context.registerService(ResourceProvider.class, provider, params));
                            }
                        }
                        resolutionCaches.values().forEach(ResolutionCache::flushCache);
                    } else {
                        for (final String root : srProvider.getServletPaths()) {
                            @SuppressWarnings("unchecked")
                            final ServiceRegistration<ResourceProvider<Object>> reg =
                                    (ServiceRegistration<ResourceProvider<Object>>) bundleContext.registerService(
                                            ResourceProvider.class.getName(),
                                            srProvider,
                                            createServiceProperties(reference, root));
                            regs.add(reg);
                        }
                    }
                    registered = true;
                } catch (final IllegalStateException ise) {
                    // bundle context not valid anymore - ignore and continue without this
                }
                if (registered) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Registered {}", srProvider);
                    }
                    synchronized (this.servletsByReference) {
                        servletsByReference.put(reference, new ServletReg(servlet, regs, srProvider));
                    }
                }
            }
        }
        if (!registered) {
            logger.debug("bindServlet: servlet has been unregistered in the meantime. Ignoring {}", name);
        }

        return true;
    }

    private Dictionary<String, Object> createServiceProperties(
            final ServiceReference<Servlet> reference, final String root) {

        final Dictionary<String, Object> params = new Hashtable<>(); // NOSONAR
        params.put(ResourceProvider.PROPERTY_ROOT, root);
        params.put(Constants.SERVICE_DESCRIPTION, "ServletResourceProvider for Servlet at " + root);

        // inherit service ranking
        Object rank = reference.getProperty(Constants.SERVICE_RANKING);
        if (rank instanceof Integer) {
            params.put(Constants.SERVICE_RANKING, rank);
        }

        return params;
    }

    private void destroyAllServlets(final Collection<ServiceReference<Servlet>> refs) {
        for (ServiceReference<Servlet> serviceReference : refs) {
            destroyServlet(serviceReference);
        }
    }

    private void destroyServlet(final ServiceReference<Servlet> reference) {
        ServletReg registration;
        synchronized (this.servletsByReference) {
            registration = servletsByReference.remove(reference);
        }
        if (registration != null) {

            for (final ServiceRegistration<ResourceProvider<Object>> reg : registration.registrations) {
                try {
                    reg.unregister();
                } catch (final IllegalStateException ise) {
                    // this might happen on shutdown
                }
            }
            if (registration.provider != null && provider != null && provider.remove(registration.provider)) {
                resolutionCaches.values().forEach(ResolutionCache::flushCache);
            }
            final String name = RequestUtil.getServletName(registration.servlet);
            logger.debug("unbindServlet: Servlet {} removed", name);

            try {
                registration.servlet.destroy();
            } catch (Throwable t) { // NOSONAR
                logger.error("unbindServlet: Unexpected problem destroying servlet " + name, t);
            }
        }
    }

    /** The list of property names checked by {@link #getName(ServiceReference)} */
    private static final String[] NAME_PROPERTIES = {SLING_SERVLET_NAME, COMPONENT_NAME, SERVICE_PID, SERVICE_ID};

    /**
     * Looks for a name value in the service reference properties. See the
     * class comment at the top for the list of properties checked by this
     * method.
     * @return The servlet name. This method never returns {@code null}
     */
    private static String getName(final ServiceReference<Servlet> reference) {
        String servletName = null;
        for (int i = 0; i < NAME_PROPERTIES.length && (servletName == null || servletName.length() == 0); i++) {
            Object prop = reference.getProperty(NAME_PROPERTIES[i]);
            if (prop != null) {
                servletName = String.valueOf(prop);
            }
        }
        return servletName;
    }

    static final class ServletReg {
        public final Servlet servlet;
        public final List<ServiceRegistration<ResourceProvider<Object>>> registrations;
        private final ServletResourceProvider provider;

        public ServletReg(
                final Servlet s,
                final List<ServiceRegistration<ResourceProvider<Object>>> srs,
                final ServletResourceProvider provider) {
            this.servlet = s;
            this.registrations = srs;
            this.provider = provider;
        }
    }
}
