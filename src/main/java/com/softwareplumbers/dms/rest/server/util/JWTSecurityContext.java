package com.softwareplumbers.dms.rest.server.util;

import java.security.Principal;

import javax.ws.rs.core.SecurityContext;

import io.jsonwebtoken.Claims;

public class JWTSecurityContext implements SecurityContext {
    
    private static class PrincipalImpl implements Principal {
        private String name;
        @Override
        public String getName() { return name; }
        public PrincipalImpl(String name) { this.name = name; }
        
    }
    
    private final Claims claims;

    @Override
    public Principal getUserPrincipal() {
        return new PrincipalImpl(claims.getSubject());
    }

    @Override
    public boolean isUserInRole(String role) {
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
    
    public JWTSecurityContext(Claims claims) {
        this.claims = claims;
    }
    
}