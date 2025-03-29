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
package org.apache.sling.servlets.resolver.internal.bundle;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.felix.hc.api.FormattingResultLog;
import org.apache.felix.hc.api.HealthCheck;
import org.apache.felix.hc.api.Result;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@Component(
        property = {
            "felix.healthcheck.name=BundledScriptTracker",
        },
        service = {HealthCheck.class})
@Designate(ocd = BundledScriptTrackerHC.BundledScriptTrackerHCConfig.class)
public class BundledScriptTrackerHC implements HealthCheck {

    private final BundledScriptTracker tracker;

    private final BundleContext bundleContext;

    private final Set<String> expectedBundles = new HashSet<>();

    private final boolean ignoreNonExistingBundles;

    @Activate
    public BundledScriptTrackerHC(
            final BundleContext context,
            final @Reference BundledScriptTracker tracker,
            final BundledScriptTrackerHCConfig config) {
        this.tracker = tracker;
        this.bundleContext = context;
        this.ignoreNonExistingBundles = config.ignoreNonExistingBundles();
        if (config.mandatoryBundles() != null) {
            expectedBundles.addAll(Arrays.asList(config.mandatoryBundles()));
            BundledScriptTracker.LOGGER.info(
                    "Healthcheck configured with mandatory bundles {} for tags {}, ignoreNonExistingBundles = {}",
                    Arrays.toString(config.mandatoryBundles()),
                    Arrays.toString(config.hc_tags()),
                    ignoreNonExistingBundles);
        }
    }

    @Override
    public Result execute() {
        if (this.expectedBundles.isEmpty()) {
            return new Result(Result.Status.OK, "Health check is not configured.");
        }

        final Set<String> mandatoryAvailableBundles;
        if (this.ignoreNonExistingBundles) {
            // Filter the provided symbolic names if a bundle with that name actually exists
            mandatoryAvailableBundles = filterForExistingBundles(this.bundleContext, this.expectedBundles);
        } else {
            mandatoryAvailableBundles = this.expectedBundles;
        }

        if (this.tracker.getRegisteredBundles().containsAll(mandatoryAvailableBundles)) {
            return new Result(Result.Status.OK, "All expected bundles have registered their scripts.");
        } else {
            FormattingResultLog log = new FormattingResultLog();
            log.warn(
                    "Expected bundles : {}, registered bundles: {}",
                    mandatoryAvailableBundles,
                    this.tracker.getRegisteredBundles());
            return new Result(log);
        }
    }

    /**
     * Return the symbolic names of bundles which are provided via {{code expectedBundles}} and present
     * @param bundleContext a bundleContext
     * @param expectedBundles the symbolic names of bundles to check for
     * @return the symbolic names of present bundles
     */
    protected static Set<String> filterForExistingBundles(
            final BundleContext bundleContext, final Set<String> expectedBundles) {
        final List<Bundle> allBundles = Arrays.asList(bundleContext.getBundles());
        return allBundles.stream()
                .map(Bundle::getSymbolicName)
                .filter(s -> expectedBundles.contains(s))
                .collect(Collectors.toSet());
    }

    @ObjectClassDefinition
    public @interface BundledScriptTrackerHCConfig {

        @AttributeDefinition(
                name = "Mandatory Bundles",
                description =
                        "A list of symbolic bundle names for which the "
                                + "script registration process must have been successfully completed for the health check to report ok.")
        String[] mandatoryBundles();

        @AttributeDefinition(
                name = "Check for bundle presence",
                description =
                        "If disabled, bundles listed as mandatory are ignored if no bundle with that symbolic name is present")
        boolean ignoreNonExistingBundles() default false;

        @AttributeDefinition(
                name = "healthcheck tags",
                description = "the tags under which the healthcheck should be registered")
        String[] hc_tags() default "systemready";
    }
}
