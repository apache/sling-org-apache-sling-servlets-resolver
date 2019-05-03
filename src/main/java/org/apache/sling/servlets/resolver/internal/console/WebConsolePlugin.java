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
package org.apache.sling.servlets.resolver.internal.console;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.request.ResponseUtil;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.SyntheticResource;
import org.apache.sling.api.scripting.SlingScript;
import org.apache.sling.api.servlets.OptingServlet;
import org.apache.sling.engine.impl.request.SlingRequestPathInfo;
import org.apache.sling.serviceusermapping.ServiceUserMapped;
import org.apache.sling.servlets.resolver.internal.ResolverConfig;
import org.apache.sling.servlets.resolver.internal.SlingServletResolver;
import org.apache.sling.servlets.resolver.internal.helper.ResourceCollector;
import org.apache.sling.servlets.resolver.internal.resolution.ResolutionCache;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;

@SuppressWarnings("serial")
@Component(service = {Servlet.class},
  configurationPid = ResolverConfig.PID,
  property = {
          Constants.SERVICE_DESCRIPTION + "=Sling Servlet Resolver Web Console Plugin",
          Constants.SERVICE_VENDOR + "=The Apache Software Foundation",
          "felix.webconsole.label=servletresolver",
          "felix.webconsole.title=Sling Servlet Resolver",
          "felix.webconsole.css=/servletresolver/res/ui/styles.css",
          "felix.webconsole.category=Sling"
  })
public class WebConsolePlugin extends HttpServlet {

    private static final String PARAMETER_URL = "url";
    private static final String PARAMETER_METHOD = "method";

    private static final String SERVICE_USER_CONSOLE = "console";

    @Reference(target="("+ServiceUserMapped.SUBSERVICENAME+"=" + SERVICE_USER_CONSOLE + ")")
    private ServiceUserMapped consoleServiceUserMapped;

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Reference
    private ResolutionCache resolutionCache;

    /**
     * The allowed execution paths.
     */
    private volatile String[] executionPaths;

    /**
     * The default extensions
     */
    private volatile String[] defaultExtensions;

    /**
     * Activate this component.
     */
    @Activate
    @Modified
    protected void activate(final ResolverConfig config) {
        this.executionPaths = SlingServletResolver.getExecutionPaths(config.servletresolver_paths());
        this.defaultExtensions = config.servletresolver_defaultExtensions();
    }

