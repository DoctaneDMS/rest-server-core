package com.softwareplumbers.dms.rest.server.util;

import java.security.Principal;

import javax.ws.rs.core.SecurityContext;


/** Security Context created from dummy authentication service
 * 
 * @author SWPNET\jonessex
 *
 */
public class DummySecurityContext implements SecurityContext {
   
    /** Implementation of Principal
     * 
     * @author SWPNET\jonessex
     *
     */
    private final Principal principal; 
        
    public DummySecurityContext(final String name) {
        this.principal = new Principal() {
            @Override public String getName() { return name; } 
        };
    }

    @Override
    public Principal getUserPrincipal() {
        return principal;
    }

    @Override
    public boolean isUserInRole(String role) {
        return true;
    }

    @Override
    public boolean isSecure() {
        return true;
    }

    @Override
    public String getAuthenticationScheme() {
        return "Dummy";
    }
}