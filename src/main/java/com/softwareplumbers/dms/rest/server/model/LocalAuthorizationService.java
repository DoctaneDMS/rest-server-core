/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractquery.Query;
import com.softwareplumbers.common.abstractquery.Range;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.ws.rs.core.MediaType;

/**
 *
 * @author SWPNET\jonessex
 */
public class LocalAuthorizationService implements AuthorizationService {
    
    private Map<String, JsonObject> localUsers = new HashMap<>();
    
    public void setLocalUsers(Map<String,String> localUsers) {
        for (Map.Entry<String,String> entry : localUsers.entrySet()) {
            JsonReader reader = Json.createReader(new StringReader(entry.getValue()));
            this.localUsers.put(entry.getKey(), reader.readObject());
        }
    }
    
    public void addLocalUser(String username, JsonObject userMetadata) {
        localUsers.put(username, userMetadata);
    }

    @Override
    public Query getObjectACL(String rootId, QualifiedName path, JsonObject metadata, ObjectAccessRole role) {
        return Query.from("serviceAccount", Range.equals(JsonValue.TRUE));
    }

    @Override
    public Query getAccessConstraint(JsonObject userMetadata, String rootId, QualifiedName pathTemplate) {
        if (userMetadata.getBoolean("serviceAccount", false))
            return Query.UNBOUNDED;
        else 
            return Query.EMPTY;
    }

    @Override
    public JsonObject getUserMetadata(String userId) {
        return localUsers.get(userId);
    }

    @Override
    public Query getDocumentACL(Reference ref, MediaType mediaType, JsonObject metadata, DocumentAccessRole role) {
        return Query.from("serviceAccount", Range.equals(JsonValue.TRUE));
    }


    @Override
    public Query getObjectACLById(String rootId, QualifiedName path, String documentId, ObjectAccessRole role) throws RepositoryService.InvalidObjectName, RepositoryService.InvalidWorkspace, RepositoryService.InvalidDocumentId {
        return Query.from("serviceAccount", Range.equals(JsonValue.TRUE));
    }
}
