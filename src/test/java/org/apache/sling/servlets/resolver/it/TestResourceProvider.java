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
package org.apache.sling.servlets.resolver.it;

import java.util.Iterator;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.spi.resource.provider.ProviderContext;
import org.apache.sling.spi.resource.provider.ResolveContext;
import org.apache.sling.spi.resource.provider.ResourceContext;
import org.apache.sling.spi.resource.provider.ResourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.component.annotations.Component;

@Component(
        service = ResourceProvider.class,
        property = {"provider.root=/"})
public class TestResourceProvider extends ResourceProvider<Void> {

    public TestResourceProvider() {}

    @Override
    public @Nullable Resource getResource(
            @NotNull ResolveContext<Void> ctx,
            @NotNull String path,
            @NotNull ResourceContext resourceContext,
            @Nullable Resource parent) {
        return null;
    }

    @Override
    public @Nullable Iterator<Resource> listChildren(@NotNull ResolveContext<Void> ctx, @NotNull Resource parent) {
        return null;
    }

    @Override
    public void start(@NotNull ProviderContext ctx) {
        super.start(ctx);
    }

    @Override
    public void stop() {
        super.stop();
    }

    @Override
    public void update(long changeSet) {
        super.update(changeSet);
    }
}
