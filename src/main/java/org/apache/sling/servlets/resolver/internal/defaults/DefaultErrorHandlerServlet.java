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
package org.apache.sling.servlets.resolver.internal.defaults;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;
import jakarta.servlet.GenericServlet;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.sling.api.SlingJakartaHttpServletRequest;
import org.apache.sling.api.request.RequestProgressTracker;
import org.apache.sling.api.request.ResponseUtil;
import org.apache.sling.api.request.header.JakartaMediaRangeList;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <code>DefaultErrorHandlerServlet</code>
 *
 * This is the default error handler servlet registered at the end of the
 * global search path
 */
@SuppressWarnings("serial")
@Component(
        service = Servlet.class,
        property = {
            Constants.SERVICE_VENDOR + "=The Apache Software Foundation",
            "sling.servlet.paths=sling/servlet/errorhandler/default",
            "sling.servlet.prefix=-1"
        })
public class DefaultErrorHandlerServlet extends GenericServlet {
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String HTML_CONTENT_TYPE = "text/html";

    /** default log */
    private final transient Logger log = LoggerFactory.getLogger(DefaultErrorHandlerServlet.class);

    @Override
    public void service(ServletRequest req, ServletResponse res) throws IOException {

        // get settings
        Integer scObject = (Integer) req.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        String statusMessage = (String) req.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        String requestUri = (String) req.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String servletName = (String) req.getAttribute(RequestDispatcher.ERROR_SERVLET_NAME);

        // ensure values
        int statusCode = (scObject != null) ? scObject.intValue() : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        if (statusMessage == null) {
            statusMessage = statusToString(statusCode);
        }

        // properly consider the 'Accept' header conditions to decide whether to send json or html back
        if (req instanceof HttpServletRequest
                && JSON_CONTENT_TYPE.equals(new JakartaMediaRangeList((HttpServletRequest) req)
                        .prefer(HTML_CONTENT_TYPE, JSON_CONTENT_TYPE))) {
            renderJson(req, res, statusMessage, requestUri, servletName, statusCode);
        } else {
            // default to HTML rendering
            renderHtml(req, res, statusMessage, requestUri, servletName, statusCode);
        }
    }

    /**
     * Render the error as html
     */
    protected void renderHtml(
            ServletRequest req,
            ServletResponse res,
            String statusMessage,
            String requestUri,
            String servletName,
            int statusCode)
            throws IOException {
        // start the response message
        final PrintWriter pw = sendIntro((HttpServletResponse) res, statusCode, statusMessage, requestUri, servletName);

        // write the exception message
        final PrintWriter escapingWriter = new PrintWriter(ResponseUtil.getXmlEscapingWriter(pw));

        // dump the stack trace
        if (req.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable) {
            final Throwable throwable = (Throwable) req.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
            pw.println("<h3>Exception:</h3>");
            pw.println("<pre>");
            pw.flush();
            printStackTrace(escapingWriter, throwable);
            escapingWriter.flush();
            pw.println("</pre>");
        }

        // dump the request progress tracker
        if (req instanceof SlingJakartaHttpServletRequest) {
            final RequestProgressTracker tracker = ((SlingJakartaHttpServletRequest) req).getRequestProgressTracker();
            pw.println("<h3>Request Progress:</h3>");
            pw.println("<pre>");
            pw.flush();
            tracker.dump(escapingWriter);
            escapingWriter.flush();
            pw.println("</pre>");
        }

        // conclude the response message
        sendEpilogue(pw);
    }

    /**
     * Render the error as json
     */
    protected void renderJson(
            ServletRequest req,
            ServletResponse res,
            String statusMessage,
            String requestUri,
            String servletName,
            int statusCode)
            throws IOException {
        HttpServletResponse response = (HttpServletResponse) res;
        response.setStatus(statusCode);
        response.setContentType(JSON_CONTENT_TYPE);
        response.setCharacterEncoding("UTF-8");

        // send the error as JSON
        try (JsonGenerator jsonGenerator = Json.createGenerator(res.getWriter())) {
            jsonGenerator.writeStartObject();
            jsonGenerator.write("status", statusCode);

            String msg = (String) req.getAttribute(RequestDispatcher.ERROR_MESSAGE);
            if (msg != null && !msg.isEmpty()) {
                jsonGenerator.write("message", statusMessage);
            }

            if (requestUri != null && !requestUri.isEmpty()) {
                jsonGenerator.write("requestUri", requestUri);
            }

            if (servletName != null && !servletName.isEmpty()) {
                jsonGenerator.write("servletName", servletName);
            }

            // SLING-10615 - for backward compatibility check for either a
            // String or Class value
            Object exceptionTypeObj = req.getAttribute(RequestDispatcher.ERROR_EXCEPTION_TYPE);
            String exceptionType = null;
            if (exceptionTypeObj instanceof String) {
                exceptionType = (String) exceptionTypeObj;
            } else if (exceptionTypeObj instanceof Class) {
                exceptionType = ((Class<?>) exceptionTypeObj).getName();
            }
            if (exceptionType != null && !exceptionType.isEmpty()) {
                jsonGenerator.write("exceptionType", exceptionType);
            }

            // dump the stack trace
            if (req.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable) {
                final Throwable throwable = (Throwable) req.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
                try (StringWriter sw = new StringWriter();
                        PrintWriter pw = new PrintWriter(sw)) {
                    printStackTrace(pw, throwable);
                    jsonGenerator.write("exception", sw.toString());
                }
            }

            // dump the request progress tracker
            if (req instanceof SlingJakartaHttpServletRequest) {
                // dump the request progress tracker
                final RequestProgressTracker tracker =
                        ((SlingJakartaHttpServletRequest) req).getRequestProgressTracker();
                StringWriter strWriter = new StringWriter();
                try (PrintWriter progressWriter = new PrintWriter(strWriter)) {
                    tracker.dump(progressWriter);
                }
                jsonGenerator.write("requestProgress", strWriter.toString());
            }

            jsonGenerator.writeEnd();
        }
    }

