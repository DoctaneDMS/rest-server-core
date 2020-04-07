/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.dms.RepositoryObject;
import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.RepositoryService;
import com.softwareplumbers.common.immutablelist.QualifiedName;
import com.softwareplumbers.common.abstractquery.Query;
import java.util.ArrayList;
import java.util.List;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.core.MediaType;
import com.softwareplumbers.dms.Exceptions.*;

/**
 *
 * @author SWPNET\jonessex
 */
public class FederatedAuthorizationService implements AuthorizationService {

    private List<AuthorizationService> authorizationServices = new ArrayList<>();
    
    public void setAuthorizationServices(List<AuthorizationService> services) {
        this.authorizationServices = services;
    }
    
    public void addAuthorizationService(AuthorizationService service) {
        this.authorizationServices.add(service);
    }
    
    public FederatedAuthorizationService() { }
    
    @Override
    public Query getDocumentACL(Reference ref, String type, JsonObject metadata, DocumentAccessRole role) throws InvalidReference {
        Query result = Query.EMPTY;
        for (AuthorizationService service : authorizationServices)
            result = result.union(service.getDocumentACL(ref, type, metadata, role));
        return result;
    }


    @Override
    public Query getObjectACL(String rootId, QualifiedName path, RepositoryObject.Type type, JsonObject metadata, ObjectAccessRole role) throws InvalidObjectName, InvalidWorkspace {
        Query result = Query.EMPTY;
        for (AuthorizationService service : authorizationServices)
            result = result.union(service.getObjectACL(rootId, path, type, metadata, role));
        return result;
    }


    @Override
    public Query getObjectACLById(String rootId, QualifiedName path, String documentId, ObjectAccessRole role) throws InvalidObjectName, InvalidWorkspace, InvalidDocumentId {
        Query result = Query.EMPTY;
        for (AuthorizationService service : authorizationServices)
            result = result.union(service.getObjectACLById(rootId, path, documentId, role));
        return result;
    }

    @Override
    public Query getAccessConstraint(JsonObject userMetadata, String rootId, QualifiedName pathTemplate) {
        Query result = Query.EMPTY;
        for (AuthorizationService service : authorizationServices)
            result = result.union(service.getAccessConstraint(userMetadata, rootId, pathTemplate));
        return result;
    }

    @Override
    public JsonObject getUserMetadata(String userId) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        for (AuthorizationService service : authorizationServices) {
            JsonObject umd = service.getUserMetadata(userId);
            if (umd != null ) builder.addAll(Json.createObjectBuilder(umd));
        }
        return builder.build();
    }
    
}
