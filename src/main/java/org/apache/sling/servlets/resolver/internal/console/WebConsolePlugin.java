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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringEscapeUtils;
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
import org.apache.sling.servlets.resolver.internal.bundle.BundledScriptServlet;
import org.apache.sling.servlets.resolver.internal.helper.ResourceCollector;
import org.apache.sling.servlets.resolver.internal.resolution.ResolutionCache;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;

/**
 * If the servlet request path ends with .json, the information is returned in JSON format.
 * Otherwise, an HTML code is returned.
 */
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
    private static final String CONSOLE_PATH_WARNING =
                    "Note that in a real Sling request, the path might vary depending on the existence of"
                    + " resources that partially match it. "
                    + "<br/>This utility does not take this into account and uses the first dot to split"
                    + " between path and selectors/extension. "
                    + "<br/>As a workaround, you can replace dots with underline characters, for example, when " +
                            "testing such a URL.";

    @Reference(target="("+ServiceUserMapped.SUBSERVICENAME+"=" + SERVICE_USER_CONSOLE + ")")
    private ServiceUserMapped consoleServiceUserMapped; // NOSONAR

    @Reference
    private ResourceResolverFactory resourceResolverFactory; // NOSONAR

    @Reference
    private ResolutionCache resolutionCache; // NOSONAR

    /**
     * The allowed execution paths.
     */
    private AtomicReference<String[]> executionPaths = new AtomicReference<>(); // NOSONAR

    /**
     * The default extensions
     */
    private AtomicReference<String[]> defaultExtensions = new AtomicReference<>(); // NOSONAR

    /**
     * Activate this component.
     */
    @Activate
    @Modified
    protected void activate(final ResolverConfig config) {
        this.executionPaths.set(SlingServletResolver.getExecutionPaths(config.servletresolver_paths()));
        this.defaultExtensions.set(config.servletresolver_defaultExtensions());
    }

    @Override
    protected void service(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        final String url = request.getParameter(PARAMETER_URL);
        final RequestPathInfo requestPathInfo = getRequestPathInfo(url);
        String method = request.getParameter(PARAMETER_METHOD);
        if (StringUtils.isBlank(method)) {
            method = "GET";
        }

        String requestURI = request.getRequestURI();
        try (final ResourceResolver resourceResolver = resourceResolverFactory.getServiceResourceResolver(Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, (Object)SERVICE_USER_CONSOLE))) {
            final PrintWriter pw = response.getWriter();

            if (requestURI.endsWith("json")) {
                pw.println("{");
                if (StringUtils.isNotBlank(url)) {
                    printJSONDecomposedURLElement(pw, requestPathInfo);
                }
                if (StringUtils.isNotBlank(requestPathInfo.getResourcePath())) {
                    printJSONCandidatesElement(pw, resourceResolver, requestPathInfo, method);
                }
                pw.printf("  \"warningMsg\" : \"%s\",%n", CONSOLE_PATH_WARNING.replace("<br/>", ""));
                pw.printf("  \"method\" : \"%s\"%n", StringEscapeUtils.escapeJson(method));
                pw.print("}");

                response.setContentType("application/json");
            } else {
                printHTMLInputElements(pw, url);
                if (StringUtils.isNotBlank(url)) {
                    printHTMLDecomposedURLElement(pw, requestPathInfo);
                }

                if (StringUtils.isNotBlank(requestPathInfo.getResourcePath())) {
                    Resource resource = resourceResolver.resolve(requestPathInfo.getResourcePath());
                    final Collection<Resource> servlets = resolveServlets(resourceResolver, requestPathInfo, resource,
                            method);

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
                        outputHTMLServlets(pw, servlets.iterator());
                        pw.println("</ol>");
                    }
                    closeTd(pw);
                    closeTr(pw);
                }

                pw.println("</table>");
                pw.print("</form>");
            }
        } catch (final LoginException e) {
            throw new ServletException(e);
        }
    }

    /**
     * Format an array.
     */
    private String formatArrayAsJSON(final String[] array) {
        if ( array == null || array.length == 0 ) {
            return "[]";
        }
        final StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for(final String s : array) {
            if ( !first ) {
                sb.append(", ");
            }
            first = false;
            sb.append("\"");
            sb.append(StringEscapeUtils.escapeJson(s));
            sb.append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private Map<String, List<String>> getAllowedAndDeniedServlets(Collection<Resource> servlets) {
        List<String> allowedServlets = new ArrayList<>();
        List<String> deniedServlets = new ArrayList<>();
        for (Resource candidateResource : servlets) {
            Servlet candidate = candidateResource.adaptTo(Servlet.class);
            if (candidate != null) {
                final boolean allowed = SlingServletResolver.isPathAllowed(candidateResource.getPath(),
                        this.executionPaths.get());

                String finalCandidate = getServletDetails(candidate);

                if (allowed) {
                    allowedServlets.add(finalCandidate);
                } else {
                    deniedServlets.add(finalCandidate);
                }
            }
        }

        Map<String, List<String>> result = new HashMap<>();
        result.put("allowed", allowedServlets);
        result.put("denied", deniedServlets);

        return result;
    }

    private void printJSONDecomposedURLElement(PrintWriter pw, RequestPathInfo requestPathInfo) {
        pw.println("  \"decomposedURL\" : {");
        pw.printf("    \"path\" : \"%s\",%n",
                StringEscapeUtils.escapeJson(StringUtils.defaultIfEmpty(requestPathInfo.getResourcePath(), "")));
        pw.printf("    \"extension\" : \"%s\",%n",
                StringEscapeUtils.escapeJson(StringUtils.defaultIfEmpty(requestPathInfo.getExtension(), "")));
        pw.printf("    \"selectors\" : %s,%n",
                StringUtils.defaultIfEmpty(formatArrayAsJSON(requestPathInfo.getSelectors()), ""));
        pw.printf("    \"suffix\" : \"%s\"%n",
                StringEscapeUtils.escapeJson(StringUtils.defaultIfEmpty(requestPathInfo.getSuffix(), "")));
        pw.println("  },");
    }

    private void printJSONCandidatesElement(PrintWriter pw, ResourceResolver resourceResolver,
                                            RequestPathInfo requestPathInfo, String method) {
        Resource resource = resourceResolver.resolve(requestPathInfo.getResourcePath());
        final Collection<Resource> servlets = resolveServlets(resourceResolver, requestPathInfo, resource,
                method);
        pw.println("  \"candidates\" : {");
        if (servlets != null) {
            // check for non-existing resources
            if (ResourceUtil.isNonExistingResource(resource)) {
                pw.printf("    \"errorMsg\" : \"%s\",%n", String.format("The resource given by path " +
                        "'%s' does not exist. Therefore no " +
                        "resource type could be determined!", StringEscapeUtils.escapeJson(resource.getPath())));
            }

            Map<String, List<String>> allowedAndDeniedServlets = getAllowedAndDeniedServlets(servlets);
            List<String> allowedServlets = allowedAndDeniedServlets.getOrDefault("allowed", new ArrayList<>());
            List<String> deniedServlets = allowedAndDeniedServlets.getOrDefault("denied", new ArrayList<>());
            pw.printf("    \"allowedServlets\" : %s,%n",
                    formatArrayAsJSON(allowedServlets.toArray(new String[0])));
            pw.printf("    \"deniedServlets\" : %s%n",
                    formatArrayAsJSON(deniedServlets.toArray(new String[0])));
        }
        pw.print("  },");
    }

    private void printHTMLInputElements(PrintWriter pw, String url) {
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
    }

    private void printHTMLDecomposedURLElement(PrintWriter pw, RequestPathInfo requestPathInfo) {
            tr(pw);
            tdLabel(pw, "Decomposed URL");
            tdContent(pw);
            pw.println("<dl>");
            pw.println("<dt>Path</dt>");
            dd(pw);
            pw.print(ResponseUtil.escapeXml(requestPathInfo.getResourcePath()));
            pw.print("<br/>");
            pw.print(String.format("<em>%s</em>", CONSOLE_PATH_WARNING));
            closeDd(pw);
            pw.println("<dt>Selectors</dt>");
            dd(pw);
            if (requestPathInfo.getSelectors().length == 0) {
                pw.print("&lt;none&gt;");
            } else {
                pw.print("[");
                pw.print(ResponseUtil.escapeXml(StringUtils.join(requestPathInfo.getSelectors(), ", ")));
                pw.print("]");
            }
            closeDd(pw);
            pw.println("<dt>Extension</dt>");
            dd(pw);
            pw.print(ResponseUtil.escapeXml(requestPathInfo.getExtension()));
            closeDd(pw);
            pw.println("</dl>");
            closeDd(pw);
            pw.println("<dt>Suffix</dt>");
            dd(pw);
            pw.print(ResponseUtil.escapeXml(requestPathInfo.getSuffix()));
            closeDd(pw);
            pw.println("</dl>");
            closeTd(pw);
            closeTr(pw);
    }

    private Collection<Resource> resolveServlets(ResourceResolver resourceResolver, RequestPathInfo requestPathInfo,
                                                 Resource resource, String method) {
        final Collection<Resource> servlets;
        if (resource.adaptTo(Servlet.class) != null) {
            servlets = Collections.singleton(resource);
        } else {
            final ResourceCollector locationUtil = ResourceCollector.create(
                    resource,
                    requestPathInfo.getExtension(),
                    executionPaths.get(),
                    defaultExtensions.get(),
                    method,
                    requestPathInfo.getSelectors());
            servlets = locationUtil.getServlets(resourceResolver, resolutionCache.getScriptEngineExtensions());
        }

        return servlets;
    }

    private void tdContent(final PrintWriter pw) {
        pw.print("<td class='content' colspan='2'>");
    }

    private void closeTd(final PrintWriter pw) {
        pw.print("</td>");
    }

    private void dd(final PrintWriter pw) {
        pw.println("<dd>");
    }
    private void closeDd(final PrintWriter pw) {
        pw.print("</dd>");
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

    private void outputHTMLServlets(final PrintWriter pw, final Iterator<Resource> iterator) {
        while (iterator.hasNext()) {
            Resource candidateResource = iterator.next();
            Servlet candidate = candidateResource.adaptTo(Servlet.class);
            if (candidate != null) {
                final boolean allowed = SlingServletResolver.isPathAllowed(candidateResource.getPath(), this.executionPaths.get());
                pw.print("<li>");

                String candidateStr = getServletDetails(candidate);
                if ( !allowed ) {
                    pw.print("<del>" + candidateStr + "</del>");
                } else {
                    pw.print(candidateStr);
                }
                pw.println("</li>");
            }
        }
    }

    private String getServletDetails(Servlet servlet) {
        StringBuilder details = new StringBuilder();
        if (servlet instanceof SlingScript) {
            SlingScript script = SlingScript.class.cast(servlet);
            details.append(ResponseUtil.escapeXml(script.getScriptResource().getPath()));
            details.append(" (Resource Script)");
        } else {
            final Bundle bundle;
            if (servlet instanceof BundledScriptServlet) {
                BundledScriptServlet script = BundledScriptServlet.class.cast(servlet);
                bundle = script.getBundledRenderUnit().getBundle();
                details.append(ResponseUtil.escapeXml(script.getBundledRenderUnit().getName()));
                details.append(" (Bundled Script)");
            } else {
                final boolean isOptingServlet = servlet instanceof OptingServlet;
                details.append(ResponseUtil.escapeXml(servlet.getClass().getName()));
                if (isOptingServlet) {
                    details.append(" (OptingServlet)");
                } else {
                    details.append(" (Servlet)");
                }
                bundle = FrameworkUtil.getBundle(servlet.getClass());
            }
            if (bundle != null) {
                details.append(" in bundle '").append(bundle.getSymbolicName()).append("' (").append(bundle.getBundleId()).append(")");
            }
        }
        
        return details.toString();
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
                // ignored
            }
        }
        final int firstDot = fullPath.indexOf(".");

        final ResourceMetadata metadata = new ResourceMetadata();
        final Resource r = new SyntheticResource(null, metadata, null); // NOSONAR
        metadata.setResolutionPath(firstDot < 0 ? fullPath : fullPath.substring(0, firstDot));
        metadata.setResolutionPathInfo(firstDot < 0 ? null : fullPath.substring(firstDot));
        return new SlingRequestPathInfo(r);
    }

}
