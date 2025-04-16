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
package org.apache.sling.servlets.resolver.internal.helper;

import java.util.Dictionary;
import java.util.List;

import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

@Component
public class SearchPathProvider {

    private final BundleContext bundleContext;

    private volatile ServiceRegistration<SearchPathProvider> serviceRegistration;

    private volatile List<String> searchPaths;

    @Activate
    public SearchPathProvider(final BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    @Deactivate
    public void deactivate() {
        if (this.serviceRegistration != null) {
            try {
                this.serviceRegistration.unregister();
            } catch (final IllegalStateException ise) {
                // ignore on shutdown
            }
            this.serviceRegistration = null;
        }
    }

    @Reference(policy = ReferencePolicy.DYNAMIC, cardinality = ReferenceCardinality.OPTIONAL)
    protected void bindResourceResolverFactory(final ResourceResolverFactory factory) {
        if (this.searchPaths == null || !this.searchPaths.equals(factory.getSearchPath())) {
            this.searchPaths = factory.getSearchPath();
            final Dictionary<String, Object> props = new java.util.Hashtable<>();
            props.put("paths", this.searchPaths.toArray(new String[this.searchPaths.size()]));
            if (this.serviceRegistration != null) {
                this.serviceRegistration.setProperties(props);
            } else {
                this.serviceRegistration = this.bundleContext.registerService(SearchPathProvider.class, this, props);
            }
        }
    }

    protected void unbindResourceResolverFactory(final ResourceResolverFactory factory) {
        // nothing to do
    }

    public List<String> getSearchPaths() {
        return this.searchPaths;
    }
}
