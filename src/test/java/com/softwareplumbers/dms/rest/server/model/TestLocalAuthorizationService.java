package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractquery.Query;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.ws.rs.core.MediaType;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class TestLocalAuthorizationService {
    
    private final JsonObject USER_METADATA_SERVICE_ACCOUNT = Json.createObjectBuilder()
        .add("serviceAccount", true)
        .build();
    
    LocalAuthorizationService service = new LocalAuthorizationService() {{
       addLocalUser("userOne", USER_METADATA_SERVICE_ACCOUNT); 
       addLocalUser("userTwo", JsonValue.EMPTY_JSON_OBJECT);
    }};

	@Test public void testGetUserMetadata() {
        assertThat(service.getUserMetadata("userOne"), equalTo(USER_METADATA_SERVICE_ACCOUNT));
        assertThat(service.getUserMetadata("userTwo"), equalTo(JsonValue.EMPTY_JSON_OBJECT));
    }
    
    @Test public void testGetObjectACL() {
        Query acl = service.getObjectACL("rootId", QualifiedName.ROOT, null, AuthorizationService.ObjectAccessRole.READ);
        assertThat(acl.containsItem(USER_METADATA_SERVICE_ACCOUNT), equalTo(true));
        assertThat(acl.containsItem(JsonValue.EMPTY_JSON_OBJECT), equalTo(false));
    }
    
    @Test public void testGetDocumentACL() {
        Query acl = service.getDocumentACL(new Reference("DocumentId"), null, null, AuthorizationService.DocumentAccessRole.READ);
        assertThat(acl.containsItem(USER_METADATA_SERVICE_ACCOUNT), equalTo(true));
        assertThat(acl.containsItem(JsonValue.EMPTY_JSON_OBJECT), equalTo(false));        
    }

    @Test public void testGetDocumentCreationACL() {
        Query acl = service.getDocumentACL(null, MediaType.TEXT_PLAIN_TYPE, JsonValue.EMPTY_JSON_OBJECT, AuthorizationService.DocumentAccessRole.CREATE);
        assertThat(acl.containsItem(USER_METADATA_SERVICE_ACCOUNT), equalTo(true));
        assertThat(acl.containsItem(JsonValue.EMPTY_JSON_OBJECT), equalTo(false));        
    }
}
