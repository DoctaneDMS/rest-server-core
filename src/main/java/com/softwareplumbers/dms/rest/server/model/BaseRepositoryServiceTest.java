package com.softwareplumbers.dms.rest.server.model;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.ws.rs.core.MediaType;

import org.junit.Test;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractquery.ObjectConstraint;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidDocumentId;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidObjectName;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidReference;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspace;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspaceState;
import com.softwareplumbers.dms.rest.server.model.Workspace.State;

import static com.softwareplumbers.dms.rest.server.model.RepositoryService.ROOT_WORKSPACE_ID;

/** Unit tests that should pass for all implementations of RepositoryService. 
 * 
 */
public abstract class BaseRepositoryServiceTest {
	
	public abstract RepositoryService service();
	
	public static final String[] names = { "julien", "peter", "fairfax", "austen", "celtic", "a", "the", "halibut", "eaten" };
	public static final String characters = "-._~";
	public static final String reserved = "&$+,/:;=?@#";
	
	public static int unique = 0;
	
	public static final String randomUrlSafeName() {
		StringBuffer buffer = new StringBuffer(names[(int)(Math.random() * names.length)]);
		buffer.append(characters.charAt((int)(Math.random() * characters.length())));
		buffer.append(Integer.toHexString(unique++));
		return buffer.toString();
	}
	
	public static final String randomReservedName() {
		StringBuffer buffer = new StringBuffer();
		for (int i = 1; i < 3; i++) {
			buffer.append(reserved.charAt((int)(Math.random() * reserved.length())));
			buffer.append(randomUrlSafeName());
		}
		return buffer.toString();
	}
	
	public static final String randomText() {
		StringBuffer buffer = new StringBuffer();
		for (int i = 1; i < 10; i++) {
			buffer.append(randomUrlSafeName());
			buffer.append(" ");
		}
		return buffer.toString();		
	}
	
	public static final InputStream toStream(String out) {
		return new ByteArrayInputStream(out.getBytes());
	}
	
	public abstract Reference randomDocumentReference();
	public abstract String randomWorkspaceId();
	
	@Test
	public void testAnonymousCreateWorkspace() throws InvalidWorkspace {
		String workspace1 = service().createWorkspace(null, State.Open, null);
		assertNotNull(workspace1);
		String workspace2 = service().createWorkspace(null, State.Open, null);
		assertNotNull(workspace1);
		assertNotEquals(workspace1, workspace2);
	}
	
	@Test
	public void testCreateAndFindWorkspaceWithURLSafeName() throws InvalidWorkspace, InvalidObjectName {
		QualifiedName name = QualifiedName.of(randomUrlSafeName());
		String workspace = service().createWorkspace(name, State.Open, null);
		Workspace ws = (Workspace)service().getObjectByName(ROOT_WORKSPACE_ID, name);
		assertEquals(workspace, ws.getId());
	}
	
	@Test(expected = InvalidReference.class)
	public void testRepositoryFetchWithInvalidRef() throws IOException, InvalidReference {
		Reference ref1 = randomDocumentReference();
		service().getDocument(ref1);
	}
	
	@Test (expected = InvalidObjectName.class)
	public void testGetWorkspaceNotFoundByNameError() throws InvalidWorkspace, InvalidObjectName {
		QualifiedName name = QualifiedName.of(randomUrlSafeName());
		Workspace test = (Workspace)service().getObjectByName(ROOT_WORKSPACE_ID,name);
	}
	
	@Test (expected = InvalidWorkspace.class)
	public void testGetWorkspaceNotFoundByIdError() throws InvalidWorkspace {
		Workspace test = service().getWorkspaceById(randomWorkspaceId());
	}

	@Test (expected = InvalidWorkspace.class)
	public void testUpdateWorkspaceNotFoundError() throws InvalidWorkspace {
		QualifiedName name = QualifiedName.of(randomUrlSafeName());
		service().updateWorkspaceByName(ROOT_WORKSPACE_ID, name, null, Workspace.State.Closed, null, false);
	}
	
	@Test (expected = InvalidWorkspace.class)
	public void testCreateDocumentNotFoundError() throws InvalidWorkspace, InvalidWorkspaceState, IOException {
		service().createDocument(
			MediaType.TEXT_PLAIN_TYPE, 
			()->toStream(randomText()), 
			null,
			randomWorkspaceId(), 
			false);
	}
	
	@Test (expected = InvalidDocumentId.class)
	public void testDeleteDocumentInvalidDocumentError() throws InvalidWorkspace, InvalidDocumentId, InvalidWorkspaceState {
		String workspace = service().createWorkspace(null, State.Open, null);
		service().deleteDocument(workspace, randomDocumentReference().getId());
	}

	@Test (expected = InvalidWorkspace.class)
	public void testDeleteDocumentInvalidWorkspaceId() throws InvalidWorkspace, InvalidDocumentId, InvalidWorkspaceState {
		Reference ref = service().createDocument(
			MediaType.TEXT_PLAIN_TYPE, 
			()->toStream(randomText()), null, null, false
		);
		service().deleteDocument(randomWorkspaceId(), ref.id);
	}
	
	@Test 
	public void testListWorkspacesInvalidWorkspaceId() throws InvalidDocumentId {
		assertEquals(0L,service().listWorkspaces(randomDocumentReference().id).count());
	}