    @Override
    protected void service(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        final String url = request.getParameter(PARAMETER_URL);
        final RequestPathInfo requestPathInfo = getRequestPathInfo(url);
        String method = request.getParameter(PARAMETER_METHOD);
        if (StringUtils.isBlank(method)) {
            method = "GET";
        }

        final String CONSOLE_PATH_WARNING =
                "<em>"
                + "Note that in a real Sling request, the path might vary depending on the existence of"
                + " resources that partially match it."
                + "<br/>This utility does not take this into account and uses the first dot to split"
                + " between path and selectors/extension."
                + "<br/>As a workaround, you can replace dots with underline characters, for example, when testing such an URL."
                + "</em>";

        try (final ResourceResolver resourceResolver = resourceResolverFactory.getServiceResourceResolver(Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, (Object)SERVICE_USER_CONSOLE))) {

            final PrintWriter pw = response.getWriter();

            pw.print("<form method='get'>");
            pw.println("<table class='content' cellpadding='0' cellspacing='0' width='100%'>");

            titleHtml(
                    pw,
                    "Servlet Resolver Test",
                    "To check which servlet is responsible for rendering a response, enter a request path into " +
                             "the field and click 'Resolve' to resolve it.");

            tr(pw);
            tdLabel(pw, "URL");
            tdContent(pw);

            pw.print("<input type='text' name='");
            pw.print(PARAMETER_URL);
            pw.print("' value='");
            if ( url != null ) {
                pw.print(ResponseUtil.escapeXml(url));
            }
            pw.println("' class='input' size='50'>");
            closeTd(pw);
            closeTr(pw);
            closeTr(pw);

            tr(pw);
            tdLabel(pw, "Method");
            tdContent(pw);
            pw.print("<select name='");
            pw.print(PARAMETER_METHOD);
            pw.println("'>");
            pw.println("<option value='GET'>GET</option>");
            pw.println("<option value='POST'>POST</option>");
            pw.println("</select>");
            pw.println("&nbsp;&nbsp;<input type='submit' value='Resolve' class='submit'>");

            closeTd(pw);
            closeTr(pw);

            if (StringUtils.isNotBlank(url)) {
                tr(pw);
                tdLabel(pw, "Decomposed URL");
                tdContent(pw);
                pw.println("<dl>");
                pw.println("<dt>Path</dt>");
                pw.print("<dd>");
                pw.print(ResponseUtil.escapeXml(requestPathInfo.getResourcePath()));
                pw.print("<br/>");
                pw.print(CONSOLE_PATH_WARNING);
                pw.println("</dd>");
                pw.println("<dt>Selectors</dt>");
                pw.print("<dd>");
                if (requestPathInfo.getSelectors().length == 0) {
                    pw.print("&lt;none&gt;");
                } else {
                    pw.print("[");
                    pw.print(ResponseUtil.escapeXml(StringUtils.join(requestPathInfo.getSelectors(), ", ")));
                    pw.print("]");
                }
                pw.println("</dd>");
                pw.println("<dt>Extension</dt>");
                pw.print("<dd>");
                pw.print(ResponseUtil.escapeXml(requestPathInfo.getExtension()));
                pw.println("</dd>");
                pw.println("</dl>");
                pw.println("</dd>");
                pw.println("<dt>Suffix</dt>");
                pw.print("<dd>");
                pw.print(ResponseUtil.escapeXml(requestPathInfo.getSuffix()));
                pw.println("</dd>");
                pw.println("</dl>");
                closeTd(pw);
                closeTr(pw);
            }

            if (StringUtils.isNotBlank(requestPathInfo.getResourcePath())) {
                final Collection<Resource> servlets;
                Resource resource = resourceResolver.resolve(requestPathInfo.getResourcePath());
                if (resource.adaptTo(Servlet.class) != null) {
                    servlets = Collections.singleton(resource);
                } else {
                    final ResourceCollector locationUtil = ResourceCollector.create(
                            resource,
                            requestPathInfo.getExtension(),
                            executionPaths,
                            defaultExtensions,
                            method,
                            requestPathInfo.getSelectors());
                    servlets = locationUtil.getServlets(resourceResolver, resolutionCache.getScriptEngineExtensions());
                }
                tr(pw);
                tdLabel(pw, "Candidates");
                tdContent(pw);

                if (servlets == null || servlets.isEmpty()) {
                    pw.println("Could not find a suitable servlet for this request!");
                } else {
                    // check for non-existing resources
                    if (ResourceUtil.isNonExistingResource(resource)) {
                        pw.println("The resource given by path '");
                        pw.println(ResponseUtil.escapeXml(resource.getPath()));
                        pw.println("' does not exist. Therefore no resource type could be determined!<br/>");
                    }
                    pw.print("Candidate servlets and scripts in order of preference for method ");
                    pw.print(ResponseUtil.escapeXml(method));
                    pw.println(":<br/>");
                    pw.println("<ol class='servlets'>");
                    outputServlets(pw, servlets.iterator());
                    pw.println("</ol>");
                }
                pw.println("</td>");
                closeTr(pw);
            }

            pw.println("</table>");
            pw.print("</form>");
        } catch (final LoginException e) {
            throw new ServletException(e);
        }
    }

    private void tdContent(final PrintWriter pw) {
        pw.print("<td class='content' colspan='2'>");
    }

    private void closeTd(final PrintWriter pw) {
        pw.print("</td>");
    }

    @SuppressWarnings("unused")
    private URL getResource(final String path) {
        if (path.startsWith("/servletresolver/res/ui")) {
            return this.getClass().getResource(path.substring(16));
        } else {
            return null;
        }
    }

    private void closeTr(final PrintWriter pw) {
        pw.println("</tr>");
    }

    private void tdLabel(final PrintWriter pw, final String label) {
        pw.print("<td class='content'>");
        pw.print(ResponseUtil.escapeXml(label));
        pw.println("</td>");
    }

    private void tr(final PrintWriter pw) {
        pw.println("<tr class='content'>");
    }

    private void outputServlets(final PrintWriter pw, final Iterator<Resource> iterator) {
        while (iterator.hasNext()) {
            Resource candidateResource = iterator.next();
            Servlet candidate = candidateResource.adaptTo(Servlet.class);
            if (candidate != null) {
                final boolean allowed = SlingServletResolver.isPathAllowed(candidateResource.getPath(), this.executionPaths);
                pw.print("<li>");
                if ( !allowed ) {
                    pw.print("<del>");
                }

                if (candidate instanceof SlingScript) {
                    pw.print(ResponseUtil.escapeXml(candidateResource.getPath()));
                } else {
                    final boolean isOptingServlet = candidate instanceof OptingServlet;
                    pw.print(ResponseUtil.escapeXml((candidate.getClass().getName())));
                    if ( isOptingServlet ) {
                        pw.print(" (OptingServlet)");
                    }
                }

                if ( !allowed ) {
                    pw.print("</del>");
                }
                pw.println("</li>");
            }
        }
    }

    private void titleHtml(final PrintWriter pw, final String title, final String description) {
        tr(pw);
        pw.print("<th colspan='3' class='content container'>");
        pw.print(ResponseUtil.escapeXml(title));
        pw.println("</th>");
        closeTr(pw);

        if (description != null) {
            tr(pw);
            pw.print("<td colspan='3' class='content'>");
            pw.print(ResponseUtil.escapeXml(description));
            pw.println("</th>");
            closeTr(pw);
        }
    }

    public static RequestPathInfo getRequestPathInfo(String urlString) {

        if(urlString == null) {
            urlString = "";
        }

        // For the path, take everything up to the first dot
        String fullPath = urlString;
        if(urlString.contains("http")) {
            try {
                fullPath = new URL(urlString).getPath();
            } catch(MalformedURLException ignore) {
            }
        }
        final int firstDot = fullPath.indexOf(".");

        final ResourceMetadata metadata = new ResourceMetadata();
        final Resource r = new SyntheticResource(null, metadata, null);
        metadata.setResolutionPath(firstDot < 0 ? fullPath : fullPath.substring(0, firstDot));
        metadata.setResolutionPathInfo(firstDot < 0 ? null : fullPath.substring(firstDot));
        return new SlingRequestPathInfo(r);
    }
}
