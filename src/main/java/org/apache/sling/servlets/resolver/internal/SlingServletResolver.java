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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.SlingJakartaHttpServletResponse;
import org.apache.sling.api.request.RequestProgressTracker;
import org.apache.sling.api.request.RequestUtil;
import org.apache.sling.api.request.SlingJakartaRequestEvent;
import org.apache.sling.api.request.SlingJakartaRequestListener;
import org.apache.sling.api.request.SlingRequestEvent;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.SyntheticResource;
import org.apache.sling.api.servlets.JakartaErrorHandler;
import org.apache.sling.api.servlets.JakartaOptingServlet;
import org.apache.sling.api.servlets.ServletResolver;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.api.wrappers.JavaxToJakartaRequestWrapper;
import org.apache.sling.serviceusermapping.ServiceUserMapped;
import org.apache.sling.servlets.resolver.api.IgnoredServletResourcePredicate;
import org.apache.sling.servlets.resolver.internal.defaults.DefaultErrorHandlerServlet;
import org.apache.sling.servlets.resolver.internal.defaults.DefaultServlet;
import org.apache.sling.servlets.resolver.internal.helper.AbstractResourceCollector;
import org.apache.sling.servlets.resolver.internal.helper.LocationCollector;
import org.apache.sling.servlets.resolver.internal.helper.NamedScriptResourceCollector;
import org.apache.sling.servlets.resolver.internal.helper.ResourceCollector;
import org.apache.sling.servlets.resolver.internal.resolution.ResolutionCache;
import org.apache.sling.servlets.resolver.internal.resource.MergingServletResourceProvider;
import org.apache.sling.servlets.resolver.internal.resource.SlingServletConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.sling.api.SlingConstants.SLING_CURRENT_SERVLET_NAME;
import static org.apache.sling.api.servlets.ServletResolverConstants.DEFAULT_ERROR_HANDLER_RESOURCE_TYPE;

/**
 * The <code>SlingServletResolver</code> resolves a
 * servlet for a request by implementing the {@link ServletResolver} interface.
 *
 * The SlingServletResolver uses uses dedicated ResourceResolver(s) for resolving the servlets.
 * In case the thread is handling a request, the {@link #onEvent(SlingRequestEvent)} method is called by the
 * Sling engine and a per-thread ResourceResolver is created, used and also closed when the request is
 * finished.
 *
 * In case the thread does execute not within the context of a request, a shared ResourceResolver instance is used.
 *
 */
@Component(
        name = ResolverConfig.PID,
        service = {ServletResolver.class, JakartaErrorHandler.class, SlingJakartaRequestListener.class},
        property = {
            Constants.SERVICE_DESCRIPTION + "=Apache Sling Servlet Resolver and Error Handler",
            Constants.SERVICE_VENDOR + "=The Apache Software Foundation"
        })
@Designate(ocd = ResolverConfig.class)
public class SlingServletResolver implements ServletResolver, SlingJakartaRequestListener, JakartaErrorHandler {

    private static final String SERVICE_USER = "scripts";

    /** Servlet resolver logger */
    public static final Logger LOGGER = LoggerFactory.getLogger(SlingServletResolver.class);

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Reference(
            target = "(|(" + ServiceUserMapped.SUBSERVICENAME + "=" + SERVICE_USER + ")(!("
                    + ServiceUserMapped.SUBSERVICENAME + "=*)))")
    private ServiceUserMapped scriptServiceUserMapped;

    @Reference
    private ResolutionCache resolutionCache;

    @Reference(target = "(name=org.apache.sling)")
    private ServletContext servletContext;

    // the default servlet if no other servlet applies for a request. This
    // field is set on demand by getDefaultServlet()
    private AtomicReference<Servlet> defaultServlet = new AtomicReference<>();

    // the default error handler servlet if no other error servlet applies for
    // a request. This field is set on demand by getDefaultErrorServlet()
    private AtomicReference<Servlet> fallbackErrorServlet = new AtomicReference<>();

    private AtomicReference<ResourceResolver> sharedScriptResolver = new AtomicReference<>();

    private final ThreadLocal<ResourceResolver> perThreadScriptResolver = new ThreadLocal<>();

    @Reference(policy = ReferencePolicy.DYNAMIC, cardinality = ReferenceCardinality.OPTIONAL)
    private volatile IgnoredServletResourcePredicate ignoredResourcePredicate;

