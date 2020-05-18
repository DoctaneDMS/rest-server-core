package com.softwareplumbers.rest.server.core;

import java.security.Key;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.NewCookie;

import com.softwareplumbers.dms.rest.server.util.JWTSecurityContext;
import com.softwareplumbers.keymanager.KeyManager;
import org.slf4j.ext.XLogger;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import javax.ws.rs.core.Response.ResponseBuilder;
import com.softwareplumbers.rest.server.model.RequestValidationService;
import org.slf4j.ext.XLoggerFactory;

/** Cookie-based Authentication Service.
 * 
 * @author SWPNET\jonessex
 *
 */
public class CookieRequestValidationService implements RequestValidationService {
    
    private static final XLogger LOG = XLoggerFactory.getXLogger(CookieRequestValidationService.class);
    
    private final Key jwtSigningKey;
    private final String repository;

    public CookieRequestValidationService(KeyManager<SystemSecretKeys, SystemKeyPairs> keyManager, String repository) {
        jwtSigningKey = keyManager.getKey(SystemSecretKeys.JWT_SIGNING_KEY);
        this.repository = repository;
    }
    
    public NewCookie generateCookie(String uid) {
        LOG.entry(uid);
        ZonedDateTime expirationDate = LocalDateTime.now().plusDays(1).atZone(ZoneId.systemDefault());
        Date expirationDateAsDate = Date.from(expirationDate.toInstant());
        String jwt = Jwts.builder()
            .setSubject(uid)
            .setExpiration(expirationDateAsDate)
            .signWith(jwtSigningKey)
            .compact();
        NewCookie cookie = new NewCookie(
             "DoctaneUserToken/"+repository, jwt, 
             "/", null, Cookie.DEFAULT_VERSION, "Doctane User Token", 
             NewCookie.DEFAULT_MAX_AGE, expirationDateAsDate, false, false);
        return LOG.exit(cookie);    
    }
    
    /** Generate a cookie for the provided User Id.
     * 
     * @param response Response to which we add user cookie
     * @param uid User Id
     * @return modified response
     */
    @Override
    public ResponseBuilder sendIdentityToken(ResponseBuilder response, String uid) {
        LOG.entry(uid);
        return LOG.exit(response.cookie(generateCookie(uid)));
    }
    
    /** Validate that request is authenticated and generate an appropriate security context
     * 
     * @param requestContext Request to validate; may be modified
     * @return true if request is valid
     */
    @Override
    public boolean validateRequest(ContainerRequestContext requestContext) {
        LOG.entry(requestContext);
        Cookie cookie = requestContext.getCookies().get("DoctaneUserToken/"+repository);
        LOG.debug("DoctaneUserToken Cookie: {}", cookie);
        if (cookie != null) {
            String jws = cookie.getValue();
            try {
                Claims claims = Jwts.parser().setSigningKey(jwtSigningKey).parseClaimsJws(jws).getBody();
                requestContext.setSecurityContext(new JWTSecurityContext(claims));
                requestContext.setProperty("validUntil", claims.getExpiration());
                requestContext.setProperty("validFrom", claims.getNotBefore());
                return LOG.exit(true);
            } catch (JwtException exp) {
                return LOG.exit(false);
            }
        } else {
            return LOG.exit(false);
        }
    }

}
