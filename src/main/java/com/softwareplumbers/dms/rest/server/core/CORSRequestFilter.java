package com.softwareplumbers.dms.rest.server.core;

import java.io.IOException;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import com.softwareplumbers.dms.rest.server.util.Log;


@Provider
public class CORSRequestFilter implements ContainerRequestFilter {
    
    private static final Log LOG = new Log(CORSRequestFilter.class);
    
	// TODO: improve, may not need on every request
    @Override
    public void filter(ContainerRequestContext request) throws IOException {
        String origin = request.getHeaderString("Origin");
        if (request.getMethod().equals("OPTIONS") && origin != null) {
            LOG.log.fine("Handling CORS preflight for" + origin);
            Response optionsResponse = Response.ok()
                    .build();
            request.abortWith(optionsResponse);
        }
    }
}