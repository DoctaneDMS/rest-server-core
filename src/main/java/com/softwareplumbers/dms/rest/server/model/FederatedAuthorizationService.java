/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractquery.Query;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.core.MediaType;

/**
 *
 * @author SWPNET\jonessex
 */
public class FederatedAuthorizationService implements AuthorizationService {

    private List<AuthorizationService> authorizationServices = Collections.EMPTY_LIST;
    
    public void setAuthorizationServices(List<AuthorizationService> services) {
        this.authorizationServices = services;
    }
    
    public FederatedAuthorizationService() { }
    
    @Override
    public Query getDocumentACL(Reference ref, DocumentAccessRole role) throws RepositoryService.InvalidReference {
        Query result = Query.EMPTY;
        for (AuthorizationService service : authorizationServices)
            result = result.union(service.getDocumentACL(ref, role));
        return result;
    }

    @Override
    public Query getDocumentACL(Document doc, DocumentAccessRole role) {
        Query result = Query.EMPTY;
        for (AuthorizationService service : authorizationServices)
            result = result.union(service.getDocumentACL(doc, role));
        return result;
    }

    @Override
    public Query getDocumentCreationACL(MediaType mediaType, JsonObject metadata) {
        Query result = Query.EMPTY;
        for (AuthorizationService service : authorizationServices)
            result = result.union(service.getDocumentCreationACL(mediaType, metadata));
        return result;
    }

    @Override
    public Query getObjectACL(String rootId, QualifiedName path, ObjectAccessRole role) throws RepositoryService.InvalidObjectName, RepositoryService.InvalidWorkspace {
        Query result = Query.EMPTY;
        for (AuthorizationService service : authorizationServices)
            result = result.union(service.getObjectACL(rootId, path, role));
        return result;
    }

    @Override
    public Query getObjectACL(NamedRepositoryObject object, ObjectAccessRole role) {
        Query result = Query.EMPTY;
        for (AuthorizationService service : authorizationServices)
            result = result.union(service.getObjectACL(object, role));
        return result;
    }

    @Override
    public Query getObjectACL(String rootId, QualifiedName path, String documentId, ObjectAccessRole role) throws RepositoryService.InvalidObjectName, RepositoryService.InvalidWorkspace, RepositoryService.InvalidDocumentId {
        Query result = Query.EMPTY;
        for (AuthorizationService service : authorizationServices)
            result = result.union(service.getObjectACL(rootId, path, documentId, role));
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
    public Query getAccessConstraint(JsonObject userMetadata, NamedRepositoryObject searchRoot) {
        Query result = Query.EMPTY;
        for (AuthorizationService service : authorizationServices)
            result = result.union(service.getAccessConstraint(userMetadata, searchRoot));
        return result;
    }

    @Override
    public JsonObject getUserMetadata(String userId) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        for (AuthorizationService service : authorizationServices)
            builder.addAll(Json.createObjectBuilder(service.getUserMetadata(userId)));
        return builder.build();
    }
    
}
