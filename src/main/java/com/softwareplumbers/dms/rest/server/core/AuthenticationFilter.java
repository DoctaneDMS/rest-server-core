package com.softwareplumbers.dms.rest.server.core;

import java.io.IOException;
import java.security.Key;
import java.util.Optional;

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
    
    private AuthenticationService cookieHandler;
    
    public AuthenticationFilter(AuthenticationService cookieHandler) {
        this.cookieHandler = cookieHandler;
    }
        
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        Optional<SecurityContext> securityContext = cookieHandler.validateCookie(requestContext);
        if (securityContext.isPresent()) {
            requestContext.setSecurityContext(securityContext.get());
        } else {
            requestContext.abortWith(Response.status(Status.UNAUTHORIZED).build());
        }
    }

}
