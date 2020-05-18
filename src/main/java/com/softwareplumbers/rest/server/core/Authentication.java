package com.softwareplumbers.rest.server.core;

//import com.softwareplumbers.dms.rest.server.core.Error;
import com.softwareplumbers.rest.server.model.AuthenticationService;
import com.softwareplumbers.rest.server.model.CoreExceptions;
import com.softwareplumbers.rest.server.model.CoreExceptions.AuthenticationError;
import com.softwareplumbers.rest.server.model.SAMLProtocolHandlerService;
import com.softwareplumbers.rest.server.model.SAMLProtocolHandlerService.SAMLParsingError;
import com.softwareplumbers.rest.server.model.SignedRequestValidationService.RequestValidationError;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import javax.inject.Singleton;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;

import org.springframework.stereotype.Component;

import org.slf4j.ext.XLogger;
import java.util.Optional;
import javax.ws.rs.PathParam;
import org.slf4j.ext.XLoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

/** Handle authentication operations
 * 
 * The main rest endpoints use a JWT token to authenticate the caller. The Authentication methods
 * provide different ways to obtain a token. 
 * 
 * @author Jonathan Essex
 *
 */
@Component
@Path("/auth")
@Singleton
public class Authentication {
    
    private static final XLogger LOG = XLoggerFactory.getXLogger(Authentication.class);
    
    private final AuthenticationServiceFactory authenticationServiceFactory;
    
    private  AuthenticationService getAuthenticationService(String repository) throws CoreExceptions.InvalidService {
        try {
            return authenticationServiceFactory.getService(repository);
        } catch (NoSuchBeanDefinitionException e) {
            throw new CoreExceptions.InvalidService(repository);
        }
    }
    
    /** Construct an authentication web service using an authentication back-end.
     * 
     * @param authenticationServiceFactory back-end authentication service locator
     */
    public Authentication(
            AuthenticationServiceFactory authenticationServiceFactory
    ) {
        this.authenticationServiceFactory = authenticationServiceFactory;
    }
     
    /** Check to see if a token is valid and, if so, how long for.
     * 
     * Returns a json object { validFrom: X, validTo: Y } with validity information.
     * 
     * Since this is an authenticated endpoint, it will return UNAUTHORIZED if the token presented is not valid.
     * 
     * @param repository Not actually used in this function, but used in the Authentication Filter
     * @param context Security context generated by authentication filter
     * @return validity information in json format
     */
    @Path("{repository}/token") // Note repository param needed for AuthenticationFilter
    @Authenticated
    @GET
    @Produces({MediaType.APPLICATION_JSON})
    public Response getTokenValidity(@PathParam("repository") String repository, @Context ContainerRequestContext context) {
        LOG.entry(repository, context);
        SecurityContext secContext = context.getSecurityContext();
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("user", secContext.getUserPrincipal().getName());
        Date validFrom = (Date)context.getProperty("validFrom");
        Date validUntil = (Date)context.getProperty("validUntil");
        if (validFrom != null) builder.add("validFrom", DateTimeFormatter.ISO_INSTANT.format(validFrom.toInstant()));
        if (validUntil != null) builder.add("validUntil", DateTimeFormatter.ISO_INSTANT.format(validUntil.toInstant()));
        return LOG.exit(Response.ok(MediaType.APPLICATION_JSON_TYPE).entity(builder.build()).build());      
    }

    /** Handle a SAML2 response
     * 
     * Doctane handles a SAML2 authentication flow with this method. Takes a SAML response (passed here from
     * the IDP via the client), validate it, and if OK passes a JWT token back to the client with a redirect.
     * 
     * @param repository Repository to authorize
     * @param samlResponse SAML response signed by IDP containing authenticated user details
     * @param relayState Client URI for redirect
     * @return A SEE OTHER response redirecting to the URI specified in relayState
     * @throws CoreExceptions.InvalidService if repository is invalid
     * @throws CoreExceptions.AuthenticationError is request cannot be validated

     */
    @Path("{repository}/saml")
    @POST
    @Consumes({ MediaType.APPLICATION_FORM_URLENCODED })
    public Response handleSamlResponse(
        @PathParam("repository") String repository,     
        @FormParam("SAMLResponse") String samlResponse,
        @FormParam("RelayState") String relayState
        
    ) throws CoreExceptions.InvalidService, CoreExceptions.AuthenticationError
    {
        LOG.entry();
                
        try {     
            AuthenticationService authService = getAuthenticationService(repository);
            SAMLProtocolHandlerService samlResponseHandler = authService.getSAMLResponseHandlerService();
    
            org.opensaml.saml.saml2.core.Response response = samlResponseHandler.parseSamlResponse(samlResponse);
        
            if (samlResponseHandler.validateSignature(response) && samlResponseHandler.hasDocumentViewerRole(response)) {
                URI location = new URI(relayState);
                return LOG.exit( 
                    authService.getRequestValidationService().sendIdentityToken(
                            Response.seeOther(location), 
                            samlResponseHandler.getName(response)
                    ).build()
                );
            } else {
                return LOG.exit(Response.status(Status.FORBIDDEN).build());
            }
        } catch(SAMLParsingError | URISyntaxException e) {
            throw LOG.throwing(new CoreExceptions.AuthenticationError(e));
        }         
    }
    
    /**  Handle service authentication request (i.e.authentication via public key)
     * 
     * The request is made in the form of a JSON object { account: X, instant: Y }. The account is the alias of the 
     * public key stored in the server's key store, which will be used to validate the signature. Instant is a 
     * timestamp, which must be within 60 seconds of the current system time.
     *
     * @param repository Repository to authenticate to
     * @param request Request for access to the API (a base 64 encoded JSON object)
     * @param signature Signature of request (base 64 encoded)
     * @return A JWT token providing access to the API
     * @throws CoreExceptions.InvalidService is repository is invalid
     * @throws CoreExceptions.AuthenticationError is request cannot be validated
     */
    @Path("{repository}/service")
    @GET
    public Response handleServiceRequest(
        @PathParam("repository") String repository,     
        @QueryParam("request") String request,
        @QueryParam("signature") String signature
    ) throws CoreExceptions.InvalidService, CoreExceptions.AuthenticationError
    {
        LOG.entry();
        
        try {
            AuthenticationService authService = getAuthenticationService(repository);

            if (request == null) return Response.status(Status.NOT_ACCEPTABLE).build();
            if (signature == null) return Response.status(Status.NOT_ACCEPTABLE).build();
        
            Optional<String> account = authService.getSignedRequestValidationService().validateSignature(request, signature);
            if (account.isPresent()) {
                return LOG.exit(authService.getRequestValidationService().sendIdentityToken(Response.ok(), account.get()).build());
            } else {
                return LOG.exit(Response.status(Status.FORBIDDEN).build());
            }
        } catch(RequestValidationError e) {
            throw LOG.throwing(new AuthenticationError(e));
        } 
    }
    
    /** Redirect the requestor to the preferred authentication service for the given repository 
     *
     * @param repository Repository we want a sign-on for
     * @param relayState Target URI in the requesting application; where to go to after interactive authentication
     * @return A redirect response
     * @throws CoreExceptions.InvalidService if repository is invalid
     */
    @Path("{repository}/signon")
    @GET
    public Response handleRedirect(
        @PathParam("repository") String repository,
        @QueryParam("relayState") String relayState
    ) throws CoreExceptions.InvalidService 
    {
        LOG.entry();
        AuthenticationService authService = getAuthenticationService(repository);
        return LOG.exit(authService.getSignonService().redirect(relayState));
    }

}
