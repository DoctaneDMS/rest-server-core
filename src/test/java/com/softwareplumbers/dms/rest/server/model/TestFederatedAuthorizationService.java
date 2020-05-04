package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.RepositoryService;
import com.softwareplumbers.common.immutablelist.QualifiedName;
import com.softwareplumbers.common.abstractquery.Query;
import com.softwareplumbers.dms.Exceptions.*;
import com.softwareplumbers.dms.RepositoryPath;

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

    @Test public void testGetObjectACL() throws InvalidObjectName, InvalidWorkspace {
        Query acl = service.getObjectACL(RepositoryPath.ROOT, null, null,  AuthorizationService.ObjectAccessRole.READ);
        assertThat(acl.containsItem(USER_METADATA_SERVICE_ACCOUNT), equalTo(true));
        assertThat(acl.containsItem(USER_METADATA_KBSL_AND_SVC_AC), equalTo(true));
        assertThat(acl.containsItem(USER_METADATA_KBSL_BRANCH), equalTo(false));
    }
    
    @Test public void testGetDocumentACL() throws InvalidReference {
        Query acl = service.getDocumentACL(new Reference("DocumentId"), null, null, AuthorizationService.DocumentAccessRole.READ);
        assertThat(acl.containsItem(USER_METADATA_SERVICE_ACCOUNT), equalTo(true));
        assertThat(acl.containsItem(USER_METADATA_KBSL_AND_SVC_AC), equalTo(true));
        assertThat(acl.containsItem(USER_METADATA_KBSL_BRANCH), equalTo(false));        
    }

    @Test public void testGetDocumentCreationACL() throws InvalidReference {
        Query acl = service.getDocumentACL(null, "text/plain", JsonValue.EMPTY_JSON_OBJECT, AuthorizationService.DocumentAccessRole.CREATE);
        assertThat(acl.containsItem(USER_METADATA_SERVICE_ACCOUNT), equalTo(true));
        assertThat(acl.containsItem(USER_METADATA_KBSL_AND_SVC_AC), equalTo(true));
        assertThat(acl.containsItem(USER_METADATA_KBSL_BRANCH), equalTo(false));       
    }    
}
