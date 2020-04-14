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
package org.apache.sling.scripting.bundle.tracker.internal.request;

import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.wrappers.SlingHttpServletResponseWrapper;

public class ResponseWrapper extends SlingHttpServletResponseWrapper {
    private final AtomicReference<PrintWriter> writer = new AtomicReference<>();

    /**
     * Create a wrapper for the supplied wrappedRequest
     *
     * @param wrappedResponse The response
     */
    public ResponseWrapper(SlingHttpServletResponse wrappedResponse) {
        super(wrappedResponse);
    }

    @Override
    public PrintWriter getWriter() {
        PrintWriter result = writer.get();
        if (result == null) {
            result = new PrintWriter(new OnDemandWriter(getResponse()));
            if (!writer.compareAndSet(null, result)) {
                result = writer.get();
            }
        }
        return result;
    }
}
