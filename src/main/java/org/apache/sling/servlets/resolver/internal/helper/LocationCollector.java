package org.apache.sling.servlets.resolver.internal.helper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.sling.api.resource.ResourceResolver;

public class LocationCollector {
    
    
    public static List<String> getLocations(String resourceType, String resourceSuperType, String baseResourceType,
            ResourceResolver resolver) {
        
        List<String> result = new ArrayList<>();
        
        final Iterator<String> locations = new LocationIterator(resourceType, resourceSuperType,
                baseResourceType, resolver);
        
        while (locations.hasNext()) {
            result.add(locations.next());
        }
        return result;
    }

}
