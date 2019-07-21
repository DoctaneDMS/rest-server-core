package com.softwareplumbers.dms.rest.server.model;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.ws.rs.core.MediaType;

import org.junit.Test;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractquery.ObjectConstraint;
import com.softwareplumbers.common.abstractquery.Range;
import com.softwareplumbers.common.abstractquery.Value;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidDocumentId;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidObjectName;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidReference;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspace;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspaceState;
import com.softwareplumbers.dms.rest.server.model.Workspace.State;

import static com.softwareplumbers.dms.rest.server.model.Constants.*;

/** Unit tests that should pass for all implementations of RepositoryService. 
 * 
 */
public abstract class BaseRepositoryServiceTest {
	
	public abstract RepositoryService service();
	
	public static final String[] names = { "julien", "peter", "fairfax", "austen", "celtic", "a", "the", "halibut", "eaten" };
	public static final String characters = "-._";
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
	
	public QualifiedName randomQualifiedName() {
		QualifiedName result = QualifiedName.ROOT;
		for (int i = 0; i < 3; i++) result = result.add(randomUrlSafeName());
		return result;
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

	public static final String getDocText(Document doc) throws IOException {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		doc.writeDocument(stream);
		return new String(stream.toByteArray());
	}
	

	public abstract Reference randomDocumentReference();
	public abstract String randomWorkspaceId();
	
	@Test
	public void testAnonymousCreateWorkspace() throws InvalidWorkspace {
		String workspace1 = service().createWorkspaceById(null, null, State.Open, null);
		assertNotNull(workspace1);
		String workspace2 = service().createWorkspaceById(null, null, State.Open, null);
		assertNotNull(workspace1);
		assertNotEquals(workspace1, workspace2);
	}
	
	@Test
	public void testCreateAndFindWorkspaceWithURLSafeName() throws InvalidWorkspace, InvalidObjectName {
		QualifiedName name = QualifiedName.of(randomUrlSafeName());
		String workspace = service().createWorkspaceByName(ROOT_ID, name, State.Open, null);
		Workspace ws = (Workspace)service().getObjectByName(ROOT_ID, name);
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
		Workspace test = (Workspace)service().getObjectByName(ROOT_ID,name);
	}
	
	@Test (expected = InvalidWorkspace.class)
	public void testGetWorkspaceNotFoundByIdError() throws InvalidWorkspace {
		Workspace test = service().getWorkspaceById(randomWorkspaceId());
	}

	@Test (expected = InvalidWorkspace.class)
	public void testUpdateWorkspaceNotFoundError() throws InvalidWorkspace {
		QualifiedName name = QualifiedName.of(randomUrlSafeName());
		service().updateWorkspaceByName(ROOT_ID, name, null, Workspace.State.Closed, null, false);
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
		String workspace = service().createWorkspaceById(null, null, State.Open, null);
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
		assertEquals(0L,service().listWorkspaces(randomDocumentReference().id, QualifiedName.of("*")).count());
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
		String wsId = service().createWorkspaceById(null, null, State.Open, null);
		QualifiedName wsName = QualifiedName.of(randomUrlSafeName());
		service().updateWorkspaceById(wsId, wsName, null, null, true);
		Workspace ws = (Workspace)service().getObjectByName(ROOT_ID, wsName);
		assertEquals(wsId, ws.getId());
	}
	
	@Test  (expected = InvalidWorkspace.class)
	public void testRenameFolderExistingName() throws InvalidWorkspace {
		QualifiedName wsName = QualifiedName.of(randomUrlSafeName());
		service().createWorkspaceByName(ROOT_ID,wsName, State.Open, null);
		String wsId = service().createWorkspaceById(null, null, State.Open, null);
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
		service().createWorkspaceByName(ROOT_ID, peter_jones, State.Open, null);
		service().createWorkspaceByName(ROOT_ID, peter_carter, State.Open, null);
		service().createWorkspaceByName(ROOT_ID, pamela_jones, State.Open, null);
		service().createWorkspaceByName(ROOT_ID, roger_carter, State.Open, null);
				
		assertEquals(4, service().catalogueByName(ROOT_ID, base.addAll("*","*"), ObjectConstraint.UNBOUNDED, false).count());
		assertEquals(2, service().catalogueByName(ROOT_ID,base.addAll("jones","*"), ObjectConstraint.UNBOUNDED, false).count());
		assertEquals(2, service().catalogueByName(ROOT_ID,base.addAll("carter","*"), ObjectConstraint.UNBOUNDED, false).count());
		assertEquals(3, service().catalogueByName(ROOT_ID,base.addAll("*","p*"), ObjectConstraint.UNBOUNDED, false).count());
		assertEquals(3, service().catalogueByName(ROOT_ID,base.addAll("*","*r"), ObjectConstraint.UNBOUNDED, false).count());
	}
	
	@Test
	public void testWorkspaceMetadataRoundtrip() throws InvalidWorkspace, InvalidObjectName {
		QualifiedName base = QualifiedName.of(randomUrlSafeName());
		JsonObject testMetadata = Json.createObjectBuilder().add("Branch", "slartibartfast").build();
		service().createWorkspaceByName(ROOT_ID, base, State.Open, testMetadata);
		Workspace fetched =  (Workspace)service().getObjectByName(ROOT_ID,base);
		assertEquals("slartibartfast", fetched.getMetadata().getString("Branch"));
	}
	
	@Test
	public void testCatalogueWorkspaceWithMixedEntyTypes() throws InvalidWorkspace, InvalidWorkspaceState, InvalidObjectName {
		QualifiedName base = QualifiedName.of(randomUrlSafeName());
		QualifiedName jones = base.add("jones");
		QualifiedName carter = base.add("carter");
		service().createWorkspaceByName(ROOT_ID, base, State.Open, null);
		service().createWorkspaceByName(ROOT_ID, jones, State.Open, null);
		service().createDocumentByName(null, carter, MediaType.TEXT_PLAIN_TYPE, ()->toStream(randomText()), null, false);
		assertEquals(2,service().catalogueByName(ROOT_ID, base.add("*"), ObjectConstraint.UNBOUNDED, false).count());
	}
	
	@Test
	public void testWorkspaceMetadataMerge() throws InvalidWorkspace, InvalidObjectName {
		QualifiedName base = QualifiedName.of(randomUrlSafeName());
		JsonObject testMetadata1 = Json.createObjectBuilder().add("Branch", "slartibartfast").build();
		JsonObject testMetadata2 = Json.createObjectBuilder().add("Team", "alcatraz").build();
		service().createWorkspaceByName(ROOT_ID, base, State.Open, testMetadata1);
		service().updateWorkspaceByName(ROOT_ID,base, null, null, testMetadata2, false);
		Workspace fetched = (Workspace) service().getObjectByName(ROOT_ID,base);
		assertEquals("slartibartfast", fetched.getMetadata().getString("Branch"));
		assertEquals("alcatraz", fetched.getMetadata().getString("Team"));
		JsonObject testMetadata3 = Json.createObjectBuilder().add("Branch", JsonValue.NULL).build();
		service().updateWorkspaceByName(ROOT_ID,base, null, null, testMetadata3, false);
		Workspace fetched2 = (Workspace)service().getObjectByName(ROOT_ID,base);
		assertEquals(null, fetched2.getMetadata().getString("Branch",null));
		assertEquals("alcatraz", fetched2.getMetadata().getString("Team"));
	}

	/** Important - what should be returned when we have multiple versions of a document matching criteria.
	 * 
	 * Definiton is that we return the latest version which matches the criteria.
	 * 
	 */
	@Test
	public void testRepositoryCatalogWithVersions() throws IOException, InvalidDocumentId, InvalidWorkspace, InvalidWorkspaceState, InvalidReference {
		long count1 = service().catalogue(ObjectConstraint.UNBOUNDED, true).count();
		Reference ref1 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(randomText()), EMPTY_METADATA, null, false);
		service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(randomText()), EMPTY_METADATA, null, false);
		service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(randomText()), EMPTY_METADATA, null, false);
		service().updateDocument(ref1.id, null, ()->toStream(randomText()), EMPTY_METADATA, null, false);
		long count2 = service().catalogue(ObjectConstraint.UNBOUNDED, true).count();
		assertEquals(3, count2 - count1);
	}
	
	@Test
	public void testRepositoryCatalog() throws IOException, InvalidWorkspace, InvalidWorkspaceState, InvalidDocumentId {
		long count1 = service().catalogue(ObjectConstraint.UNBOUNDED, false).count();
		Reference ref1 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(randomText()), EMPTY_METADATA, null, false);
		service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(randomText()), EMPTY_METADATA, null, false);
		service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(randomText()), EMPTY_METADATA, null, false);
		service().updateDocument(ref1.id, null, ()->toStream(randomText()), EMPTY_METADATA, null, false);
		long count2 = service().catalogue(ObjectConstraint.UNBOUNDED, false).count();
		assertEquals(3, count2 - count1);
	}

		@Test
	public void testWorkspaceNameRoundtrip() throws InvalidWorkspace {
		QualifiedName name = QualifiedName.of(randomUrlSafeName());
		String wsid = service().createWorkspaceByName(ROOT_ID, name, State.Open, EMPTY_METADATA);
		Workspace workspace = service().getWorkspaceById(wsid);
		assertEquals(name, workspace.getName());
	}
	
	@Test
	public void testCreateAndFindWorkspaceWithPath() throws InvalidWorkspace, InvalidObjectName {
		QualifiedName name = randomQualifiedName();
		String wsid = service().createWorkspaceByName(ROOT_ID, name, State.Open, EMPTY_METADATA);
		Workspace workspace = (Workspace)service().getObjectByName(ROOT_ID, name);
		assertEquals(wsid, workspace.getId());
	}

	@Test
	public void testWorkspacePathRoundtrip() throws InvalidWorkspace {
		QualifiedName name = randomQualifiedName();
		String wsid = service().createWorkspaceByName(ROOT_ID, name, State.Open, EMPTY_METADATA);
		Workspace workspace = service().getWorkspaceById(wsid);
		assertEquals(name, workspace.getName());
	}
	
	@Test
	public void testDocumentCreateWithRandomWorkspaceId() throws InvalidWorkspace, InvalidWorkspaceState {
		Reference ref = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(randomText()), EMPTY_METADATA, randomWorkspaceId(), true);
		assertEquals(1, service().listWorkspaces(ref.id, QualifiedName.of("*")).count());
	}
	
	@Test
	public void testGetDocumentWithWorkspaceId() throws IOException, InvalidDocumentId, InvalidWorkspace, InvalidWorkspaceState, InvalidReference {
		String wsId = service().createWorkspaceById(null, null, State.Open, EMPTY_METADATA);
		String originalText = randomText();
		// Create a document in the workspace
		Reference ref1 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(originalText), EMPTY_METADATA, wsId, false);
		// now close the workspace
		service().updateWorkspaceById(wsId, null, State.Closed, EMPTY_METADATA, false);
		Reference ref2 = service().updateDocument(ref1.id, null, ()->toStream(randomText()), EMPTY_METADATA, null, false);
		assertEquals(ref1.id, ref2.id);
		String doc1 = getDocText(service().getDocument(ref1));
		String doc2 = getDocText(service().getDocument(ref2));
		assertNotEquals(doc1, doc2);	
		String wsDoc = getDocText(service().getDocument(ref1.id, wsId));
		// assertEquals(originalText, wsDoc); <-- TODO: this is broken for now in filenet impl
	}
	
	@Test
	public void testGetObjectById() throws InvalidWorkspace, InvalidObjectName {
		String wsId = service().createWorkspaceById(null, null, State.Open, EMPTY_METADATA);
		RepositoryObject ro = service().getObjectByName(wsId, QualifiedName.ROOT);
		assertEquals(RepositoryObject.Type.WORKSPACE, ro.getType());
	}
	
	@Test
	public void testGeneratedWorkspaceName() throws InvalidWorkspace, InvalidObjectName {
		String wsId = service().createWorkspaceById(null, null, State.Open, EMPTY_METADATA);
		String wsId2 = service().createWorkspaceByName(wsId, null, State.Open, EMPTY_METADATA);
		RepositoryObject ro = service().getObjectByName(wsId, QualifiedName.ROOT);
		assertEquals(QualifiedName.of("~" + wsId), ((Workspace)ro).getName());
		RepositoryObject ro2 = service().getObjectByName(wsId2, QualifiedName.ROOT);
		assertEquals(2, ((Workspace)ro2).getName().size());
	}

	@Test
	public void testUpdateWorkspaceReturnsSameId() throws InvalidWorkspace, InvalidObjectName {
		JsonObject DUMMY_METADATA = Json.createObjectBuilder()
				.add("Branch", "XYZABC")
				.add("Team", "TEAM1")
				.build();
		String wsId = service().createWorkspaceById(null, null, State.Open, EMPTY_METADATA);
		QualifiedName name = randomQualifiedName();
		String resultId = service().updateWorkspaceByName(wsId, QualifiedName.ROOT, name, null, DUMMY_METADATA, true);
		assertEquals(wsId, resultId);
	}
	
	@Test
	public void testCreateDocumentLink() throws InvalidWorkspace, InvalidWorkspaceState, InvalidObjectName, InvalidReference {
        QualifiedName name1 = randomQualifiedName();
        service().createWorkspaceByName(ROOT_ID, name1, State.Open, EMPTY_METADATA);
        String originalText = randomText();
        Reference ref1 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(originalText), EMPTY_METADATA, null, false);
        QualifiedName docName = name1.add(randomUrlSafeName());
        service().createDocumentLinkByName(ROOT_ID, docName, ref1, true);
	    Document doc1 = (Document)service().getObjectByName(ROOT_ID, docName);
	    assertEquals(ref1, doc1.getReference());
	}
    
    @Test
	public void testUpdateDocumentLink() throws InvalidWorkspace, InvalidWorkspaceState, InvalidObjectName, InvalidReference {
        QualifiedName name1 = randomQualifiedName();
        service().createWorkspaceByName(ROOT_ID, name1, State.Open, EMPTY_METADATA);
        String originalText = randomText();
        Reference ref1 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(originalText), EMPTY_METADATA, null, false);
        Reference ref2 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(originalText), EMPTY_METADATA, null, false);
        QualifiedName docName = name1.add(randomUrlSafeName());
        service().createDocumentLinkByName(ROOT_ID, docName, ref1, true);
	    Document doc1 = (Document)service().getObjectByName(ROOT_ID, docName);
	    assertEquals(ref1, doc1.getReference());
        service().updateDocumentLinkByName(ROOT_ID, docName, ref2, false, false);
	    Document doc2 = (Document)service().getObjectByName(ROOT_ID, docName);
	    assertEquals(ref2, doc2.getReference());
	}
    
    @Test
	public void testUpdateDocumentLinkInCreateMode() throws InvalidWorkspace, InvalidWorkspaceState, InvalidObjectName, InvalidReference {
        QualifiedName name1 = randomQualifiedName();
        service().createWorkspaceByName(ROOT_ID, name1, State.Open, EMPTY_METADATA);
        String originalText = randomText();
        Reference ref1 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(originalText), EMPTY_METADATA, null, false);
        Reference ref2 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(originalText), EMPTY_METADATA, null, false);
        QualifiedName docName = name1.add(randomUrlSafeName());
        service().updateDocumentLinkByName(ROOT_ID, docName, ref1, false, true);
	    Document doc1 = (Document)service().getObjectByName(ROOT_ID, docName);
	    assertEquals(ref1, doc1.getReference());
    }
	
	@Test 
	public void testSearchByDocumentId() throws InvalidWorkspace, InvalidWorkspaceState, InvalidDocumentId, InvalidObjectName, InvalidReference  {
	    QualifiedName name1 = randomQualifiedName();
	    String wsId1 = service().createWorkspaceByName(ROOT_ID, name1, State.Open, EMPTY_METADATA);
	    QualifiedName name2 = randomQualifiedName();
	    String wsId2 = service().createWorkspaceByName(ROOT_ID, name2, State.Open, EMPTY_METADATA);
	    String originalText = randomText();
	    Reference ref1 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(originalText), EMPTY_METADATA, null, false);
	    service().createDocumentLinkByName(ROOT_ID, name1.add("one"), ref1, true);
	    service().createDocumentLinkByName(ROOT_ID, name2.add("two"), ref1, true);
	    Stream<DocumentLink> result = service().listWorkspaces(ref1.id, null);
	    assertEquals(2, result.count());
	}
}