	@Test
	public void testContentLength() throws InvalidReference, InvalidWorkspace, InvalidWorkspaceState {
		for (int i = 0; i < 3; i++) {
			String testData = randomText();
			Reference ref = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(testData), null, null, false);
			Document doc = service().getDocument(ref);
			assertEquals(testData.length(), doc.getLength());
		}
	}
	
	@Test
	public void testRenameWorkspace() throws InvalidWorkspace, InvalidObjectName {
		String wsId = service().createWorkspace(null, State.Open, null);
		QualifiedName wsName = QualifiedName.of(randomUrlSafeName());
		service().updateWorkspaceById(wsId, wsName, null, null, true);
		Workspace ws = (Workspace)service().getObjectByName(ROOT_WORKSPACE_ID, wsName);
		assertEquals(wsId, ws.getId());
	}
	
	@Test  (expected = InvalidWorkspace.class)
	public void testRenameFolderExistingName() throws InvalidWorkspace {
		QualifiedName wsName = QualifiedName.of(randomUrlSafeName());
		service().createWorkspace(wsName, State.Open, null);
		String wsId = service().createWorkspace(null, State.Open, null);
		service().updateWorkspaceById(wsId,wsName, null, null, false);
	}
	
	@Test
	public void testSearchForWorkspaceByWildcard() throws InvalidWorkspace {
		QualifiedName base = QualifiedName.of(randomUrlSafeName());
		QualifiedName jones = base.add("jones");
		QualifiedName carter = base.add("carter");
		QualifiedName peter_jones = jones.add("peter");
		QualifiedName peter_carter = carter.add("peter");
		QualifiedName pamela_jones = jones.add("pamela");
		QualifiedName roger_carter = carter.add("roger");
		service().createWorkspace(peter_jones, State.Open, null);
		service().createWorkspace(peter_carter, State.Open, null);
		service().createWorkspace(pamela_jones, State.Open, null);
		service().createWorkspace(roger_carter, State.Open, null);
				
		assertEquals(4, service().catalogueByName(ROOT_WORKSPACE_ID, base.addAll("*","*"), ObjectConstraint.UNBOUNDED, false).count());
		assertEquals(2, service().catalogueByName(ROOT_WORKSPACE_ID,base.addAll("jones","*"), ObjectConstraint.UNBOUNDED, false).count());
		assertEquals(2, service().catalogueByName(ROOT_WORKSPACE_ID,base.addAll("carter","*"), ObjectConstraint.UNBOUNDED, false).count());
		assertEquals(3, service().catalogueByName(ROOT_WORKSPACE_ID,base.addAll("*","p*"), ObjectConstraint.UNBOUNDED, false).count());
		assertEquals(3, service().catalogueByName(ROOT_WORKSPACE_ID,base.addAll("*","*r"), ObjectConstraint.UNBOUNDED, false).count());
	}
	
	@Test
	public void testWorkspaceMetadataRoundtrip() throws InvalidWorkspace, InvalidObjectName {
		QualifiedName base = QualifiedName.of(randomUrlSafeName());
		JsonObject testMetadata = Json.createObjectBuilder().add("Branch", "slartibartfast").build();
		service().createWorkspace(base, State.Open, testMetadata);
		Workspace fetched =  (Workspace)service().getObjectByName(ROOT_WORKSPACE_ID,base);
		assertEquals("slartibartfast", fetched.getMetadata().getString("Branch"));
	}
	
	@Test
	public void testCatalogueWorkspaceWithMixedEntyTypes() throws InvalidWorkspace, InvalidWorkspaceState, InvalidObjectName {
		QualifiedName base = QualifiedName.of(randomUrlSafeName());
		QualifiedName jones = base.add("jones");
		QualifiedName carter = base.add("carter");
		service().createWorkspace(base, State.Open, null);
		service().createWorkspace(jones, State.Open, null);
		service().createDocumentByName(null, carter, MediaType.TEXT_PLAIN_TYPE, ()->toStream(randomText()), null, false);
		assertEquals(2,service().catalogueByName(ROOT_WORKSPACE_ID,base, ObjectConstraint.UNBOUNDED, false).count());
	}
	
	@Test
	public void testWorkspaceMetadataMerge() throws InvalidWorkspace, InvalidObjectName {
		QualifiedName base = QualifiedName.of(randomUrlSafeName());
		JsonObject testMetadata1 = Json.createObjectBuilder().add("Branch", "slartibartfast").build();
		JsonObject testMetadata2 = Json.createObjectBuilder().add("Team", "alcatraz").build();
		service().createWorkspace(base, State.Open, testMetadata1);
		service().updateWorkspaceByName(ROOT_WORKSPACE_ID,base, null, null, testMetadata2, false);
		Workspace fetched = (Workspace) service().getObjectByName(ROOT_WORKSPACE_ID,base);
		assertEquals("slartibartfast", fetched.getMetadata().getString("Branch"));
		assertEquals("alcatraz", fetched.getMetadata().getString("Team"));
		JsonObject testMetadata3 = Json.createObjectBuilder().add("Branch", JsonValue.NULL).build();
		service().updateWorkspaceByName(ROOT_WORKSPACE_ID,base, null, null, testMetadata3, false);
		Workspace fetched2 = (Workspace)service().getObjectByName(ROOT_WORKSPACE_ID,base);
		assertEquals(null, fetched2.getMetadata().getString("Branch",null));
		assertEquals("alcatraz", fetched2.getMetadata().getString("Team"));
	}
}
