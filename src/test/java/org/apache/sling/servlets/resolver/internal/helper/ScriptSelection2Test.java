/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 ~ Licensed to the Apache Software Foundation (ASF) under one
 ~ or more contributor license agreements.  See the NOTICE file
 ~ distributed with this work for additional information
 ~ regarding copyright ownership.  The ASF licenses this file
 ~ to you under the Apache License, Version 2.0 (the
 ~ "License"); you may not use this file except in compliance
 ~ with the License.  You may obtain a copy of the License at
 ~
 ~   http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing,
 ~ software distributed under the License is distributed on an
 ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 ~ KIND, either express or implied.  See the License for the
 ~ specific language governing permissions and limitations
 ~ under the License.
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
package org.apache.sling.servlets.resolver.internal.helper;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.apache.sling.testing.mock.sling.servlet.MockRequestPathInfo;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScriptSelection2Test {

    private static final String CONTENT_RESOURCE_PATH = "/content/test";

    @Rule
    public SlingContext context = new SlingContext();

    @Test
    public void testScriptEngineFactories1() throws PersistenceException {
        /*
         Assume the following scripting tree
         /libs/test
            test.jsp
            sel.jsp
         /apps/test
            test.html
            sel.html
         /apps/test2
            [sling:resourceSuperType='test']
            test2.jsp
         */
        context.create().resource("/libs/test/test.jsp", JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_FILE);
        context.create().resource("/libs/test/sel.jsp", JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_FILE);
        context.create().resource("/apps/test/test.html", JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_FILE);
        context.create().resource("/apps/test/sel.html", JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_FILE);
        context.create().resource("/apps/test2",
                JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_UNSTRUCTURED,
                JcrResourceConstants.SLING_RESOURCE_SUPER_TYPE_PROPERTY, "test");
        context.create().resource("/apps/test2/test2.jsp", JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_FILE);

        Resource contentResource;

        contentResource = context.create().resource(CONTENT_RESOURCE_PATH,
                JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_UNSTRUCTURED,
                ResourceResolver.PROPERTY_RESOURCE_TYPE, "test");

        // /content/test.html (sling:resourceType=test) --> /apps/test/test.html
        assertScript(contentResource, "GET", null, "html", Collections.EMPTY_LIST,
                "/apps/test/test.html",
                "/libs/test/test.jsp"
        );
        // /content/test.html (sling:resourceType=test) --> /apps/test/test.html
        assertScript(contentResource, "GET", null, "html", Arrays.asList("jsp", "html"),
                "/apps/test/test.html",
                "/libs/test/test.jsp"
        );

        context.resourceResolver().delete(contentResource);

        contentResource = context.create().resource(CONTENT_RESOURCE_PATH,
                JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_UNSTRUCTURED,
                ResourceResolver.PROPERTY_RESOURCE_TYPE, "test2");
        // /content/test.html (sling:resourceType=test2) --> /apps/test2/test2.jsp
        assertScript(contentResource, "GET", null, "html", Collections.EMPTY_LIST,
                "/apps/test2/test2.jsp",
                "/apps/test/test.html",
                "/libs/test/test.jsp"
        );
        // /content/test.html (sling:resourceType=test2) --> /apps/test2/test2.jsp
        assertScript(contentResource, "GET", null, "html", Arrays.asList("jsp", "html"),
                "/apps/test2/test2.jsp",
                "/apps/test/test.html",
                "/libs/test/test.jsp"
        );
    }

    @Test
    public void testScriptEngineFactories2() {
        /*
         Assume the following scripting tree
         /libs/test
            test.jsp
            sel.jsp
         /apps/test
            test.html
            sel.html
         /apps/test2
            [sling:resourceSuperType='test']
            test2.jsp
            test2.html
         */
        context.create().resource("/libs/test/test.jsp", JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_FILE);
        context.create().resource("/libs/test/sel.jsp", JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_FILE);
        context.create().resource("/apps/test/test.html", JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_FILE);
        context.create().resource("/apps/test/sel.html", JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_FILE);
        context.create().resource("/apps/test2",
                JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_UNSTRUCTURED,
                JcrResourceConstants.SLING_RESOURCE_SUPER_TYPE_PROPERTY, "test");
        context.create().resource("/apps/test2/test2.jsp", JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_FILE);

        Resource contentResource = context.create().resource(CONTENT_RESOURCE_PATH,
                JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_UNSTRUCTURED,
                ResourceResolver.PROPERTY_RESOURCE_TYPE, "test2");

        context.create().resource("/apps/test2/test2.html", JcrConstants.JCR_PRIMARYTYPE, JcrConstants.NT_FILE);
        // /content/test.html (sling:resourceType=test2) --> /apps/test2/test2.html
        assertScript(contentResource, "GET", null, "html", Arrays.asList("jsp", "html"),
                "/apps/test2/test2.html",
                "/apps/test2/test2.jsp",
                "/apps/test/test.html",
                "/libs/test/test.jsp"
        );

        // /content/test.sel.html (sling:resourceType=test2) --> /apps/test/sel.html
        assertScript(contentResource, "GET", "sel", "html", Collections.EMPTY_LIST,
                "/apps/test/sel.html",
                "/libs/test/sel.jsp",
                "/apps/test2/test2.jsp",
                "/apps/test2/test2.html",
                "/apps/test/test.html",
                "/libs/test/test.jsp"
        );
        // /content/test.sel.html (sling:resourceType=test2) --> /apps/test/sel.html
        assertScript(contentResource, "GET", "sel", "html", Arrays.asList("jsp", "html"),
                "/apps/test/sel.html",
                "/libs/test/sel.jsp",
                "/apps/test2/test2.html",
                "/apps/test2/test2.jsp",
                "/apps/test/test.html",
                "/libs/test/test.jsp"
        );

    }

    private void assertScript(Resource contentResource, String method, String selectors, String extension,
                              List<String> scriptEngineFactoriesExtensions, String... expectedScripts) {
        SlingHttpServletRequest request = prepareRequest(method, contentResource, selectors, extension);
        final ResourceCollector collector =
                ResourceCollector.create(request, context.resourceResolver().getSearchPath(), new String[]{"html"});
        final Collection<Resource> s = collector.getServlets(request.getResourceResolver(), scriptEngineFactoriesExtensions);
        if (expectedScripts == null || expectedScripts.length == 0) {
            assertFalse("No script must be found", s.iterator().hasNext());
        } else {
            assertEquals("The number of expected scripts is different than the number of collected scripts.", expectedScripts.length,
                    s.size());
            // Verify that the expected script is the first in the list of candidates
            Iterator<Resource> iterator = s.iterator();
            assertTrue("A script must be found", iterator.hasNext());
            final String scriptPath = iterator.next().getPath();
            assertEquals("Expected a different main script.", expectedScripts[0], scriptPath);
            int index = 1;
            while (iterator.hasNext()) {
                assertEquals("Unexpected script order.", expectedScripts[index++], iterator.next().getPath());
            }
        }
    }

    @NotNull
    private SlingHttpServletRequest prepareRequest(@Nullable String method, @NotNull Resource resource, @Nullable String selectorString,
                                                   @NotNull String extension) {
        MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
        if (StringUtils.isEmpty(method)) {
            request.setMethod("GET");
        } else {
            request.setMethod(method);
        }
        request.setResource(resource);
        MockRequestPathInfo requestPathInfo = (MockRequestPathInfo) request.getRequestPathInfo();
        if (StringUtils.isNotEmpty(selectorString)) {
            requestPathInfo.setSelectorString(selectorString);
        }
        requestPathInfo.setExtension(extension);
        return request;
    }

}
