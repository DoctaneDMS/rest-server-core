package com.softwareplumbers.dms.rest.server.core;

import java.io.IOException;
import java.util.Optional;
import java.util.TreeMap;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;

import org.springframework.stereotype.Component;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;

/** Authentication filter.
 * 
 * This filter will be applied to all end-points tagged with the @Authenticate marker.
 * 
 * @author SWPNET\jonessex
 *
 */
@Provider
@Authenticated
@Component
public class AuthenticationFilter implements ContainerRequestFilter {
    
    private CookieAuthenticationService cookieHandler;
    
    public AuthenticationFilter(CookieAuthenticationService cookieHandler) {
        this.cookieHandler = cookieHandler;
    }
        
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        TreeMap<String,Object> additionalProperties = new TreeMap<>();
        Optional<SecurityContext> securityContext = cookieHandler.validateCookie(requestContext, additionalProperties);
        if (securityContext.isPresent()) {
            requestContext.setSecurityContext(securityContext.get());
            additionalProperties.forEach((key, value) -> requestContext.setProperty(key, value));
        } else {
            requestContext.abortWith(Response.status(Status.UNAUTHORIZED).build());
        }
    }

}
