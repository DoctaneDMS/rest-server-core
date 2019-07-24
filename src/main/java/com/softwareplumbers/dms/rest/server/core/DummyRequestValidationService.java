/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.core;

import com.softwareplumbers.dms.rest.server.util.DummySecurityContext;
import com.softwareplumbers.dms.rest.server.util.Log;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import com.softwareplumbers.dms.rest.server.model.RequestValidationService;

/** Dummy authentication service
 *
 * @author jonathan.local
 */
public class DummyRequestValidationService implements RequestValidationService {
    
    private static final Log LOG = new Log(DummyRequestValidationService.class);
    
    private final String repository;
    
    public DummyRequestValidationService(String repository) {
        this.repository = repository;
    }

    @Override
    public boolean validateRequest(ContainerRequestContext requestContext) {
        LOG.logEntering("validateRequest", Log.fmt(requestContext));
        Cookie cookie = requestContext.getCookies().get("DoctaneUserToken/"+repository);
        LOG.log.finer(()->"DoctaneUserToken Cookie:" + cookie);
        String user = "DummyUser";
        if (cookie != null) user = cookie.getValue();
        try {
            requestContext.setSecurityContext(new DummySecurityContext(user));
            ZonedDateTime expirationDate = LocalDateTime.now().plusDays(1).atZone(ZoneId.systemDefault());
            ZonedDateTime fromDate = LocalDateTime.now().atZone(ZoneId.systemDefault());
            requestContext.setProperty("validUntil", Date.from(expirationDate.toInstant()));
            requestContext.setProperty("validFrom", Date.from(fromDate.toInstant()));
            return LOG.logReturn("validateRequest",true);
        } catch (Exception exp) {
            return LOG.logReturn("validateRequest", false);
        }   
    }

    @Override
    public Response.ResponseBuilder sendIdentityToken(Response.ResponseBuilder response, String userId) {
        LOG.logEntering("sendIdentityToken", Log.fmt(response), userId);
        ZonedDateTime expirationDate = LocalDateTime.now().plusDays(1).atZone(ZoneId.systemDefault());
        NewCookie cookie = new NewCookie(
             "DoctaneUserToken/"+repository , userId, 
             null, null, Cookie.DEFAULT_VERSION, "Dummy Doctane User Token", 
             NewCookie.DEFAULT_MAX_AGE, Date.from(expirationDate.toInstant()), false, false);
        return LOG.logReturn("sendIdentityToken", response.cookie(cookie));
    }
    
}
