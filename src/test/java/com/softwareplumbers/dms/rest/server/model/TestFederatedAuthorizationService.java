package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractquery.Query;
import java.util.Arrays;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.ws.rs.core.MediaType;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import org.junit.Test;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author jonathan
 */
public class TestFederatedAuthorizationService {
    
    private final JsonObject USER_METADATA_SERVICE_ACCOUNT = Json.createObjectBuilder()
        .add("serviceAccount", true)
        .build();

    private final JsonObject USER_METADATA_KBSL_BRANCH = Json.createObjectBuilder()
        .add("transactionBranches", Json.createArrayBuilder(Arrays.asList("KBSL")))
        .build();

    private final JsonObject USER_METADATA_MBUK_AND_SVC_AC = Json.createObjectBuilder()
        .add("transactionBranches", Json.createArrayBuilder(Arrays.asList("MBUK")))
        .add("serviceAccount", true)
        .build();

    private final JsonObject USER_METADATA_KBSL_AND_SVC_AC = Json.createObjectBuilder()
        .add("transactionBranches", Json.createArrayBuilder(Arrays.asList("KBSL")))
        .add("serviceAccount", true)
        .build();

    FederatedAuthorizationService service = new FederatedAuthorizationService() {{
        addAuthorizationService(new LocalAuthorizationService() {{
            addLocalUser("userOne", USER_METADATA_SERVICE_ACCOUNT); 
            addLocalUser("userTwo", USER_METADATA_MBUK_AND_SVC_AC);
        }});
        addAuthorizationService(new LocalAuthorizationService() {{
            addLocalUser("userTwo", USER_METADATA_KBSL_BRANCH);
            addLocalUser("userThree", USER_METADATA_KBSL_BRANCH);
        }});
    }};

	@Test public void testGetUserMetadata() {
        assertThat(service.getUserMetadata("userOne"), equalTo(USER_METADATA_SERVICE_ACCOUNT));
        assertThat(service.getUserMetadata("userTwo"), equalTo(USER_METADATA_KBSL_AND_SVC_AC));
        assertThat(service.getUserMetadata("userThree"), equalTo(USER_METADATA_KBSL_BRANCH));
    }

    @Test public void testGetObjectACL() throws RepositoryService.InvalidObjectName, RepositoryService.InvalidWorkspace {
        Query acl = service.getObjectACL("rootId", QualifiedName.ROOT, AuthorizationService.ObjectAccessRole.READ);
        assertThat(acl.containsItem(USER_METADATA_SERVICE_ACCOUNT), equalTo(true));
        assertThat(acl.containsItem(USER_METADATA_KBSL_AND_SVC_AC), equalTo(true));
        assertThat(acl.containsItem(USER_METADATA_KBSL_BRANCH), equalTo(false));
    }
    
    @Test public void testGetDocumentACL() throws RepositoryService.InvalidReference {
        Query acl = service.getDocumentACL(new Reference("DocumentId"), AuthorizationService.DocumentAccessRole.READ);
        assertThat(acl.containsItem(USER_METADATA_SERVICE_ACCOUNT), equalTo(true));
        assertThat(acl.containsItem(USER_METADATA_KBSL_AND_SVC_AC), equalTo(true));
        assertThat(acl.containsItem(USER_METADATA_KBSL_BRANCH), equalTo(false));        
    }

    @Test public void testGetDocumentCreationACL() {
        Query acl = service.getDocumentCreationACL(MediaType.TEXT_PLAIN_TYPE, JsonValue.EMPTY_JSON_OBJECT);
        assertThat(acl.containsItem(USER_METADATA_SERVICE_ACCOUNT), equalTo(true));
        assertThat(acl.containsItem(USER_METADATA_KBSL_AND_SVC_AC), equalTo(true));
        assertThat(acl.containsItem(USER_METADATA_KBSL_BRANCH), equalTo(false));       
    }    
}
