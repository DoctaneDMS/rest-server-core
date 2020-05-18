/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.authz.AuthorizationService;
import com.softwareplumbers.authz.AuthorizationServiceFactory;
import com.softwareplumbers.dms.RepositoryObject;
import com.softwareplumbers.dms.RepositoryPath;
import static com.softwareplumbers.dms.rest.server.model.RepositoryAuthorizationService.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

/** Service Locator for Authentication Services.
 *
 * @author Jonathan Essex
 */
public class RepositoryAuthorizationServiceFactory {
    
    @Autowired
    @Qualifier("workspaceAuthorization")
    AuthorizationServiceFactory<RepositoryObject.Type, ObjectAccessRole, RepositoryPath> workspaceAuthz;

    @Autowired
    @Qualifier("documentAuthorization")
    AuthorizationServiceFactory<DocumentTypes, DocumentAccessRole, RepositoryPath> documentAuthz;
    
    /** Get the authorization service for the given name.
     * 
     * @param name
     * @return An authorization service
     */
    public RepositoryAuthorizationService getService(String name) {
        return new RepositoryAuthorizationService(documentAuthz.getService(name), workspaceAuthz.getService(name));
    }
}
