package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractquery.Query;
import com.softwareplumbers.dms.rest.server.core.SystemKeyPairs;
import java.math.BigDecimal;
import static org.junit.Assert.assertEquals;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.ws.rs.core.MediaType;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class TestLocalAuthenticationService {
    
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
        Query acl = service.getObjectACL("rootId", QualifiedName.ROOT, AuthorizationService.ObjectAccessRole.READ);
        assertThat(acl.containsItem(USER_METADATA_SERVICE_ACCOUNT), equalTo(true));
        assertThat(acl.containsItem(JsonValue.EMPTY_JSON_OBJECT), equalTo(false));
    }
    
    @Test public void testGetDocumentACL() {
        Query acl = service.getDocumentACL(new Reference("DocumentId"), AuthorizationService.DocumentAccessRole.READ);
        assertThat(acl.containsItem(USER_METADATA_SERVICE_ACCOUNT), equalTo(true));
        assertThat(acl.containsItem(JsonValue.EMPTY_JSON_OBJECT), equalTo(false));        
    }

    @Test public void testGetDocumentCreationACL() {
        Query acl = service.getDocumentCreationACL(MediaType.TEXT_PLAIN_TYPE, JsonValue.EMPTY_JSON_OBJECT);
        assertThat(acl.containsItem(USER_METADATA_SERVICE_ACCOUNT), equalTo(true));
        assertThat(acl.containsItem(JsonValue.EMPTY_JSON_OBJECT), equalTo(false));        
    }
}
