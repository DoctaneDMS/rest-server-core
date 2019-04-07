package com.softwareplumbers.dms.rest.server.core;

import java.security.Key;
import java.util.Optional;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.Response.Status;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.softwareplumbers.dms.rest.server.util.JWTSecurityContext;
import com.softwareplumbers.dms.rest.server.util.KeyManager;
import com.softwareplumbers.dms.rest.server.util.KeyManager.KeyName;
import com.softwareplumbers.dms.rest.server.util.Log;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

@Component
@Scope("singleton")
public class AuthenticationService {
    
    private static final Log LOG = new Log(AuthenticationService.class);
    
    private final Key jwtSigningKey;

    public AuthenticationService(KeyManager keyManager) {
        jwtSigningKey = keyManager.getKey(KeyManager.KeyName.JWT_SIGNING_KEY);    
    }
    
    public NewCookie generateCookie(String uid) {
        LOG.logEntering("generateCookie", uid);
        return LOG.logReturn("generateCookie", new NewCookie("DoctaneUserToken", Jwts.builder().setSubject(uid).signWith(jwtSigningKey).compact()));
    }
    
    public Optional<SecurityContext> validateCookie(ContainerRequestContext requestContext) {
        LOG.logEntering("validateCookie", Log.fmt(requestContext));
        Cookie cookie = requestContext.getCookies().get("DoctaneUserToken");
        LOG.log.finer("DoctaneUserToken Cookie:" + cookie);
        if (cookie != null) {
            String jws = cookie.getValue();
            try {
                Claims claims = Jwts.parser().setSigningKey(jwtSigningKey).parseClaimsJws(jws).getBody();
                return LOG.logReturn("validateCookie", Optional.of(new JWTSecurityContext(claims)));
            } catch (JwtException exp) {
                return LOG.logReturn("validateCookie", Optional.empty());
            }
        } else {
            return LOG.logReturn("validateCookie", Optional.empty());
        }
    }

}