    /**
     * Print the stack trace for the root exception if the throwable is a
     * {@link ServletException}. If this does not contain an exception,
     * the throwable itself is printed.
     */
    private void printStackTrace(PrintWriter pw, Throwable t) {
        // nothing to do, if there is no exception
        if (t == null) {
            return;
        }

        // unpack a servlet exception
        if (t instanceof ServletException) {
            ServletException se = (ServletException) t;
            while (se.getRootCause() != null) {
                t = se.getRootCause();
                if (t instanceof ServletException) {
                    se = (ServletException) t;
                } else {
                    break;
                }
            }
        }

        // dump stack, including causes
        t.printStackTrace(pw);
    }

    /**
     * Sets the response status and content type header and starts the the
     * response HTML text with the header, and an introductory phrase.
     */
    private PrintWriter sendIntro(
            final HttpServletResponse response,
            final int statusCode,
            final String statusMessageIn,
            final String requestUri,
            final String servletName)
            throws IOException {

        final String statusMessage = ResponseUtil.escapeXml(statusMessageIn);

        // set the status code and content type in the response
        final PrintWriter pw;
        if (!response.isCommitted()) {

            response.reset();
            response.setStatus(statusCode);
            response.setContentType(HTML_CONTENT_TYPE);
            response.setCharacterEncoding("UTF-8");

            pw = response.getWriter();
            pw.println("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">");
            pw.println("<html>");
            pw.println("<head>");
            pw.print("<title>");
            pw.print(statusCode);
            pw.print(" ");
            pw.print(statusMessage);
            pw.println("</title>");
            pw.println("</head>");
            pw.println("<body>");

        } else {

            // Response already committed: don't change status or write HTML prolog, but report
            // the error inline and warn about that
            log.warn("Response already committed, unable to change status, output might not be well formed");
            pw = response.getWriter();
        }

        pw.print("<h1>");
        pw.print(statusMessage);
        pw.print(" (");
        pw.print(statusCode);
        pw.println(")</h1>");
        pw.print("<p>The requested URL ");
        pw.print(ResponseUtil.escapeXml(requestUri));
        pw.print(" resulted in an error");

        if (servletName != null) {
            pw.print(" in ");
            pw.print(ResponseUtil.escapeXml(servletName));
        }

        pw.println(".</p>");

        return pw;
    }

    /**
     * Ends the response sending with an apache-style server line and closes the
     * body and html tags of the HTML response text.
     */
    private void sendEpilogue(final PrintWriter pw) {
        pw.println("<hr>");
        pw.print("<address>");
        pw.print(ResponseUtil.escapeXml(getServletContext().getServerInfo()));
        pw.println("</address>");
        pw.println("</body>");
        pw.println("</html>");
    }

    public static String statusToString(int statusCode) {
        switch (statusCode) { // NOSONAR
            case 100:
                return "Continue";
            case 101:
                return "Switching Protocols";
            case 102:
                return "Processing (WebDAV)";
            case 200:
                return "OK";
            case 201:
                return "Created";
            case 202:
                return "Accepted";
            case 203:
                return "Non-Authoritative Information";
            case 204:
                return "No Content";
            case 205:
                return "Reset Content";
            case 206:
                return "Partial Content";
            case 207:
                return "Multi-Status (WebDAV)";
            case 300:
                return "Multiple Choices";
            case 301:
                return "Moved Permanently";
            case 302:
                return "Found";
            case 303:
                return "See Other";
            case 304:
                return "Not Modified";
            case 305:
                return "Use Proxy";
            case 307:
                return "Temporary Redirect";
            case 400:
                return "Bad Request";
            case 401:
                return "Unauthorized";
            case 402:
                return "Payment Required";
            case 403:
                return "Forbidden";
            case 404:
                return "Not Found";
            case 405:
                return "Method Not Allowed";
            case 406:
                return "Not Acceptable";
            case 407:
                return "Proxy Authentication Required";
            case 408:
                return "Request Time-out";
            case 409:
                return "Conflict";
            case 410:
                return "Gone";
            case 411:
                return "Length Required";
            case 412:
                return "Precondition Failed";
            case 413:
                return "Request Entity Too Large";
            case 414:
                return "Request-URI Too Large";
            case 415:
                return "Unsupported Media Type";
            case 416:
                return "Requested range not satisfiable";
            case 417:
                return "Expectation Failed";
            case 422:
                return "Unprocessable Entity (WebDAV)";
            case 423:
                return "Locked (WebDAV)";
            case 424:
                return "Failed Dependency (WebDAV)";
            case 500:
                return "Internal Server Error";
            case 501:
                return "Not Implemented";
            case 502:
                return "Bad Gateway";
            case 503:
                return "Service Unavailable";
            case 504:
                return "Gateway Time-out";
            case 505:
                return "HTTP Version not supported";
            case 507:
                return "Insufficient Storage (WebDAV)";
            case 510:
                return "Not Extended";
            default:
                return String.valueOf(statusCode);
        }
    }
}
