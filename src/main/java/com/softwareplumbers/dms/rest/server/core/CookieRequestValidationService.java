package com.softwareplumbers.dms.rest.server.core;

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
import com.softwareplumbers.dms.rest.server.util.Log;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import javax.ws.rs.core.Response.ResponseBuilder;
import com.softwareplumbers.dms.rest.server.model.RequestValidationService;

/** Cookie-based Authentication Service.
 * 
 * @author SWPNET\jonessex
 *
 */
public class CookieRequestValidationService implements RequestValidationService {
    
    private static final Log LOG = new Log(CookieRequestValidationService.class);
    
    private final Key jwtSigningKey;
    private final String cookiePath;

    public CookieRequestValidationService(KeyManager<SystemSecretKeys, SystemKeyPairs> keyManager, String cookiePath) {
        jwtSigningKey = keyManager.getKey(SystemSecretKeys.JWT_SIGNING_KEY);
        this.cookiePath = cookiePath;
    }
    
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
             cookiePath, null, Cookie.DEFAULT_VERSION, "Doctane User Token", 
             NewCookie.DEFAULT_MAX_AGE, expirationDateAsDate, false, false);
        return LOG.logReturn("generateCookie", cookie);    
    }
    
    /** Generate a cookie for the provided User Id.
     * 
     * @param response Response to which we add user cookie
     * @param uid User Id
     * @return modified response
     */
    @Override
    public ResponseBuilder sendIdentityToken(ResponseBuilder response, String uid) {
        LOG.logEntering("sendIdentityToken <response>, ", uid);
        return LOG.logReturn("sendIdentityToken", response.cookie(generateCookie(uid)));
    }
    
    /** Validate that request is authenticated and generate an appropriate security context
     * 
     * @param requestContext Request to validate; may be modified
     * @return true if request is valid
     */
    @Override
    public boolean validateRequest(ContainerRequestContext requestContext) {
        LOG.logEntering("validateRequest", Log.fmt(requestContext));
        Cookie cookie = requestContext.getCookies().get("DoctaneUserToken");
        LOG.log.finer(()->"DoctaneUserToken Cookie:" + cookie);
        if (cookie != null) {
            String jws = cookie.getValue();
            try {
                Claims claims = Jwts.parser().setSigningKey(jwtSigningKey).parseClaimsJws(jws).getBody();
                requestContext.setSecurityContext(new JWTSecurityContext(claims));
                requestContext.setProperty("validUntil", claims.getExpiration());
                requestContext.setProperty("validFrom", claims.getNotBefore());
                return LOG.logReturn("validateRequest", true);
            } catch (JwtException exp) {
                return LOG.logReturn("validateRequest", false);
            }
        } else {
            return LOG.logReturn("validateRequest", false);
        }
    }

}
