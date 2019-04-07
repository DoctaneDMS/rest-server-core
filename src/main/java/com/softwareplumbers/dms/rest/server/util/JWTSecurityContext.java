package com.softwareplumbers.dms.rest.server.util;

import java.security.Principal;

import javax.ws.rs.core.SecurityContext;

import io.jsonwebtoken.Claims;

/** Security Context created from JWT token
 * 
 * @author SWPNET\jonessex
 *
 */
public class JWTSecurityContext implements SecurityContext {
    
   
    /** Implementation of Principal
     * 
     * @author SWPNET\jonessex
     *
     */
    private static class PrincipalImpl implements Principal {
        private String name;
        @Override
        public String getName() { return name; }
        public PrincipalImpl(String name) { this.name = name; }    
    }
    
    /** Internal Claims object */
    private final Claims claims;

    @Override
    public Principal getUserPrincipal() {
        return new PrincipalImpl(claims.getSubject());
    }

    @Override
    public boolean isUserInRole(String role) {
        // TODO: improve this!
        return claims.containsKey(role);
    }

    @Override
    public boolean isSecure() {
        return true;
    }

    @Override
    public String getAuthenticationScheme() {
        return "JWT";
    }
    
    /** Create a JEE Security Context from a set of JWT Claims.
     * 
     * @param claims
     */
    public JWTSecurityContext(Claims claims) {
        this.claims = claims;
    }
}