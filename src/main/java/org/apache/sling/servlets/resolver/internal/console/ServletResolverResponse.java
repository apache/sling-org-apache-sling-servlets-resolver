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

import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.scripting.SlingScript;
import org.apache.sling.api.servlets.OptingServlet;
import org.apache.sling.servlets.resolver.internal.SlingServletResolver;

import javax.servlet.Servlet;
import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * WebConsolePlugin response object
 */
public class ServletResolverResponse {
    private String warningMsg;
    private DecomposedURLServletResolverResponse decomposedURL;
    private String method;
    private CandidateResourceResolverResponse candidates;

    public String getWarningMsg() {
        return warningMsg;
    }

    public void setWarningMsg(String warningMsg) {
        this.warningMsg = warningMsg;
    }

    public DecomposedURLServletResolverResponse getDecomposedURL() {
        return decomposedURL;
    }

    public void setDecomposedURL(DecomposedURLServletResolverResponse decomposedURL) {
        this.decomposedURL = decomposedURL;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public CandidateResourceResolverResponse getCandidates() {
        return candidates;
    }

    public void setCandidates(CandidateResourceResolverResponse candidates) {
        this.candidates = candidates;
    }

    static public class DecomposedURLServletResolverResponse {
        private String path;
        private String[] selectors;
        private String extension, suffix;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String[] getSelectors() {
            return selectors;
        }

        public void setSelectors(String[] selectors) {
            this.selectors = selectors;
        }

        public String getExtension() {
            return extension;
        }

        public void setExtension(String extension) {
            this.extension = extension;
        }

        public String getSuffix() {
            return suffix;
        }

        public void setSuffix(String suffix) {
            this.suffix = suffix;
        }

        public DecomposedURLServletResolverResponse(RequestPathInfo requestPathInfo) {
            this.path = requestPathInfo.getResourcePath();
            this.selectors = requestPathInfo.getSelectors();
            this.extension = requestPathInfo.getExtension();
            this.suffix = requestPathInfo.getSuffix();
        }
    }

    static
    public class CandidateResourceResolverResponse {

        private SortedSet<String> allowedServlets;
        private SortedSet<String> deniedServlets;
        private String errorMsg;

        public SortedSet<String> getAllowedServlets() {
            return allowedServlets;
        }

        public void setAllowedServlets(SortedSet<String> allowedServlets) {
            this.allowedServlets = allowedServlets;
        }

        public SortedSet<String> getDeniedServlets() {
            return deniedServlets;
        }

        public void setDeniedServlets(SortedSet<String> deniedServlets) {
            this.deniedServlets = deniedServlets;
        }

        public String getErrorMsg() {
            return errorMsg;
        }

        public void setErrorMsg(String errorMsg) {
            this.errorMsg = errorMsg;
        }

        public CandidateResourceResolverResponse(Resource resource, Collection<Resource> servlets,
                                                 String[] executionPaths) {
            this.allowedServlets = new TreeSet<>();
            this.deniedServlets = new TreeSet<>();

            // check for non-existing resources
            if (ResourceUtil.isNonExistingResource(resource)) {
                this.errorMsg = new String().format("The resource given by path '%s' does not exist. Therefore no " +
                        "resource type could be determined!", resource.getPath());
            }

            for (Resource candidateResource : servlets) {
                Servlet candidate = candidateResource.adaptTo(Servlet.class);
                if (candidate != null) {
                    final boolean allowed = SlingServletResolver.isPathAllowed(candidateResource.getPath(),
                            executionPaths);

                    String finalCandidate;
                    if (candidate instanceof SlingScript) {
                        finalCandidate = candidateResource.getPath();
                    } else {
                        final boolean isOptingServlet = candidate instanceof OptingServlet;
                        finalCandidate = candidate.getClass().getName();
                        if (isOptingServlet) {
                            finalCandidate += " (OptingServlet)";
                        }
                    }

                    if (allowed) {
                        this.allowedServlets.add(finalCandidate);
                    } else {
                        this.deniedServlets.add(finalCandidate);
                    }
                }
            }
        }
    }
}
