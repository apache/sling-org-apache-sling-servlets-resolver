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
package org.apache.sling.servlets.resolver.internal.resolution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.servlet.Servlet;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.observation.ExternalResourceChangeListener;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChangeListener;
import org.apache.sling.api.resource.path.Path;
import org.apache.sling.servlets.resolver.internal.ResolverConfig;
import org.apache.sling.servlets.resolver.internal.helper.AbstractResourceCollector;
import org.apache.sling.servlets.resolver.jmx.SlingServletResolverCacheMBean;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cache for script resolution
 *
 */
@Component(configurationPid = ResolverConfig.PID,
           service = {ResolutionCache.class})
public class ResolutionCache
    implements EventHandler, ResourceChangeListener, ExternalResourceChangeListener {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Reference
    private ScriptEngineManager scriptEngineManager;

    private volatile List<String> scriptEnginesExtensions = Collections.emptyList();

    /** The script resolution cache. */
    private volatile Map<AbstractResourceCollector, Servlet> cache;

    /** The cache size. */
    private volatile int cacheSize;

    /** Flag to log warning if cache size exceed only once. */
    private volatile boolean logCacheSizeWarning;

    /** Registration as event handler. */
    private volatile ServiceRegistration<EventHandler> eventHandlerRegistration;

    private volatile ServiceRegistration<ResourceChangeListener> resourceListenerRegistration;

    private volatile ServiceRegistration<SlingServletResolverCacheMBean> mbeanRegistration;

    /**
     * Activate this component.
     */
    @Activate
    protected void activate(final BundleContext context,
            final ResolverConfig config) throws LoginException {
        // create cache - if a cache size is configured
        this.cacheSize = config.servletresolver_cacheSize();
        if (this.cacheSize > 5) {
            this.cache = new ConcurrentHashMap<>(cacheSize);
            this.logCacheSizeWarning = true;

            // register MBean
            try {
                Dictionary<String, String> mbeanProps = new Hashtable<>();
                mbeanProps.put("jmx.objectname", "org.apache.sling:type=servletResolver,service=SlingServletResolverCache");

                ServletResolverCacheMBeanImpl mbean = new ServletResolverCacheMBeanImpl();
                mbeanRegistration = context.registerService(SlingServletResolverCacheMBean.class, mbean, mbeanProps);
            } catch (final Throwable t) {
                logger.warn("Unable to register servlets resolver cache MBean", t);
            }
        }

        // and finally register as event listener
        // to invalidate cache and script extensions
        final Dictionary<String, Object> props = new Hashtable<>();
        props.put(Constants.SERVICE_DESCRIPTION, "Apache Sling Servlet Resolver Event Handler");
        props.put(Constants.SERVICE_VENDOR,"The Apache Software Foundation");

        // the event listener is for updating the script engine extensions
        props.put(EventConstants.EVENT_TOPIC, new String[] {
                "javax/script/ScriptEngineFactory/*",
                "org/apache/sling/api/adapter/AdapterFactory/*",
                "org/apache/sling/scripting/core/BindingsValuesProvider/*" });

        this.eventHandlerRegistration = context.registerService(EventHandler.class, this, props);

        // we need a resource change listener to invalidate the cache
        if ( this.cache != null ) {
            final String[] listenerPaths = new String[config.servletresolver_paths().length];
            for(int i=0; i<config.servletresolver_paths().length; i++) {
                final Path p = new Path(config.servletresolver_paths()[i]);
                listenerPaths[i] = p.getPath();
            }

            final Dictionary<String, Object> listenerProps = new Hashtable<>();
            listenerProps.put(Constants.SERVICE_DESCRIPTION, "Apache Sling Servlet Resolver Resource Listener");
            listenerProps.put(Constants.SERVICE_VENDOR,"The Apache Software Foundation");
            listenerProps.put(ResourceChangeListener.PATHS, listenerPaths);
            this.resourceListenerRegistration = context.registerService(ResourceChangeListener.class, this, listenerProps);
        }

        updateScriptEngineExtensions();
    }

    @Modified
    protected void modified(final BundleContext context,
            final ResolverConfig config) throws LoginException {
        this.deactivate();
        this.activate(context, config);
    }

    /**
     * Deactivate this component.
     */
    @Deactivate
    protected void deactivate() {
        this.cache = null;

        // unregister mbean
        if ( this.mbeanRegistration != null ) {
            this.mbeanRegistration.unregister();
            this.mbeanRegistration = null;
        }

        // unregister event handler
        if (this.eventHandlerRegistration != null) {
            this.eventHandlerRegistration.unregister();
            this.eventHandlerRegistration = null;
        }

        // unregister event handler
        if (this.resourceListenerRegistration != null) {
            this.resourceListenerRegistration.unregister();
            this.resourceListenerRegistration = null;
        }
    }

    /**
     * Get the list of script engine extensions
     * @return The list of script engine extensions
     */
    public List<String> getScriptEngineExtensions() {
        return this.scriptEnginesExtensions;
    }

    private void updateScriptEngineExtensions() {
        final ScriptEngineManager localScriptEngineManager = scriptEngineManager;
        // use local variable to avoid racing with deactivate
        if ( localScriptEngineManager != null ) {
            final List<String> scriptEnginesExtensions = new ArrayList<>();
            for (ScriptEngineFactory factory : localScriptEngineManager.getEngineFactories()) {
                scriptEnginesExtensions.addAll(factory.getExtensions());
            }
            this.scriptEnginesExtensions = Collections.unmodifiableList(scriptEnginesExtensions);
        }
    }

    /**
     * @see org.osgi.service.event.EventHandler#handleEvent(org.osgi.service.event.Event)
     */
    @Override
    public void handleEvent(final Event event) {
        // return immediately if already deactivated
        if ( this.eventHandlerRegistration == null ) {
            return;
        }
        flushCache();
        updateScriptEngineExtensions();
    }

    public void flushCache() {
        // use local variable to avoid racing with deactivate
        final Map<AbstractResourceCollector, Servlet> localCache = this.cache;
        if ( localCache != null ) {
            localCache.clear();
            this.logCacheSizeWarning = true;
        }
    }

    @Override
	public void onChange(final List<ResourceChange> changes) {
        // return immediately if already deactivated
        if ( resourceListenerRegistration == null || changes.isEmpty() ) {
            return;
        }
        // we invalidate the cache once, regardless of the number of changes
        flushCache();
    }

    class ServletResolverCacheMBeanImpl extends StandardMBean implements SlingServletResolverCacheMBean {

        ServletResolverCacheMBeanImpl() throws NotCompliantMBeanException {
            super(SlingServletResolverCacheMBean.class);
        }

        @Override
        public int getCacheSize() {
            // use local variable to avoid racing with deactivate
            final Map<AbstractResourceCollector, Servlet> localCache = cache;
            return localCache != null ? localCache.size() : 0;
        }

        @Override
        public void flushCache() {
            ResolutionCache.this.flushCache();
        }

        @Override
        public int getMaximumCacheSize() {
            return cacheSize;
        }

    }

    public Servlet get(final AbstractResourceCollector context) {
        final Map<AbstractResourceCollector, Servlet> localCache = this.cache;
        if ( localCache != null ) {
            return localCache.get(context);
        }
        return null;
    }

    public void put(final AbstractResourceCollector context, final Servlet candidate) {
        final Map<AbstractResourceCollector, Servlet> localCache = this.cache;
        if ( localCache != null ) {
            if ( localCache.size() < this.cacheSize ) {
                localCache.put(context, candidate);
            } else if ( this.logCacheSizeWarning ) {
                this.logCacheSizeWarning = false;
                logger.warn("Script cache has reached its limit of {}. You might want to increase the cache size for the servlet resolver.",
                    this.cacheSize);
            }
        }
    }
}
