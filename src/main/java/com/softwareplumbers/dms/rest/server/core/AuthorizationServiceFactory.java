/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.core;

import com.softwareplumbers.dms.rest.server.model.AuthorizationService;

/** Service Locator for Authentication Services.
 *
 * @author Jonathan Essex
 */
public interface AuthorizationServiceFactory {
    
    /** Get the authorization service for the given name.
     * 
     * @param name
     * @return An authorization service
     */
    AuthorizationService getService(String name);
}
