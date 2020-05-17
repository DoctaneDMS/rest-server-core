package com.softwareplumbers.rest.server.core;

import com.softwareplumbers.dms.rest.server.core.Error;
import com.softwareplumbers.rest.server.core.Authenticated;
import com.softwareplumbers.rest.server.core.AuthorizationServiceFactory;
import org.slf4j.ext.XLogger;
import java.io.IOException;
import javax.annotation.Priority;

import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.Provider;

import org.springframework.stereotype.Component;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import org.springframework.beans.factory.annotation.Autowired;
import javax.json.JsonObject;
import org.slf4j.ext.XLoggerFactory;
import com.softwareplumbers.dms.rest.server.model.RepositoryAuthorizationService;

/**
 * UserMetadata filter.
 *
 * This filter will be applied to all end-points tagged with the @Authenticate
 * marker. It will invoke the authorizationServiceFactory defined for the repository
 * in spring config, and add a property to the request context with the user
 * metadata retrieved from the service.
 *
 * @author SWPNET\jonessex
 *
 */
@Provider
@Authenticated
@Priority(2)
@Component
public class UserMetadataFilter implements ContainerRequestFilter {

    private static final XLogger LOG = XLoggerFactory.getXLogger(UserMetadataFilter.class);

    private AuthorizationServiceFactory authorizationServiceFactory;

    /**
     * Use by Spring to inject a service factory for retrieval of a named
     * authorization service.
     *
     * @param authorizationServiceFactory A factory for retrieving named services
     */
    @Autowired
    public void setRepositoryServiceFactory(AuthorizationServiceFactory authorizationServiceFactory) {
        this.authorizationServiceFactory = authorizationServiceFactory;
    }

    /**
     * Add user metadata to a request
     *
     * @param requestContext request to authenticate
     * @throws IOException
     */
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String repository = requestContext.getUriInfo().getPathParameters().getFirst("repository");
        LOG.entry(repository);

        if (repository != null) {
            String userId = requestContext.getSecurityContext().getUserPrincipal().getName();
            RepositoryAuthorizationService authService = authorizationServiceFactory.getService(repository);
            if (authService != null) { 
                JsonObject userMetadata = authorizationServiceFactory.getService(repository).getUserMetadata(userId);
                requestContext.setProperty("userMetadata", userMetadata);
            } else {
                requestContext.abortWith(
                    LOG.exit(
                        Error.errorResponse(Status.NOT_FOUND, Error.repositoryNotFound(repository))
                    )
                );
            }
        } else {
            requestContext.abortWith(
                LOG.exit(
                    Error.errorResponse(Status.NOT_FOUND, Error.repositoryNotFound(repository))
                )
            );
        }

        LOG.exit();
    }

}
