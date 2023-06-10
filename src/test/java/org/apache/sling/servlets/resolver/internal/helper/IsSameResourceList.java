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
package org.apache.sling.servlets.resolver.internal.helper;

import java.util.List;

import org.apache.sling.api.resource.Resource;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

/**
 * Custom matcher which compares the list of resources. For matching just the path
 * of the resource is considered.
 *
 */
public class IsSameResourceList extends TypeSafeMatcher<List<Resource>>{
	
	List<Resource> baseLine;
	
	private IsSameResourceList(List<Resource> baseline) {
		this.baseLine = baseline;
	}
	
	@Override
	public boolean matchesSafely (List<Resource> item) {
		
		if (item.size() != baseLine.size()) {
			return false;
		}
		for (int i=0; i < item.size(); i++) {
			if (!sameResourcePath(item.get(i),baseLine.get(i))) {
				return false;
			}
		}
		return true;
	}
	
	@Override
	public void describeTo(Description description) {
		description.appendText("isSameListOfResources for");
		
	}
	
	
	private boolean sameResourcePath (Resource a, Resource b) {
		return a.getPath().equals(b.getPath());
	}
	
	
	public static Matcher<List<Resource>> isSameResourceList(List<Resource> baseline) {
		return new IsSameResourceList(baseline);
	}

}
