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

import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

import org.osgi.framework.ServiceReference;

public class SlingServletConfig implements ServletConfig {

    /** The <code>ServletContext</code> of this configuration object */
    private final ServletContext servletContext;

    /** The <code>ServiceReference</code> providing the properties */
    private final ServiceReference<Servlet> reference;

    /** The name of this configuration object */
    private final String name;

    /**
     * Sets up this base configuration object.
     *
     * @param servletContext The <code>ServletContext</code> attached to this
     *            configuration.
     * @param reference The service reference providing the initialization
     *            parameter values.
     * @param name The name of this configuration.
     */
    public SlingServletConfig(final ServletContext servletContext,
            final ServiceReference<Servlet> reference, final String name) {
        this.servletContext = servletContext;
        this.reference = reference;
        this.name = name;
    }

    /**
     * @see javax.servlet.ServletConfig#getInitParameter(java.lang.String)
     */
    @Override
    public String getInitParameter(final String name) {
        final Object prop = reference.getProperty(name);
        return (prop == null) ? null : String.valueOf(prop);
    }

    /**
     * @see javax.servlet.ServletConfig#getInitParameterNames()
     */
    @Override
    public Enumeration<String> getInitParameterNames() {
        final List<String> keys = Arrays.asList(reference.getPropertyKeys());
        return Collections.enumeration(keys);
    }

    /**
     * @see javax.servlet.ServletConfig#getServletContext()
     */
    @Override
    public ServletContext getServletContext() {
        return servletContext;
    }

    /**
     * @see javax.servlet.ServletConfig#getServletName()
     */
    @Override
    public String getServletName() {
        return this.name;
    }

    /** @return the value of an OSGi service property of this servlet
     *  (which can be an array that getInitParameter* messes up)
     */
    public Object getServiceProperty(String key) {
        return reference.getProperty(key);
    }

    /** @return the OSGi service property keys of this servlet
     *  (which can be an array that getInitParameter* messes up)
    */
    public String [] getServicePropertyKeys() {
        return reference.getPropertyKeys();
    }
}