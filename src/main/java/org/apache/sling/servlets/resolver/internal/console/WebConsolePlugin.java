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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.scripting.SlingScript;
import org.apache.sling.api.servlets.JakartaOptingServlet;
import org.apache.sling.api.uri.SlingUriBuilder;
import org.apache.sling.serviceusermapping.ServiceUserMapped;
import org.apache.sling.servlets.resolver.internal.ResolverConfig;
import org.apache.sling.servlets.resolver.internal.ServletWrapperUtil;
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
import org.owasp.encoder.Encode;

/**
 * If the servlet request path ends with .json, the information is returned in JSON format.
 * Otherwise, an HTML code is returned.
 */
@SuppressWarnings("serial")
@Component(
        service = {Servlet.class},
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

    @Reference(
            target = "(|(" + ServiceUserMapped.SUBSERVICENAME + "=" + SERVICE_USER_CONSOLE + ")(!("
                    + ServiceUserMapped.SUBSERVICENAME + "=*)))")
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
    private AtomicReference<Collection<String>> defaultExtensions = new AtomicReference<>(); // NOSONAR

    /**
     * Activate this component.
     */
    @Activate
    @Modified
    protected void activate(final ResolverConfig config) {
        this.executionPaths.set(SlingServletResolver.getExecutionPaths(config.servletresolver_paths()));
        this.defaultExtensions.set(Arrays.asList(config.servletresolver_defaultExtensions()));
    }

    @Override
    protected void service(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        final String url = request.getParameter(PARAMETER_URL);

        String method = request.getParameter(PARAMETER_METHOD);
        if (method == null || method.isBlank()) {
            method = "GET";
        }

        String requestURI = request.getRequestURI();
        try (final ResourceResolver resourceResolver = resourceResolverFactory.getServiceResourceResolver(
                Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, (Object) SERVICE_USER_CONSOLE))) {
            final PrintWriter pw = response.getWriter();
            final RequestPathInfo requestPathInfo = getRequestPathInfo(url, resourceResolver);
            if (requestURI.endsWith("json")) {
                pw.println("{");
                if (url != null && !url.isBlank()) {
                    printJSONDecomposedURLElement(pw, requestPathInfo);
                }
                if (requestPathInfo.getResourcePath() != null
                        && !requestPathInfo.getResourcePath().isBlank()) {
                    printJSONCandidatesElement(pw, resourceResolver, requestPathInfo, method);
                }
                pw.printf("  \"method\" : \"%s\"%n", Encode.forJavaScript(method));
                pw.print("}");

                response.setContentType("application/json");
            } else {
                printHTMLInputElements(pw, url);
                if (url != null && !url.isBlank()) {
                    printHTMLDecomposedURLElement(pw, requestPathInfo);
                }

                if (requestPathInfo.getResourcePath() != null
                        && !requestPathInfo.getResourcePath().isBlank()) {
                    Resource resource = resourceResolver.resolve(requestPathInfo.getResourcePath());
                    final Collection<Resource> servlets =
                            resolveServlets(resourceResolver, requestPathInfo, resource, method);

                    tr(pw);
                    tdLabel(pw, "Candidates");
                    tdContent(pw);
                    if (servlets == null || servlets.isEmpty()) {
                        pw.println("Could not find a suitable servlet for this request!");
                    } else {
                        // check for non-existing resources
                        if (ResourceUtil.isNonExistingResource(resource)) {
                            pw.println("The resource given by path '");
                            pw.println(Encode.forHtml(resource.getPath()));
                            pw.println("' does not exist. Therefore no resource type could be determined!<br/>");
                        }
                        pw.print("Candidate servlets and scripts in order of preference for method ");
                        pw.print(Encode.forHtml(method));
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
        if (array == null || array.length == 0) {
            return "[]";
        }
        final StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (final String s : array) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append("\"");
            sb.append(Encode.forJavaScript(s));
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
                final boolean allowed =
                        SlingServletResolver.isPathAllowed(candidateResource.getPath(), this.executionPaths.get());

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
        pw.printf(
                "    \"path\" : \"%s\",%n",
                requestPathInfo.getResourcePath() != null
                        ? Encode.forJavaScript(requestPathInfo.getResourcePath())
                        : "");
        pw.printf(
                "    \"extension\" : \"%s\",%n",
                requestPathInfo.getExtension() != null ? Encode.forJavaScript(requestPathInfo.getExtension()) : "");
        pw.printf("    \"selectors\" : %s,%n", formatArrayAsJSON(requestPathInfo.getSelectors()));
        pw.printf(
                "    \"suffix\" : \"%s\"%n",
                requestPathInfo.getSuffix() != null ? Encode.forJavaScript(requestPathInfo.getSuffix()) : "");
        pw.println("  },");
    }

    private void printJSONCandidatesElement(
            PrintWriter pw, ResourceResolver resourceResolver, RequestPathInfo requestPathInfo, String method) {
        Resource resource = resourceResolver.resolve(requestPathInfo.getResourcePath());
        final Collection<Resource> servlets = resolveServlets(resourceResolver, requestPathInfo, resource, method);
        pw.println("  \"candidates\" : {");
        if (servlets != null) {
            // check for non-existing resources
            if (ResourceUtil.isNonExistingResource(resource)) {
                pw.printf(
                        "    \"errorMsg\" : \"%s\",%n",
                        String.format(
                                "The resource given by path " + "'%s' does not exist. Therefore no "
                                        + "resource type could be determined!",
                                Encode.forJavaScript(resource.getPath())));
            }

            Map<String, List<String>> allowedAndDeniedServlets = getAllowedAndDeniedServlets(servlets);
            List<String> allowedServlets = allowedAndDeniedServlets.getOrDefault("allowed", new ArrayList<>());
            List<String> deniedServlets = allowedAndDeniedServlets.getOrDefault("denied", new ArrayList<>());
            pw.printf("    \"allowedServlets\" : %s,%n", formatArrayAsJSON(allowedServlets.toArray(new String[0])));
            pw.printf("    \"deniedServlets\" : %s%n", formatArrayAsJSON(deniedServlets.toArray(new String[0])));
        }
        pw.print("  },");
    }

    private void printHTMLInputElements(PrintWriter pw, String url) {
        pw.print("<form method='get'>");
        pw.println("<table class='content' cellpadding='0' cellspacing='0' width='100%'>");

        titleHtml(
                pw,
                "Servlet Resolver Test",
                "To check which servlet is responsible for rendering a response, enter a request path into "
                        + "the field and click 'Resolve' to resolve it.");

        tr(pw);
        tdLabel(pw, "URL");
        tdContent(pw);

        pw.print("<input type='text' name='");
        pw.print(PARAMETER_URL);
        pw.print("' value='");
        if (url != null) {
            pw.print(Encode.forHtml(url));
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
        if (requestPathInfo.getResourcePath() != null) {
            pw.print(Encode.forHtml(requestPathInfo.getResourcePath()));
        }
        closeDd(pw);
        pw.println("<dt>Selectors</dt>");
        dd(pw);
        if (requestPathInfo.getSelectors().length == 0) {
            pw.print("&lt;none&gt;");
        } else {
            pw.print("[");
            pw.print(Encode.forHtml(String.join(", ", requestPathInfo.getSelectors())));
            pw.print("]");
        }
        closeDd(pw);
        pw.println("<dt>Extension</dt>");
        dd(pw);
        if (requestPathInfo.getExtension() != null) {
            pw.print(Encode.forHtml(requestPathInfo.getExtension()));
        }
        closeDd(pw);
        pw.println("</dl>");
        closeDd(pw);
        pw.println("<dt>Suffix</dt>");
        dd(pw);
        if (requestPathInfo.getSuffix() != null) {
            pw.print(Encode.forHtml(requestPathInfo.getSuffix()));
        }
        closeDd(pw);
        pw.println("</dl>");
        closeTd(pw);
        closeTr(pw);
    }

    private Collection<Resource> resolveServlets(
            ResourceResolver resourceResolver, RequestPathInfo requestPathInfo, Resource resource, String method) {
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
                    requestPathInfo.getSelectors(),
                    true);
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
        pw.print(Encode.forHtml(label));
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
                final boolean allowed =
                        SlingServletResolver.isPathAllowed(candidateResource.getPath(), this.executionPaths.get());
                pw.print("<li>");

                String candidateStr = getServletDetails(candidate);
                if (!allowed) {
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
            final SlingScript script = SlingScript.class.cast(servlet);
            details.append(Encode.forHtml(script.getScriptResource().getPath()));
            details.append(" (Resource Script)");
        } else {
            final Bundle bundle;
            if (servlet instanceof BundledScriptServlet) {
                BundledScriptServlet script = BundledScriptServlet.class.cast(servlet);
                bundle = script.getBundledRenderUnit().getBundle();
                details.append(Encode.forHtml(script.getBundledRenderUnit().getName()));
                details.append(" (Bundled Script)");
            } else {
                final boolean isOptingServlet = servlet instanceof JakartaOptingServlet;
                final Class<?> servletClass;
                javax.servlet.Servlet javaxServlet = null;
                // unwrap Jakarta wrappers
                if (servlet instanceof ServletWrapperUtil.JakartaScriptServletWrapper wrapper) {
                    javaxServlet = wrapper.servlet;

                } else if (servlet instanceof ServletWrapperUtil.JakartaScriptOptingServletWrapper wrapper) {
                    javaxServlet = wrapper.servlet;
                }
                if (javaxServlet != null) {
                    servletClass = javaxServlet.getClass();
                    details.append("Jakarta wrapper for ");
                } else {
                    servletClass = servlet.getClass();
                }
                details.append(Encode.forHtml(servletClass.getName()));
                if (isOptingServlet) {
                    details.append(" (OptingServlet)");
                } else {
                    details.append(" (Servlet)");
                }
                bundle = FrameworkUtil.getBundle(servletClass);
            }
            if (bundle != null) {
                details.append(" in bundle '")
                        .append(Encode.forHtml(bundle.getSymbolicName()))
                        .append("' (")
                        .append(bundle.getBundleId())
                        .append(")");
            }
        }

        return details.toString();
    }

    private void titleHtml(final PrintWriter pw, final String title, final String description) {
        tr(pw);
        pw.print("<th colspan='3' class='content container'>");
        pw.print(Encode.forHtml(title));
        pw.println("</th>");
        closeTr(pw);

        if (description != null) {
            tr(pw);
            pw.print("<td colspan='3' class='content'>");
            pw.print(Encode.forHtml(description));
            pw.println("</th>");
            closeTr(pw);
        }
    }

    static RequestPathInfo getRequestPathInfo(String urlString, ResourceResolver resourceResolver) {

        if (urlString == null) {
            urlString = "";
        }

        String fullPath = urlString;
        if (urlString.contains("http")) {
            try {
                fullPath = new URL(urlString).getPath();
            } catch (MalformedURLException ignore) {
                // ignored
            }
        }
        return SlingUriBuilder.create()
                .setResourceResolver(resourceResolver)
                .setPath(fullPath)
                .build();
    }
}
