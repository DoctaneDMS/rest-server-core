package com.softwareplumbers.dms.rest.server.core;

import java.io.IOException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

@Provider
public class CORSResponseFilter implements ContainerResponseFilter {
    
    private void addHeaders(MultivaluedMap<String,Object> headers, String origin) {
        headers.add("Access-Control-Allow-Origin", origin);
        headers.add("Access-Control-Allow-Headers", "origin, content-type, accept, authorization");
        headers.add("Access-Control-Allow-Credentials", "true");
        headers.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");        
    }

    // TODO: implement origin whitelist
    @Override
    public void filter(ContainerRequestContext request,
            ContainerResponseContext response) throws IOException {
            String origin = request.getHeaderString("Origin");
            addHeaders(response.getHeaders(), origin);
    }
}