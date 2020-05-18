/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.rest.server.model;

import javax.ws.rs.core.Response;

/** Service for redirecting user to sign-on service for repository.
 *
 * @author jonathan.local
 */
public interface SignonService {
    
    public Response redirect(String relayState);    
}
