package com.softwareplumbers.dms.rest.server.model;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Response;

/** Generic Authentication Service.
 * 
 * Provides a mechanism for obtaining and validating some kind of API access token.
 *
 * @author jonathan.local
 */
public interface AuthenticationService {
    
    /** Validate a requestContext.
     * 
     * Processes any security information (cookies, headers, etc) in the request context, validate it, and if valid
     * create a JEE SecurityContext and insert additional information into the request context.
     * 
     * @param tenant API tenant for which validation is being performed
     * @param requestContext
     * @return true if request is valid
     */
    public boolean validateRequest(String tenant, ContainerRequestContext requestContext);
    
    /** Create a response which includes security information.
     * 
     * Before a client can present an identity token, a token must be obtained. The token may be 
     * sent as a cookie, a response header, or as an entity; we don't care.
     * 
     * @param tenant
     * @param userId
     * @return A response containing an identity token.
     */
    public Response sendIdentityToken(String tenant, String userId);
}
