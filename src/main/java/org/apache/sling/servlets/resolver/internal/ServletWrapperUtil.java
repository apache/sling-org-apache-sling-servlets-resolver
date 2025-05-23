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

import jakarta.servlet.Servlet;
import org.apache.sling.api.servlets.JakartaOptingServlet;
import org.apache.sling.api.servlets.OptingServlet;
import org.apache.sling.api.wrappers.JakartaToJavaxServletWrapper;
import org.apache.sling.api.wrappers.JavaxToJakartaServletWrapper;

public class ServletWrapperUtil {

    public static javax.servlet.Servlet toJavaxServlet(final Servlet servlet) {
        if (servlet == null) {
            return null;
        }
        if (servlet instanceof JakartaScriptServletWrapper) {
            return ((JakartaScriptServletWrapper) servlet).servlet;
        } else if (servlet instanceof JakartaScriptOptingServletWrapper) {
            return ((JakartaScriptOptingServletWrapper) servlet).servlet;
        }
        final javax.servlet.Servlet s;
        if (servlet instanceof JakartaOptingServlet) {
            s = new ScriptOptingServletWrapper((JakartaOptingServlet) servlet);
        } else {
            s = new ScriptServletWrapper(servlet);
        }
        return s;
    }

    @SuppressWarnings("deprecation")
    public static Servlet toJakartaServlet(final javax.servlet.Servlet servlet) {
        if (servlet == null) {
            return null;
        }
        if (servlet instanceof ScriptServletWrapper) {
            return ((ScriptServletWrapper) servlet).servlet;
        } else if (servlet instanceof ScriptOptingServletWrapper) {
            return ((ScriptOptingServletWrapper) servlet).servlet;
        }
        final Servlet s;
        if (servlet instanceof OptingServlet) {
            s = new JakartaScriptOptingServletWrapper((OptingServlet) servlet);
        } else {
            s = new JakartaScriptServletWrapper(servlet);
        }
        return s;
    }

    public static class ScriptServletWrapper extends JakartaToJavaxServletWrapper {
        public final Servlet servlet;

        public ScriptServletWrapper(final Servlet servlet) {
            super(servlet);
            this.servlet = servlet;
        }
    }

    public static class ScriptOptingServletWrapper
            extends JakartaToJavaxServletWrapper.JakartaToJavaxOptingServletWrapper {
        public final Servlet servlet;

        public ScriptOptingServletWrapper(final JakartaOptingServlet servlet) {
            super(servlet);
            this.servlet = servlet;
        }
    }

    public static class JakartaScriptServletWrapper extends JavaxToJakartaServletWrapper {
        public final javax.servlet.Servlet servlet;

        public JakartaScriptServletWrapper(final javax.servlet.Servlet servlet) {
            super(servlet);
            this.servlet = servlet;
        }
    }

    public static class JakartaScriptOptingServletWrapper
            extends JavaxToJakartaServletWrapper.JavaxToJakartaOptingServletWrapper {
        public final javax.servlet.Servlet servlet;

        @SuppressWarnings("deprecation")
        public JakartaScriptOptingServletWrapper(final OptingServlet servlet) {
            super(servlet);
            this.servlet = servlet;
        }
    }
}