    /**
     * The allowed execution paths.
     */
    private AtomicReference<String[]> executionPaths = new AtomicReference<>();

    /**
     * The default extensions
     */
    private AtomicReference<Collection<String>> defaultExtensions = new AtomicReference<>();

    private boolean useResourceCaching;

    private final PathBasedServletAcceptor pathBasedServletAcceptor = new PathBasedServletAcceptor();

    private static final Servlet forbiddenPathServlet = new HttpServlet() {
        private static final long serialVersionUID = 1L;

        @Override
        public void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
    };

    // ---------- ServletResolver interface -----------------------------------

    @Override
    public @Nullable Servlet resolve(@NotNull final SlingJakartaHttpServletRequest request) {
        final Resource resource = request.getResource();

        // start tracking servlet resolution
        final RequestProgressTracker progressTracker = request.getRequestProgressTracker();
        final String timerName = "resolveServlet(" + resource.getPath() + ")";
        progressTracker.startTimer(timerName);

        final String resourceType = resource.getResourceType();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("resolveServlet called for resource {}", resource);
        }

        final ResourceResolver scriptResolver = this.getScriptResourceResolver();
        Servlet servlet = null;

        if (resourceType.length() > 0) {
            servlet = resolveServletInternal(request, null, resourceType, scriptResolver);
        }

