/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.dms.RepositoryObject;
import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.common.abstractquery.Query;
import javax.json.JsonObject;
import javax.json.JsonValue;
import com.softwareplumbers.dms.Exceptions.*;
import com.softwareplumbers.dms.RepositoryPath;


/**
 *
 * @author jonathan
 */
public class PublicAuthorizationService implements AuthorizationService {

    @Override
    public Query getDocumentACL(Reference ref, String type, JsonObject metadata, DocumentAccessRole role) throws InvalidReference {
        return Query.UNBOUNDED;
    }

    @Override
    public Query getObjectACL(RepositoryPath path, RepositoryObject.Type type, JsonObject metadata, ObjectAccessRole role) throws InvalidObjectName, InvalidWorkspace {
        return Query.UNBOUNDED;
    }

    @Override
    public Query getAccessConstraint(JsonObject userMetadata, RepositoryPath pathTemplate) {
        return Query.UNBOUNDED;
    }

    @Override
    public JsonObject getUserMetadata(String userId) {
        return JsonValue.EMPTY_JSON_OBJECT;
    }

    @Override
    public Query getObjectACLById(RepositoryPath path, String documentId, ObjectAccessRole role) {
        return Query.UNBOUNDED;
    }


}
