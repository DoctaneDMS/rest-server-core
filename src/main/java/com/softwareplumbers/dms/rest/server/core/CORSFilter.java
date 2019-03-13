package com.softwareplumbers.dms.rest.server.core;

import java.io.IOException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

@Provider
public class CORSFilter implements ContainerResponseFilter {
    
    private void addHeaders(MultivaluedMap<String,Object> headers) {
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Access-Control-Allow-Headers",
                "origin, content-type, accept, authorization");
        headers.add("Access-Control-Allow-Credentials", "true");
        headers.add("Access-Control-Allow-Methods",
                "GET, POST, PUT, DELETE, OPTIONS, HEAD");
        
    }

	// TODO: improve, may not need on every request
    @Override
    public void filter(ContainerRequestContext request,
            ContainerResponseContext response) throws IOException {
        
        if (request.getMethod().equals("OPTIONS")) {
            Response optionsResponse = Response.noContent().build();
            addHeaders(optionsResponse.getHeaders());
            request.abortWith(optionsResponse);
        } else {
            addHeaders(response.getHeaders());
        }

    }
}