        // last resort, use the default servlet
        if (servlet == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("No specific servlet found, trying default");
            }
            servlet = getDefaultServlet();
        }

        // track servlet resolution termination
        progressTracker.logTimer(timerName, "Using servlet {0}", RequestUtil.getServletName(servlet));

        // log the servlet found
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Servlet {} found for resource={}", RequestUtil.getServletName(servlet), resource);
        }

        return servlet;
    }

    @Override
    public @Nullable Servlet resolve(@NotNull final Resource resource, @NotNull final String scriptName) {
        if (resource == null) {
            throw new IllegalArgumentException("Resource must not be null");
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("resolveServlet called for resource {} with script name {}", resource, scriptName);
        }

        final ResourceResolver scriptResolver = this.getScriptResourceResolver();
        final Servlet servlet = resolveServletInternal(null, resource, scriptName, scriptResolver);

        // log the servlet found
        if (LOGGER.isDebugEnabled()) {
            if (servlet != null) {
                LOGGER.debug(
                        "Servlet {} found for resource {} and script name {}",
                        RequestUtil.getServletName(servlet),
                        resource,
                        scriptName);
            } else {
                LOGGER.debug("No servlet found for resource {} and script name {}", resource, scriptName);
            }
        }

        return servlet;
    }

    @Override
    public @Nullable Servlet resolve(@NotNull final ResourceResolver resolver, @NotNull final String scriptName) {
        if (resolver == null) {
            throw new IllegalArgumentException("Resource resolver must not be null");
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("resolveServlet called for for script name {}", scriptName);
        }

        final ResourceResolver scriptResolver = this.getScriptResourceResolver();
        final Servlet servlet = resolveServletInternal(null, (Resource) null, scriptName, scriptResolver);

        // log the servlet found
        if (LOGGER.isDebugEnabled()) {
            if (servlet != null) {
                LOGGER.debug("Servlet {} found for script name {}", RequestUtil.getServletName(servlet), scriptName);
            } else {
                LOGGER.debug("No servlet found for script name {}", scriptName);
            }
        }

        return servlet;
    }

    /**
     * Get the servlet for the resource.
     */
    private Servlet getServlet(final Resource scriptResource) {
        // no resource -> no servlet
        if (scriptResource == null) {
            return null;
        }
        // if resource is fetched using shared resource resolver
        // or resource is a servlet resource, just adapt to servlet
        if (scriptResource.getResourceResolver() == this.sharedScriptResolver
                || "sling/bundle/resource".equals(scriptResource.getResourceSuperType())) {
            return scriptResource.adaptTo(Servlet.class);
        }
        // return a resource wrapper to make sure the implementation
        // switches from the per thread resource resolver to the shared once
        // the per thread resource resolver is closed
        return new ScriptResource(scriptResource, perThreadScriptResolver::get, this.sharedScriptResolver.get())
                .adaptTo(Servlet.class);
    }

    // ---------- ErrorHandler interface --------------------------------------

    @Override
    public void handleError(
            final int status,
            final String message,
            final SlingJakartaHttpServletRequest request,
            final SlingJakartaHttpServletResponse response)
            throws IOException {

        // do not handle, if already handling ....
        if (request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) != null) {
            LOGGER.error("handleError: Recursive invocation. Not further handling status {}({})", status, message);
            return;
        }

        // start tracker
        RequestProgressTracker progressTracker = request.getRequestProgressTracker();
        String timerName = "handleError:status=" + status;
        progressTracker.startTimer(timerName);

        final ResourceResolver scriptResolver = this.getScriptResourceResolver();
        try {
            // find the error handler component
            Resource resource = getErrorResource(request);

            // find a servlet for the status as the method name
            String extension = request.getRequestPathInfo().getExtension();
            ResourceCollector locationUtil = new ResourceCollector(
                    String.valueOf(status),
                    DEFAULT_ERROR_HANDLER_RESOURCE_TYPE,
                    resource,
                    extension,
                    this.executionPaths.get(),
                    this.useResourceCaching);
            Servlet servlet = getServletInternal(locationUtil, request, scriptResolver);

            // fall back to default servlet if none
            if (servlet == null) {
                servlet = getDefaultErrorServlet(request, resource, scriptResolver);
            }

            // set the message properties
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
            request.setAttribute(RequestDispatcher.ERROR_MESSAGE, message);
            request.setAttribute(RequestDispatcher.ERROR_METHOD, request.getMethod());
            if (request.getQueryString() != null) {
                request.setAttribute(RequestDispatcher.ERROR_QUERY_STRING, request.getQueryString());
            }

            // the servlet name for a sendError handling is still stored
            // as the request attribute
            Object servletName = request.getAttribute(SLING_CURRENT_SERVLET_NAME);
            if (servletName instanceof String) {
                request.setAttribute(RequestDispatcher.ERROR_SERVLET_NAME, servletName);
            }

            // log a track entry after resolution before calling the handler
            progressTracker.logTimer(timerName, "Using handler {0}", RequestUtil.getServletName(servlet));

            handleError(servlet, request, response);

        } finally {
            progressTracker.logTimer(timerName, "Error handler finished");
        }
    }

    @Override
    public void handleError(
            final Throwable throwable,
            final SlingJakartaHttpServletRequest request,
            final SlingJakartaHttpServletResponse response)
            throws IOException {
        // do not handle, if already handling ....
        if (request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) != null) {
            LOGGER.error("handleError: Recursive invocation. Not further handling Throwable:", throwable);
            return;
        }

        // start tracker
        RequestProgressTracker progressTracker = request.getRequestProgressTracker();
        String timerName = "handleError:throwable=" + throwable.getClass().getName();
        progressTracker.startTimer(timerName);

        final ResourceResolver scriptResolver = this.getScriptResourceResolver();
        try {
            // find the error handler component
            Servlet servlet = null;
            Resource resource = getErrorResource(request);

            Class<?> tClass = throwable.getClass();
            while (servlet == null && tClass != Object.class) {
                // find a servlet for the simple class name as the method name
                String extension = request.getRequestPathInfo().getExtension();
                ResourceCollector locationUtil = new ResourceCollector(
                        tClass.getSimpleName(),
                        DEFAULT_ERROR_HANDLER_RESOURCE_TYPE,
                        resource,
                        extension,
                        this.executionPaths.get(),
                        this.useResourceCaching);
                servlet = getServletInternal(locationUtil, request, scriptResolver);

                // go to the base class
                tClass = tClass.getSuperclass();
            }

            if (servlet == null) {
                servlet = getDefaultErrorServlet(request, resource, scriptResolver);
            }

            // set the message properties
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, throwable);
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE, throwable.getClass());
            request.setAttribute(RequestDispatcher.ERROR_MESSAGE, throwable.getMessage());
            request.setAttribute(RequestDispatcher.ERROR_METHOD, request.getMethod());
            if (request.getQueryString() != null) {
                request.setAttribute(RequestDispatcher.ERROR_QUERY_STRING, request.getQueryString());
            }

            // log a track entry after resolution before calling the handler
            progressTracker.logTimer(timerName, "Using handler {0}", RequestUtil.getServletName(servlet));

            handleError(servlet, request, response);
        } finally {
            progressTracker.logTimer(timerName, "Error handler finished");
        }
    }

    // ---------- internal helper ---------------------------------------------

    private ResourceResolver getScriptResourceResolver() {
        ResourceResolver scriptResolver = this.perThreadScriptResolver.get();
        if (scriptResolver == null) {
            // no per thread, let's use the shared one
            synchronized (this.sharedScriptResolver) {
                // invalidate all caches and refresh to see the latest updates
                invalidateCache(this.sharedScriptResolver.get());
                this.sharedScriptResolver.get().refresh();
            }
            scriptResolver = this.sharedScriptResolver.get();
        }
        return scriptResolver;
    }

    @Override
    public void onEvent(final SlingJakartaRequestEvent event) {
        if (event.getType() == SlingJakartaRequestEvent.EventType.EVENT_INIT) {
            try {
                ResourceResolver clone = this.sharedScriptResolver.get().clone(null);
                this.perThreadScriptResolver.set(clone);
            } catch (final LoginException e) {
                LOGGER.error("Unable to create new script resolver clone", e);
            }
        } else if (event.getType() == SlingJakartaRequestEvent.EventType.EVENT_DESTROY) {
            final ResourceResolver resolver = this.perThreadScriptResolver.get();
            if (resolver != null) {
                this.perThreadScriptResolver.remove();
                resolver.close();
            }
        }
    }

    /**
     * Returns the resource of the given request to be used as the basis for
     * error handling. If the resource has not yet been set in the request
     * because the error occurred before the resource could be set (e.g. during
     * resource resolution) a synthetic resource is returned whose type is
     * {@link ServletResolverConstants#DEFAULT_ERROR_HANDLER_RESOURCE_TYPE}.
     *
     * @param request The request whose resource is to be returned.
     */
    private Resource getErrorResource(final SlingJakartaHttpServletRequest request) {
        Resource res = request.getResource();
        if (res == null) { // NOSONAR
            res = new SyntheticResource(
                    request.getResourceResolver(), request.getPathInfo(), DEFAULT_ERROR_HANDLER_RESOURCE_TYPE);
        }
        return res;
    }

    /** @return true if the IgnoredResourcePredicate is set and returns true for the supplied Resource */
    private boolean ignoreResource(@NotNull Resource r) {
        final boolean result = r != null && ignoredResourcePredicate != null && ignoredResourcePredicate.test(r);
        if (result && LOGGER.isDebugEnabled()) {
            LOGGER.debug("IgnoredResourcePredicate causes Resource to be ignored: {}", r.getPath());
        }
        return result;
    }

    /**
     * Resolve an appropriate servlet for a given request and resource type
     * using the provided ResourceResolver
     */
    private Servlet resolveServletInternal(
            final SlingJakartaHttpServletRequest request,
            final Resource resource,
            final String scriptNameOrResourceType,
            final ResourceResolver resolver) {
        Servlet servlet = null;

        if (isInvalidPath(scriptNameOrResourceType)) {
            if (request != null) {
                request.getRequestProgressTracker()
                        .log(
                                "Will not look for a servlet at {0} as it contains more than two consecutive dots",
                                scriptNameOrResourceType);
            }
            return null;
        }

        // first check whether the type of a resource is the absolute
        // path of a servlet (or script)
        if (scriptNameOrResourceType.charAt(0) == '/') {
            final String scriptPath = ResourceUtil.normalize(scriptNameOrResourceType);
            if (scriptPath != null && isPathAllowed(scriptPath, this.executionPaths.get())) {
                final Resource res =
                        AbstractResourceCollector.getResourceOrNull(resolver, scriptPath, useResourceCaching);
                servlet = ignoreResource(res) ? null : this.getServlet(res);
                if (servlet != null && !pathBasedServletAcceptor.accept(request, servlet)) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(
                                "Servlet {} rejected by {} returning FORBIDDEN status",
                                RequestUtil.getServletName(servlet),
                                pathBasedServletAcceptor.getClass().getSimpleName());
                    }
                    servlet = forbiddenPathServlet;
                } else if (servlet != null && LOGGER.isDebugEnabled()) {
                    LOGGER.debug(
                            "Servlet {} found using absolute resource type {}",
                            RequestUtil.getServletName(servlet),
                            scriptNameOrResourceType);
                }
            } else {
                if (request != null) {
                    request.getRequestProgressTracker()
                            .log(
                                    "Will not look for a servlet at {0} as it is not in the list of allowed paths",
                                    scriptNameOrResourceType);
                }
            }
        }
        if (servlet == null) {
            // the resource type is not absolute, so lets go for the deep search
            final AbstractResourceCollector locationUtil;
            if (request != null) {
                locationUtil = ResourceCollector.create(
                        request, this.executionPaths.get(), this.defaultExtensions.get(), this.useResourceCaching);
            } else {
                locationUtil = NamedScriptResourceCollector.create(
                        scriptNameOrResourceType, resource, this.executionPaths.get(), this.useResourceCaching);
            }
            servlet = getServletInternal(locationUtil, request, resolver);

            if (servlet != null && LOGGER.isDebugEnabled()) {
                LOGGER.debug("getServletInternal returns servlet {}", RequestUtil.getServletName(servlet));
            }
        }
        return servlet;
    }

    /**
     * Returns a servlet suitable for handling a request. The
     * <code>locationUtil</code> is used find any servlets or scripts usable for
     * the request. Each servlet returned is in turn asked whether it is
     * actually willing to handle the request in case the servlet is an
     * <code>OptingServlet</code>. The first servlet willing to handle the
     * request is used.
     *
     * @param locationUtil The helper used to find appropriate servlets ordered
     *            by matching priority.
     * @param request The request used to give to any <code>OptingServlet</code>
     *            for them to decide on whether they are willing to handle the
     *            request
     * @param resolver The <code>ResourceResolver</code> used for resolving the servlets.
     * @return a servlet for handling the request or <code>null</code> if no
     *         such servlet willing to handle the request could be found.
     */
    private Servlet getServletInternal(
            final AbstractResourceCollector locationUtil,
            final SlingJakartaHttpServletRequest request,
            final ResourceResolver resolver) {
        // use local variable to avoid race condition with activate
        final ResolutionCache localCache = this.resolutionCache;
        final Servlet scriptServlet = localCache.get(locationUtil);
        if (scriptServlet != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Using cached servlet {}", RequestUtil.getServletName(scriptServlet));
            }
            return scriptServlet;
        }

        final Collection<Resource> candidates =
                locationUtil.getServlets(resolver, localCache.getScriptEngineExtensions());
        candidates.removeIf(r -> ignoreResource(r));

        if (LOGGER.isDebugEnabled()) {
            if (candidates.isEmpty()) {
                LOGGER.debug("No servlet candidates found");
            } else {
                LOGGER.debug("Ordered list of servlet candidates follows");
                for (Resource candidateResource : candidates) {
                    LOGGER.debug("Servlet candidate: {}", candidateResource.getPath());
                }
            }
        }

        boolean hasOptingServlet = false;
        for (final Resource candidateResource : candidates) {
            LOGGER.debug(
                    "Checking if candidate resource {} adapts to servlet and accepts request",
                    candidateResource.getPath());
            Servlet candidate = this.getServlet(candidateResource);
            if (candidate != null) {
                final boolean isOptingServlet = candidate instanceof JakartaOptingServlet;
                boolean servletAcceptsRequest =
                        !isOptingServlet || (request != null && ((JakartaOptingServlet) candidate).accepts(request));
                if (servletAcceptsRequest) {
                    if (!hasOptingServlet && !isOptingServlet) {
                        localCache.put(locationUtil, candidate);
                    }
                    LOGGER.debug("Using servlet provided by candidate resource {}", candidateResource.getPath());
                    return candidate;
                }
                if (isOptingServlet) {
                    hasOptingServlet = true;
                }
                LOGGER.debug("Candidate {} does not accept request, ignored", candidateResource.getPath());
            } else {
                LOGGER.debug("Candidate {} does not adapt to a servlet, ignored", candidateResource.getPath());
            }
        }

        // exhausted all candidates, we don't have a servlet
        return null;
    }

    /**
     * Returns the internal default servlet which is called in case no other
     * servlet applies for handling a request. This servlet should really only
     * be used if the default servlets have not been registered (yet).
     */
    private Servlet getDefaultServlet() {
        Servlet servlet = defaultServlet.get();
        if (servlet == null) {
            try {
                servlet = new DefaultServlet();
                servlet.init(new SlingServletConfig(servletContext, null, "Apache Sling Core Default Servlet"));
                defaultServlet.set(servlet);
            } catch (final ServletException se) {
                LOGGER.error("Failed to initialize default servlet", se);
            }
        }

        return servlet;
    }

    /**
     * Returns the default error handler servlet, which is called in case there
     * is no other - better matching - servlet registered to handle an error or
     * exception.
     * <p>
     * The default error handler servlet is registered for the resource type
     * "sling/servlet/errorhandler" and method "default". This may be
     * overwritten by applications globally or according to the resource type
     * hierarchy of the resource.
     * <p>
     * If no default error handler servlet can be found an adhoc error handler
     * is used as a final fallback.
     */
    private Servlet getDefaultErrorServlet(
            final SlingJakartaHttpServletRequest request, final Resource resource, final ResourceResolver resolver) {

        // find a default error handler according to the resource type
        // tree of the given resource
        String extension = request.getRequestPathInfo().getExtension();
        final ResourceCollector locationUtil = new ResourceCollector(
                ServletResolverConstants.DEFAULT_ERROR_HANDLER_METHOD,
                DEFAULT_ERROR_HANDLER_RESOURCE_TYPE,
                resource,
                extension,
                this.executionPaths.get(),
                this.useResourceCaching);
        final Servlet servlet = getServletInternal(locationUtil, request, resolver);
        if (servlet != null) {
            return servlet;
        }

        // if no registered default error handler could be found use
        // the DefaultErrorHandlerServlet as an ad-hoc fallback
        Servlet fallbackServlet = fallbackErrorServlet.get();
        if (fallbackServlet == null) {
            // fall back to an adhoc instance of the DefaultErrorHandlerServlet
            // if the actual service is not registered (yet ?)
            try {
                fallbackServlet = new DefaultErrorHandlerServlet();
                fallbackServlet.init(
                        new SlingServletConfig(servletContext, null, "Sling (Ad Hoc) Default Error Handler Servlet"));
                fallbackErrorServlet.set(fallbackServlet);
            } catch (ServletException se) {
                LOGGER.error("Failed to initialize error servlet", se);
            }
        }
        return fallbackServlet;
    }

    private void handleError(
            final Servlet errorHandler,
            final SlingJakartaHttpServletRequest request,
            final SlingJakartaHttpServletResponse response)
            throws IOException {

        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, request.getRequestURI());

        // if there is no explicitly known error causing servlet, use
        // the name of the error handler servlet
        if (request.getAttribute(RequestDispatcher.ERROR_SERVLET_NAME) == null) {
            request.setAttribute(
                    RequestDispatcher.ERROR_SERVLET_NAME,
                    errorHandler.getServletConfig().getServletName());
        }

        // Let the error handler servlet process the request and
        // forward all exceptions if it fails.
        // Before SLING-4143 we only forwarded IOExceptions.
        try {
            // SLING-10478 - wrap the response to track if the writer is still open
            // after the errorHandler has serviced the request
            HandleErrorSlingHttpServletResponse wrappedResponse = new HandleErrorSlingHttpServletResponse(response);

            errorHandler.service(request, wrappedResponse);

            // SLING-10478 - if the response writer has not already been closed, then flush and close it
            if (wrappedResponse.isOpen()) {
                // commit the response
                wrappedResponse.flushBuffer();
                // close the response (SLING-2724)
                wrappedResponse.getWriter().close();
            }
        } catch (final Throwable t) { // NOSONAR
            LOGGER.error("Calling the error handler resulted in an error", t);
            LOGGER.error("Original error " + request.getAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE), (Throwable)
                    request.getAttribute(RequestDispatcher.ERROR_EXCEPTION));
            final IOException x =
                    new IOException("Error handler failed: " + t.getClass().getName());
            x.initCause(t);
            throw x;
        }
    }

    // ---------- SCR Integration ----------------------------------------------

    private ServiceTracker<MergingServletResourceProvider, MergingServletResourceProvider> tracker;
    /**
     * Activate this component.
     */
    @Activate
    protected void activate(final BundleContext context, final ResolverConfig config) throws LoginException {
        this.tracker = new ServiceTracker<>(context, MergingServletResourceProvider.class, null);
        this.tracker.open();
        this.sharedScriptResolver.set(ScriptResourceResolver.wrap(
                resourceResolverFactory.getServiceResourceResolver(
                        Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, (Object) SERVICE_USER)),
                this.tracker::getService));

        this.executionPaths.set(getExecutionPaths(config.servletresolver_paths()));
        this.defaultExtensions.set(Arrays.asList(config.servletresolver_defaultExtensions()));
        this.useResourceCaching = config.enable_resource_caching();

        // setup default servlet
        this.getDefaultServlet();
    }

    @Modified
    protected void modified(final BundleContext context, final ResolverConfig config) throws LoginException {
        this.deactivate();
        this.activate(context, config);
    }

    /**
     * Deactivate this component.
     */
    @Deactivate
    protected void deactivate() {
        this.tracker.close();
        this.resolutionCache.flushCache();
        // destroy the fallback error handler servlet
        Servlet servlet = fallbackErrorServlet.get();
        if (servlet != null) {
            try {
                servlet.destroy();
            } catch (Throwable t) { // NOSONAR
                // ignore
            } finally {
                fallbackErrorServlet.set(null);
            }
        }

        if (this.sharedScriptResolver != null) {
            ResourceResolver rr = this.sharedScriptResolver.get();
            if (rr != null) {
                rr.close();
            }
            this.sharedScriptResolver.set(null);
        }

        this.executionPaths.set(null);
        this.defaultExtensions.set(null);
    }

    /**
     * Reject paths with more than two consecutive dots
     * @param path The path
     * @return {@code true} if the path is invalid
     */
    public static boolean isInvalidPath(final String path) {
        int index = 0;
        while (index < path.length()) {
            int charCount = 0;
            int dotCount = 0;
            // count dots (".") and total chars in each path segment (between two '/')
            while (index < path.length() && path.charAt(index) != '/') {
                if (path.charAt(index) == '.') {
                    dotCount++;
                }
                charCount++;
                index++;
            }
            // if all chars are dots (".") and there are more than two dots, then the path is rejected
            if (charCount > 2 && dotCount == charCount) {
                return true;
            }
            index++;
        }
        return false;
    }

    /**
     * This method checks whether a path is allowed to be executed.
     *
     * @param path The path to check (must not be {@code null} or empty)
     * @param executionPaths The path to check against
     * @return {@code true} if the executionPaths is {@code null} or empty or if
     *         the path equals one entry or one of the executionPaths entries is
     *         a prefix to the path. Otherwise or if path is {@code null}
     *         {@code false} is returned.
     */
    public static boolean isPathAllowed(final String path, final String[] executionPaths) {
        if (executionPaths == null || executionPaths.length == 0) {
            LOGGER.debug("Accepting servlet at '{}' as there are no configured execution paths.", path);
            return true;
        }

        if (path == null || path.length() == 0) {
            LOGGER.debug("Ignoring servlet with empty path.");
            return false;
        }

        for (final String config : executionPaths) {
            if (config.endsWith("/")) {
                if (path.startsWith(config)) {
                    LOGGER.debug(
                            "Accepting servlet at '{}' as the path is prefixed with configured execution path '{}'.",
                            path,
                            config);
                    return true;
                }
            } else if (path.equals(config)) {
                LOGGER.debug(
                        "Accepting servlet at '{}' as the path equals configured execution path '{}'.", path, config);
                return true;
            }
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Ignoring servlet at '{}' as the path is not in the configured execution paths.", path);
        }

        return false;
    }

    /**
     * Calculate the execution paths from the configured execution paths
     * @param paths The configured paths
     * @return The execution paths or {@code null} for all paths.
     */
    public static String[] getExecutionPaths(final String[] paths) {
        String[] executionPaths = paths;
        if (executionPaths != null) {
            // if we find a string combination that basically allows all paths,
            // we simply set the array to null
            if (executionPaths.length == 0) {
                executionPaths = null;
            } else {
                boolean hasRoot = false;
                for (final String path : executionPaths) {
                    if (path == null || path.length() == 0 || path.equals("/")) {
                        hasRoot = true;
                        break;
                    }
                }
                if (hasRoot) {
                    executionPaths = null;
                }
            }
        }
        return executionPaths;
    }

    protected void invalidateCache(ResourceResolver r) {
        LocationCollector.clearCache(r);
        AbstractResourceCollector.clearCache(r);
    }

    @Override
    @Deprecated
    public javax.servlet.Servlet resolveServlet(final SlingHttpServletRequest request) {
        return ServletWrapperUtil.toJavaxServlet(this.resolve(new JavaxToJakartaRequestWrapper(request)));
    }

    @Override
    public javax.servlet.Servlet resolveServlet(final Resource resource, final String scriptName) {
        return ServletWrapperUtil.toJavaxServlet(this.resolve(resource, scriptName));
    }

    @Override
    public javax.servlet.Servlet resolveServlet(final ResourceResolver resolver, final String scriptName) {
        return ServletWrapperUtil.toJavaxServlet(this.resolve(resolver, scriptName));
    }
}
