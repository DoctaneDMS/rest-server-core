package com.softwareplumbers.rest.server.core;

import org.slf4j.ext.XLogger;
import java.io.IOException;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.Provider;

import org.springframework.stereotype.Component;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import org.springframework.beans.factory.annotation.Autowired;
import com.softwareplumbers.rest.server.model.RequestValidationService;
import javax.annotation.Priority;
import org.apache.log4j.MDC;
import org.slf4j.ext.XLoggerFactory;

/** Authentication filter.
 * 
 * This filter will be applied to all end-points tagged with the @Authenticate marker.
 * 
 * @author SWPNET\jonessex
 *
 */
@Provider
@Authenticated
@Priority(1)
@Component
public class AuthenticationFilter implements ContainerRequestFilter {
    
    private static final XLogger LOG = XLoggerFactory.getXLogger(AuthenticationFilter.class);
    
    private AuthenticationServiceFactory authServiceFactory;
        
    /**
     * Use by Spring to inject a service factory for retrieval of a named authentication service.
     * 
     * @param authServiceFactory A factory for retrieving named services
     */
    @Autowired
    public void setRepositoryServiceFactory(AuthenticationServiceFactory authServiceFactory) {
        this.authServiceFactory = authServiceFactory;
    }
       
    /** Check that a request is authenticated.
     * 
     * Will abort the request with status UNAUTHORIZED if the request cannot be authorized
     * with the authentication service that is configured for the request's "repository" 
     * path parameter.
     * 
     * @param requestContext request to authenticate
     * @throws IOException 
     */
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {       
        String repository = requestContext.getUriInfo().getPathParameters().getFirst("repository");
        LOG.entry(repository);
 
        
        boolean authenticated = false;
        
        if (repository != null) {
            RequestValidationService validationService = authServiceFactory.getService(repository).getRequestValidationService();
            if (validationService != null) {
                authenticated = validationService.validateRequest(requestContext);
            } 
        } else {
            LOG.warn("Could not find repository path parameter");
        }
        
        if (authenticated) {
            MDC.put("user", requestContext.getSecurityContext().getUserPrincipal().getName());
        } 
        else requestContext.abortWith(Response.status(Status.UNAUTHORIZED).build());
        LOG.exit();
    }

}
