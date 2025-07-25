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
package org.apache.sling.servlets.resolver.api;

import java.util.function.Predicate;

import org.apache.sling.api.resource.Resource;
import org.osgi.annotation.versioning.ConsumerType;

/** Created for https://issues.apache.org/jira/browse/SLING-12739
 *  to hide specific scripts or servlets from the resolution
 *  mechanism. Can be used for "soft deprecation" of scripts and
 *  servlets for example.
 *
 *  @returns true if the supplied Resource must be ignored by
 *  the servlets resolver.
 */
@ConsumerType
public interface IgnoredServletResourcePredicate extends Predicate<Resource> {}
