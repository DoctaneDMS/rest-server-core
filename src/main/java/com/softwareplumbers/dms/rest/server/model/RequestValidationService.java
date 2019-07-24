package com.softwareplumbers.dms.rest.server.model;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Response.ResponseBuilder;

/** Generic Authentication Service.
 * 
 * Provides a mechanism for obtaining and validating some kind of API access token.
 *
 * @author jonathan.local
 */
public interface RequestValidationService {
    
    /** Validate a requestContext.
     * 
     * Processes any security information (cookies, headers, etc) in the request context, validate it, and if valid
     * create a JEE SecurityContext and insert additional information into the request context.
     * 
     * @param requestContext
     * @return true if request is valid
     */
    public boolean validateRequest(ContainerRequestContext requestContext);
    
    /** Create a response which includes security information.
     * 
     * Before a client can present an identity token, a token must be obtained. The token may be 
     * sent as a cookie, a response header, or as an entity; we don't care.
     * 
     * @param response Response object to which we will add identity token
     * @param userId user id to send identity token for
     * @return The modified ResponseBuilder object
     */
    public ResponseBuilder sendIdentityToken(ResponseBuilder response, String userId);
}
