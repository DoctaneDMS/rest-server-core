package com.softwareplumbers.dms.rest.server.core;

import java.io.IOException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

@Provider
public class CORSRequestFilter implements ContainerRequestFilter {
    
	// TODO: improve, may not need on every request
    @Override
    public void filter(ContainerRequestContext request) throws IOException {
        if (request.getMethod().equals("OPTIONS")) {
            Response optionsResponse = Response.noContent().build();
            request.abortWith(optionsResponse);
        }
    }
}