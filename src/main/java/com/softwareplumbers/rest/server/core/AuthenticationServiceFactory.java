/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.rest.server.core;

import com.softwareplumbers.dms.rest.server.model.AuthenticationService;

/** Service Locator for Authentication Services.
 *
 * @author jonathan.local
 */
public interface AuthenticationServiceFactory {
    
    /** Get the authentication service for the given name.
     * 
     * @param name
     * @return An authentication service
     */
    AuthenticationService getService(String name);
}
