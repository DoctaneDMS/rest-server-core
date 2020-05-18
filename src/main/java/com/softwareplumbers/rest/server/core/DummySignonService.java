/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.rest.server.core;

import com.softwareplumbers.rest.server.model.SignonService;
import java.net.URI;
import javax.ws.rs.core.Response;

/** Dummy sign-on service redirects caller back to the target URI specified in relayState.
 *
 * @author jonathan.local
 */
public class DummySignonService implements SignonService {
    
    private final DummyRequestValidationService authService;
    
    public DummySignonService(DummyRequestValidationService authService) {
        this.authService = authService;
    }

    @Override
    public Response redirect(String relayState) {
        URI uri = URI.create(relayState);
        return authService.sendIdentityToken(Response.seeOther(uri), "DummyUser").build(); 
    }
    
}
