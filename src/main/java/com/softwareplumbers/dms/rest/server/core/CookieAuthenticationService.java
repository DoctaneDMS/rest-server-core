package com.softwareplumbers.dms.rest.server.core;

import java.security.Key;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.SecurityContext;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.softwareplumbers.dms.rest.server.util.JWTSecurityContext;
import com.softwareplumbers.keymanager.KeyManager;
import com.softwareplumbers.dms.rest.server.util.Log;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

/** Cookie-based Authentication Service.
 * 
 * @author SWPNET\jonessex
 *
 */
@Component
@Scope("singleton")
public class CookieAuthenticationService {
    
    private static final Log LOG = new Log(CookieAuthenticationService.class);
    
    private final Key jwtSigningKey;

    public CookieAuthenticationService(KeyManager<SystemSecretKeys, SystemKeyPairs> keyManager) {
        jwtSigningKey = keyManager.getKey(SystemSecretKeys.JWT_SIGNING_KEY);    
    }
    
    /** Generate a cookie for the provided UID */
    public NewCookie generateCookie(String uid) {
        LOG.logEntering("generateCookie", uid);
        ZonedDateTime expirationDate = LocalDateTime.now().plusDays(1).atZone(ZoneId.systemDefault());
        Date expirationDateAsDate = Date.from(expirationDate.toInstant());
        String jwt = Jwts.builder()
            .setSubject(uid)
            .setExpiration(expirationDateAsDate)
            .signWith(jwtSigningKey)
            .compact();
        NewCookie cookie = new NewCookie(
             "DoctaneUserToken", jwt, 
             "/", null, Cookie.DEFAULT_VERSION, "Doctane User Token", 
             NewCookie.DEFAULT_MAX_AGE, expirationDateAsDate, false, false);
        return LOG.logReturn("generateCookie", cookie);
    }
    
    /** Validate that request is authenticated and generate an appropriate security context
     * 
     * @param requestContext Request to validate
     * @param additionalProperties Used to output additional properties of a valid request
     * @return an optional security context (empty if request is not authenticated).
     */
    public Optional<SecurityContext> validateCookie(ContainerRequestContext requestContext, Map<String, Object> additionalProperties) {
        LOG.logEntering("validateCookie", Log.fmt(requestContext));
        Cookie cookie = requestContext.getCookies().get("DoctaneUserToken");
        LOG.log.finer("DoctaneUserToken Cookie:" + cookie);
        if (cookie != null) {
            String jws = cookie.getValue();
            try {
                Claims claims = Jwts.parser().setSigningKey(jwtSigningKey).parseClaimsJws(jws).getBody();
                additionalProperties.put("validUntil", claims.getExpiration());
                additionalProperties.put("validFrom", claims.getNotBefore());
                return LOG.logReturn("validateCookie", Optional.of(new JWTSecurityContext(claims)));
            } catch (JwtException exp) {
                return LOG.logReturn("validateCookie", Optional.empty());
            }
        } else {
            return LOG.logReturn("validateCookie", Optional.empty());
        }
    }

}
