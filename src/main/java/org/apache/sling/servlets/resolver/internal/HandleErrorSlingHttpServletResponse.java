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

import java.io.IOException;
import java.io.PrintWriter;

import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.wrappers.SlingHttpServletResponseWrapper;

/**
 * wrap the original response so we can monitor if the writer
 * has been closed
 */
final class HandleErrorSlingHttpServletResponse extends SlingHttpServletResponseWrapper {
    private HandleErrorResponseWriter writer = null;

    HandleErrorSlingHttpServletResponse(SlingHttpServletResponse response) {
        super(response);
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (this.writer == null) {
            this.writer = new HandleErrorResponseWriter(getResponse().getWriter());
        }
        return this.writer;
    }

    /**
     * Returns whether the response writer is open
     * @return true of open, false otherwise
     */
    public boolean isOpen() {
        return this.writer.isOpen();
    }

}
