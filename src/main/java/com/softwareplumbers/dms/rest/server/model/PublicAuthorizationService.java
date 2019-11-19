/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractquery.Query;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.ws.rs.core.MediaType;

/**
 *
 * @author jonathan
 */
public class PublicAuthorizationService implements AuthorizationService {

    @Override
    public Query getDocumentACL(Reference ref, DocumentAccessRole role) throws RepositoryService.InvalidReference {
        return Query.UNBOUNDED;
    }

    @Override
    public Query getDocumentACL(Document doc, DocumentAccessRole role) {
        return Query.UNBOUNDED;
    }

    @Override
    public Query getObjectACL(String rootId, QualifiedName path, ObjectAccessRole role) throws RepositoryService.InvalidObjectName, RepositoryService.InvalidWorkspace {
        return Query.UNBOUNDED;
    }

    @Override
    public Query getObjectACL(NamedRepositoryObject object, ObjectAccessRole role) {
        return Query.UNBOUNDED;
    }

    @Override
    public Query getAccessConstraint(JsonObject userMetadata, String rootId, QualifiedName pathTemplate) {
        return Query.UNBOUNDED;
    }

    @Override
    public Query getAccessConstraint(JsonObject userMetadata, NamedRepositoryObject searchRoot) {
        return Query.UNBOUNDED;
    }

    @Override
    public JsonObject getUserMetadata(String userId) {
        return JsonValue.EMPTY_JSON_OBJECT;
    }

    @Override
    public Query getObjectACL(String rootId, QualifiedName path, String documentId, ObjectAccessRole role) {
        return Query.UNBOUNDED;
    }

    @Override
    public Query getDocumentCreationACL(MediaType mediaType, JsonObject metadata) {
        return Query.UNBOUNDED;
    }
}
