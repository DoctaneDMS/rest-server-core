package com.softwareplumbers.dms.rest.server.model;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.ws.rs.core.MediaType;

import org.junit.Test;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractquery.JsonUtil;
import com.softwareplumbers.common.abstractquery.Query;
import com.softwareplumbers.common.abstractquery.Range;
import com.softwareplumbers.dms.rest.server.core.MediaTypes;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidDocumentId;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidObjectName;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidReference;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspace;
import com.softwareplumbers.dms.rest.server.model.Workspace.State;

import static com.softwareplumbers.dms.rest.server.model.Constants.*;
import com.softwareplumbers.dms.rest.server.model.DocumentNavigatorService.DocumentFormatException;
import com.softwareplumbers.dms.rest.server.model.DocumentNavigatorService.PartNotFoundException;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspaceState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static com.softwareplumbers.dms.rest.server.model.TestUtils.*;

/** Unit tests that should pass for all implementations of RepositoryService. 
 * 
 * Note that the underlying repository must support at least the metadata field "Description" in both workspaces and
 * documents. This is important in implementations for systems such as Filenet which have fixed schemas for content
 * metadata.
 * 
 */
public abstract class BaseRepositoryServiceTest {
	
	public abstract RepositoryService service();
    public abstract DocumentNavigatorService navigator();

	public abstract Reference randomDocumentReference();
	public abstract String randomWorkspaceId();
    public abstract JsonObject randomMetadata();
    public abstract String uniqueMetadataField();
	
	@Test
	public void testAnonymousCreateWorkspace() throws RepositoryService.BaseException {
		String workspace1 = service().createWorkspaceById(null, State.Open, null);
		assertNotNull(workspace1);
		String workspace2 = service().createWorkspaceById(null, State.Open, null);
		assertNotNull(workspace2);
		assertNotEquals(workspace1, workspace2);
	}
	
	@Test
	public void testCreateAndFindWorkspaceWithURLSafeName() throws RepositoryService.BaseException {
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
	public void testGetWorkspaceNotFoundByNameError() throws RepositoryService.BaseException {
		QualifiedName name = QualifiedName.of(randomUrlSafeName());
		Workspace test = (Workspace)service().getObjectByName(ROOT_ID,name);
	}
	
	@Test (expected = InvalidWorkspace.class)
	public void testGetWorkspaceNotFoundByIdError() throws RepositoryService.BaseException {
		Workspace test = service().getWorkspaceById(randomWorkspaceId());
	}

	@Test (expected = InvalidWorkspace.class)
	public void testUpdateWorkspaceNotFoundError() throws RepositoryService.BaseException {
		QualifiedName name = QualifiedName.of(randomUrlSafeName());
		service().updateWorkspaceByName(ROOT_ID, name, null, Workspace.State.Closed, null, false);
	}
	
	@Test (expected = InvalidWorkspace.class)
	public void testCreateDocumentNotFoundError() throws RepositoryService.BaseException, IOException {
		service().createDocument(
			MediaType.TEXT_PLAIN_TYPE, 
			()->toStream(randomText()), 
			null,
			randomWorkspaceId(), 
			false);
	}
	
	@Test (expected = InvalidDocumentId.class)
	public void testDeleteDocumentInvalidDocumentError() throws RepositoryService.BaseException {
		String workspace = service().createWorkspaceById(null, State.Open, null);
		service().deleteDocument(workspace, QualifiedName.ROOT, randomDocumentReference().getId());
	}

	@Test (expected = InvalidWorkspace.class)
	public void testDeleteDocumentInvalidWorkspaceId() throws RepositoryService.BaseException {
		Reference ref = service().createDocument(
			MediaType.TEXT_PLAIN_TYPE, 
			()->toStream(randomText()), null, null, false
		);
		service().deleteDocument(randomWorkspaceId(), QualifiedName.ROOT, ref.id);
	}
	
	@Test(expected = InvalidDocumentId.class)
	public void testListWorkspacesInvalidDocumentId() throws InvalidDocumentId {
		service().listWorkspaces(randomDocumentReference().id, QualifiedName.of("*"), Query.UNBOUNDED);
	}

	@Test
	public void testContentLength() throws RepositoryService.BaseException {
		for (int i = 0; i < 3; i++) {
			String testData = randomText();
			Reference ref = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(testData), null, null, false);
			Document doc = service().getDocument(ref);
			assertEquals(testData.length(), doc.getLength());
		}
	}
	
	@Test
	public void testRenameWorkspace() throws RepositoryService.BaseException {
		String wsId = service().createWorkspaceById(null, State.Open, null);
		QualifiedName wsName = QualifiedName.of(randomUrlSafeName());
		service().updateWorkspaceById(wsId, wsName, null, null, true);
		Workspace ws = (Workspace)service().getObjectByName(ROOT_ID, wsName);
		assertEquals(wsId, ws.getId());
	}
	
	@Test  (expected = InvalidWorkspace.class)
	public void testRenameFolderExistingName() throws RepositoryService.BaseException {
		QualifiedName wsName = QualifiedName.of(randomUrlSafeName());
		service().createWorkspaceByName(ROOT_ID,wsName, State.Open, null);
		String wsId = service().createWorkspaceById(null, State.Open, null);
		service().updateWorkspaceById(wsId,wsName, null, null, false);
	}
	
	@Test
	public void testSearchForWorkspaceByWildcard() throws RepositoryService.BaseException {
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
				
		assertEquals(4, service().catalogueByName(ROOT_ID, base.addAll("*","*"), Query.UNBOUNDED, false).count());
		assertEquals(2, service().catalogueByName(ROOT_ID,base.addAll("jones","*"), Query.UNBOUNDED, false).count());
		assertEquals(2, service().catalogueByName(ROOT_ID,base.addAll("carter","*"), Query.UNBOUNDED, false).count());
		assertEquals(3, service().catalogueByName(ROOT_ID,base.addAll("*","p*"), Query.UNBOUNDED, false).count());
		assertEquals(3, service().catalogueByName(ROOT_ID,base.addAll("*","*r"), Query.UNBOUNDED, false).count());
	}
	
	@Test
	public void testWorkspaceMetadataRoundtrip() throws RepositoryService.BaseException {
		QualifiedName base = QualifiedName.of(randomUrlSafeName());
		JsonObject testMetadata = Json.createObjectBuilder().add("Branch", "slartibartfast").build();
		service().createWorkspaceByName(ROOT_ID, base, State.Open, testMetadata);
		Workspace fetched =  (Workspace)service().getObjectByName(ROOT_ID,base);
		assertEquals("slartibartfast", fetched.getMetadata().getString("Branch"));
	}
	
	@Test
	public void testCatalogueWorkspaceWithMixedEntyTypes() throws RepositoryService.BaseException {
		QualifiedName base = QualifiedName.of(randomUrlSafeName());
		QualifiedName jones = base.add("jones");
		QualifiedName carter = base.add("carter");
		service().createWorkspaceByName(ROOT_ID, base, State.Open, null);
		service().createWorkspaceByName(ROOT_ID, jones, State.Open, null);
		service().createDocumentByName(null, carter, MediaType.TEXT_PLAIN_TYPE, ()->toStream(randomText()), null, false);
		assertEquals(2,service().catalogueByName(ROOT_ID, base.add("*"), Query.UNBOUNDED, false).count());
	}
	
	@Test
	public void testWorkspaceMetadataMerge() throws RepositoryService.BaseException {
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
	
	@Test
	public void testRepositoryCatalog() throws IOException, RepositoryService.BaseException {
		long count1 = service().catalogue(Query.UNBOUNDED, false).count();
		Reference ref1 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(randomText()), EMPTY_METADATA, null, false);
		service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(randomText()), EMPTY_METADATA, null, false);
		service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(randomText()), EMPTY_METADATA, null, false);
		service().updateDocument(ref1.id, null, ()->toStream(randomText()), EMPTY_METADATA, null, false);
		long count2 = service().catalogue(Query.UNBOUNDED, false).count();
		assertEquals(3, count2 - count1);
	}

	@Test
	public void testWorkspaceNameRoundtrip() throws RepositoryService.BaseException {
		QualifiedName name = QualifiedName.of(randomUrlSafeName());
		String wsid = service().createWorkspaceByName(ROOT_ID, name, State.Open, EMPTY_METADATA);
		Workspace workspace = service().getWorkspaceById(wsid);
		assertEquals(name, workspace.getName());
	}
	
	@Test
	public void testCreateAndFindWorkspaceWithPath() throws RepositoryService.BaseException {
		QualifiedName name = randomQualifiedName();
		String wsid = service().createWorkspaceByName(ROOT_ID, name, State.Open, EMPTY_METADATA);
		Workspace workspace = (Workspace)service().getObjectByName(ROOT_ID, name);
		assertEquals(wsid, workspace.getId());
	}

	@Test
	public void testWorkspacePathRoundtrip() throws RepositoryService.BaseException {
		QualifiedName name = randomQualifiedName();
		String wsid = service().createWorkspaceByName(ROOT_ID, name, State.Open, EMPTY_METADATA);
		Workspace workspace = service().getWorkspaceById(wsid);
		assertEquals(name, workspace.getName());
	}
	
	@Test
	public void testDocumentCreateWithRandomWorkspaceId() throws RepositoryService.BaseException {
		Reference ref = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(randomText()), EMPTY_METADATA, randomWorkspaceId(), true);
		assertEquals(1, service().listWorkspaces(ref.id, QualifiedName.of("*"), Query.UNBOUNDED).count());
	}
	

	@Test
	public void testGetObjectById() throws RepositoryService.BaseException {
		String wsId = service().createWorkspaceById(null, State.Open, EMPTY_METADATA);
		RepositoryObject ro = service().getObjectByName(wsId, QualifiedName.ROOT);
		assertEquals(RepositoryObject.Type.WORKSPACE, ro.getType());
	}
	
	@Test
	public void testGeneratedWorkspaceName() throws RepositoryService.BaseException {
		String wsId = service().createWorkspaceById(null, State.Open, EMPTY_METADATA);
		String wsId2 = service().createWorkspaceByName(wsId, null, State.Open, EMPTY_METADATA);
		RepositoryObject ro = service().getObjectByName(wsId, QualifiedName.ROOT);
		assertEquals(QualifiedName.of("~" + wsId), ((Workspace)ro).getName());
		RepositoryObject ro2 = service().getObjectByName(wsId2, QualifiedName.ROOT);
		assertEquals(2, ((Workspace)ro2).getName().size());
	}

	@Test
	public void testUpdateWorkspaceReturnsSameId() throws RepositoryService.BaseException {
		JsonObject DUMMY_METADATA = Json.createObjectBuilder()
				.add("Branch", "XYZABC")
				.add("Team", "TEAM1")
				.build();
		String wsId = service().createWorkspaceById(null, State.Open, EMPTY_METADATA);
		QualifiedName name = randomQualifiedName();
		String resultId = service().updateWorkspaceByName(wsId, QualifiedName.ROOT, name, null, DUMMY_METADATA, true);
		assertEquals(wsId, resultId);
	}
	
	@Test
	public void testCreateDocumentLink() throws RepositoryService.BaseException {
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
	public void testCreateDocumentLinkGeneratedName() throws RepositoryService.BaseException {
        QualifiedName name1 = randomQualifiedName();
        service().createWorkspaceByName(ROOT_ID, name1, State.Open, EMPTY_METADATA);
        String originalText = randomText();
        Reference ref1 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(originalText), EMPTY_METADATA, null, false);
        DocumentLink newLink = service().createDocumentLink(ROOT_ID, name1, ref1, true, true);
	    Document doc1 = (Document)service().getObjectByName(ROOT_ID, newLink.getName());
	    assertEquals(ref1, doc1.getReference());
	}
    
    @Test
	public void testCreateDocumentLinkGeneratedNameSequence() throws RepositoryService.BaseException {
        QualifiedName name1 = randomQualifiedName();
        service().createWorkspaceByName(ROOT_ID, name1, State.Open, EMPTY_METADATA);
        String originalText = randomText();
        Reference ref1 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(originalText), EMPTY_METADATA, null, false);
        String originalText2 = randomText();
        Reference ref2 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(originalText2), EMPTY_METADATA, null, false);
        DocumentLink link1 = service().createDocumentLink(ROOT_ID, name1, ref1, true, true);
        DocumentLink link2 = service().createDocumentLink(ROOT_ID, name1, ref2, true, true);
	    assertNotEquals(link1.getName(), link2.getName());
	}

    @Test
	public void testCreateDocumentLinkGeneratedNameSameObject() throws RepositoryService.BaseException {
        QualifiedName name1 = randomQualifiedName();
        service().createWorkspaceByName(ROOT_ID, name1, State.Open, EMPTY_METADATA);
        String originalText = randomText();
        Reference ref1 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(originalText), EMPTY_METADATA, null, false);
        DocumentLink link1 = service().createDocumentLink(ROOT_ID, name1, ref1, true, true);
        DocumentLink link2 = service().createDocumentLink(ROOT_ID, name1, ref1, true, true);
	    assertEquals(link1.getName(), link2.getName());
	}
    
    @Test(expected = InvalidReference.class)
	public void testCreateDocumentLinkGeneratedNameSameObjectError() throws RepositoryService.BaseException {
        QualifiedName name1 = randomQualifiedName();
        service().createWorkspaceByName(ROOT_ID, name1, State.Open, EMPTY_METADATA);
        String originalText = randomText();
        Reference ref1 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(originalText), EMPTY_METADATA, null, false);
        DocumentLink link1 = service().createDocumentLink(ROOT_ID, name1, ref1, true, true);
        DocumentLink link2 = service().createDocumentLink(ROOT_ID, name1, ref1, true, false); // false - so should be an error
	    assertEquals(link1.getName(), link2.getName());
	}
    
    @Test
	public void testUpdateDocumentLink() throws RepositoryService.BaseException {
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
	public void testUpdateDocumentByName() throws RepositoryService.BaseException {
        QualifiedName name1 = randomQualifiedName();
        service().createWorkspaceByName(ROOT_ID, name1, State.Open, EMPTY_METADATA);
        String originalText = randomText();
        Reference ref1 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(originalText), EMPTY_METADATA, null, false);
        QualifiedName docName = name1.add(randomUrlSafeName());
        service().createDocumentLinkByName(ROOT_ID, docName, ref1, true);
	    Document doc1 = (Document)service().getObjectByName(ROOT_ID, docName);
	    assertEquals(ref1, doc1.getReference());
        service().updateDocumentByName(ROOT_ID, docName, null, null, JsonUtil.parseObject("{'Description':'Text Document'}"), true, true);
	    Document doc2 = (Document)service().getObjectByName(ROOT_ID, docName);
	    assertEquals("Text Document", doc2.getMetadata().getString("Description"));
	}
    
        
    /* broadly speaking, when we have a folder id and name, test that we get the same result when reading/updating
    * the document either using the full path or the relative path from the folder id.
    */
    @Test
	public void testEquivalenceOfNameAndId() throws RepositoryService.BaseException {
        QualifiedName name1 = randomQualifiedName();
        String wsid = service().createWorkspaceByName(ROOT_ID, name1, State.Open, EMPTY_METADATA);
        String originalText = randomText();
        Reference ref1 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(originalText), EMPTY_METADATA, null, false);
        String docPart = randomUrlSafeName(); 
        QualifiedName docName = name1.add(docPart);
        service().createDocumentLinkByName(wsid, QualifiedName.of(docPart), ref1, true);
	    Document doc1 = (Document)service().getObjectByName(ROOT_ID, docName);
	    assertEquals(ref1, doc1.getReference());
        service().updateDocumentByName(wsid, QualifiedName.of(docPart), null, null, JsonUtil.parseObject("{'Description':'Text Document'}"), true, true);
	    Document doc2 = (Document)service().getObjectByName(wsid, QualifiedName.of(docPart));
	    assertEquals("Text Document", doc2.getMetadata().getString("Description"));
	}
    
    @Test
	public void testUpdateDocumentLinkInCreateMode() throws RepositoryService.BaseException {
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
	public void testSearchByDocumentId() throws RepositoryService.BaseException {
	    QualifiedName name1 = randomQualifiedName();
	    String wsId1 = service().createWorkspaceByName(ROOT_ID, name1, State.Open, EMPTY_METADATA);
	    QualifiedName name2 = randomQualifiedName();
	    String wsId2 = service().createWorkspaceByName(ROOT_ID, name2, State.Open, EMPTY_METADATA);
	    String originalText = randomText();
	    Reference ref1 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(originalText), EMPTY_METADATA, null, false);
	    service().createDocumentLinkByName(ROOT_ID, name1.add("one"), ref1, true);
	    service().createDocumentLinkByName(ROOT_ID, name2.add("two"), ref1, true);
	    Stream<DocumentLink> result = service().listWorkspaces(ref1.id, null, Query.UNBOUNDED);
	    assertEquals(2, result.count());
	}
    
    @Test
	public void testRepositorySearchByMediaType() throws IOException, RepositoryService.BaseException {
        QualifiedName name1 = randomQualifiedName();
        service().createWorkspaceByName(ROOT_ID, name1, State.Open, EMPTY_METADATA);
        String originalText = randomText();
        Reference ref1 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(originalText), EMPTY_METADATA, null, false);
        Reference ref2 = service().createDocument(MediaType.APPLICATION_OCTET_STREAM_TYPE, ()->toStream(originalText), EMPTY_METADATA, null, false);
        QualifiedName doc1Name = name1.add(randomUrlSafeName());
        QualifiedName doc2Name = name1.add(randomUrlSafeName());
        service().createDocumentLinkByName(ROOT_ID, doc1Name, ref1, true);
        service().createDocumentLinkByName(ROOT_ID, doc2Name, ref2, true);
		RepositoryObject[] result = service().catalogueByName(ROOT_ID, name1.add("*"), Query.fromJson("{ 'mediaType': 'text/plain'}"), false).toArray(RepositoryObject[]::new);
		assertEquals(1, result.length);
		assertEquals(((Document)result[0]).getReference(), ref1);
	}
    
    @Test
	public void testRepositorySearchByFolderState() throws IOException, RepositoryService.BaseException {
        QualifiedName name0 = randomQualifiedName();
        String baseId = service().createWorkspaceByName(ROOT_ID, name0, State.Open, EMPTY_METADATA);

        QualifiedName name1 = QualifiedName.ROOT.add(randomUrlSafeName());
        QualifiedName name2 = QualifiedName.ROOT.add(randomUrlSafeName());
        
        service().createWorkspaceByName(baseId, name1, State.Closed, EMPTY_METADATA);
        service().createWorkspaceByName(baseId, name2, State.Open, EMPTY_METADATA);
		RepositoryObject[] resultAll = service().catalogueByName(baseId, QualifiedName.of("*"), Query.UNBOUNDED, false).toArray(RepositoryObject[]::new);
		RepositoryObject[] result = service().catalogueByName(baseId, QualifiedName.of("*"), Query.fromJson("{ 'state': 'Closed'}"), false).toArray(RepositoryObject[]::new);
		assertEquals(2, resultAll.length);
		assertEquals(1, result.length);
		assertEquals(name0.addAll(name1), ((Workspace)result[0]).getName());
	}
    
    @Test
	public void testRepositorySearchByParentFolderStateAndMediaType() throws IOException, RepositoryService.BaseException {
        QualifiedName name0 = randomQualifiedName();
        String baseId = service().createWorkspaceByName(ROOT_ID, name0, State.Open, EMPTY_METADATA);

        QualifiedName name1 = QualifiedName.ROOT.add(randomUrlSafeName());
        QualifiedName name2 = QualifiedName.ROOT.add(randomUrlSafeName());
        String originalText = randomText();
        Reference ref1 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(originalText), EMPTY_METADATA, null, false);
        Reference ref2 = service().createDocument(MediaType.APPLICATION_OCTET_STREAM_TYPE, ()->toStream(originalText), EMPTY_METADATA, null, false);
        QualifiedName W1doc1Name = name1.add(randomUrlSafeName());
        QualifiedName W1doc2Name = name1.add(randomUrlSafeName());
        QualifiedName W2doc1Name = name2.add(randomUrlSafeName());
        QualifiedName W2doc2Name = name2.add(randomUrlSafeName());
        service().createDocumentLinkByName(baseId, W1doc1Name, ref1, true);
        service().createDocumentLinkByName(baseId, W1doc2Name, ref2, true);
        service().createDocumentLinkByName(baseId, W2doc1Name, ref1, true);
        service().createDocumentLinkByName(baseId, W2doc2Name, ref2, true);
        // Close worspace 2
        service().updateWorkspaceByName(baseId, name2, null, State.Closed, EMPTY_METADATA, false);
		JsonObject[] resultAll = service().catalogueByName(baseId, QualifiedName.of("*", "*"), Query.UNBOUNDED, false).map(item->item.toJson()).toArray(JsonObject[]::new);
		RepositoryObject[] resultClosed = service().catalogueByName(baseId, QualifiedName.of("*", "*"), Query.fromJson("{ 'parent': { 'state': 'Closed'} }"), false).toArray(RepositoryObject[]::new);
		RepositoryObject[] resultText = service().catalogueByName(baseId, QualifiedName.of("*", "*"), Query.fromJson("{ 'mediaType': 'text/plain'}"), false).toArray(RepositoryObject[]::new);
		JsonObject[] resultClosedAndText = service().catalogueByName(baseId, QualifiedName.of("*", "*"), Query.fromJson("{ 'mediaType': 'text/plain', 'parent': { 'state': 'Closed'}}"), false).map(item->item.toJson()).toArray(JsonObject[]::new);
		assertEquals(4, resultAll.length);
		assertEquals(2, resultClosed.length);
		assertEquals(2, resultText.length);
		assertEquals(1, resultClosedAndText.length);
	}
    
    private long countMatchingMetadata(List<NamedRepositoryObject> items, Predicate<JsonObject> match) {
        return items.stream().map(item->item.getMetadata()).filter(match).count();
    }
        
    @Test
	public void testRepositorySearchContent() throws IOException, RepositoryService.BaseException {
        QualifiedName name0 = randomQualifiedName();
        String baseId = service().createWorkspaceByName(ROOT_ID, name0, State.Open, EMPTY_METADATA);

        QualifiedName name1 = QualifiedName.ROOT.add(randomUrlSafeName());
        QualifiedName name2 = QualifiedName.ROOT.add(randomUrlSafeName());
        String originalText = randomText();
        Reference ref1 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(originalText), JsonUtil.parseObject("{'Description':'Text Document'}"), null, false);
        Reference ref2 = service().createDocument(MediaType.APPLICATION_OCTET_STREAM_TYPE, ()->toStream(originalText), JsonUtil.parseObject("{'Description':'Binary Document'}"), null, false);
        QualifiedName W1doc1Name = name1.add(randomUrlSafeName());
        QualifiedName W1doc2Name = name1.add(randomUrlSafeName());
        QualifiedName W2doc1Name = name2.add(randomUrlSafeName());
        QualifiedName W2doc2Name = name2.add(randomUrlSafeName());
        service().createDocumentLinkByName(baseId, W1doc1Name, ref1, true);
        service().createDocumentLinkByName(baseId, W1doc2Name, ref2, true);
        service().createDocumentLinkByName(baseId, W2doc1Name, ref1, true);
        service().createDocumentLinkByName(baseId, W2doc2Name, ref2, true);
        // Close workspace 2
        service().updateWorkspaceByName(baseId, name2, null, State.Closed, JsonUtil.parseObject("{'Description':'Closed Workspace'}"), false);
        service().updateWorkspaceByName(baseId, name1, null, null, JsonUtil.parseObject("{'Description':'Open Workspace'}"), false);
		List<NamedRepositoryObject> resultAll = service().catalogueByName(baseId, QualifiedName.of("*", "*"), Query.UNBOUNDED, false).collect(Collectors.toList());
        
        assertEquals(2, countMatchingMetadata(resultAll, m->Objects.equals(m.getString("Description"), "Text Document")));
	}

    @Test
	public void testGetDocumentLink() throws RepositoryService.BaseException, IOException {
        QualifiedName name0 = randomQualifiedName();
        String baseId = service().createWorkspaceByName(ROOT_ID, name0, State.Open, EMPTY_METADATA);

        // Create a random document
        String originalText = randomText();        
        Reference ref1 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(originalText), JsonUtil.parseObject("{'Description':'Text Document'}"), null, false);

        // Add document to a random folder
        QualifiedName name1 = QualifiedName.ROOT.add(randomUrlSafeName());
        QualifiedName W1doc1Name = name1.add(randomUrlSafeName());
        service().createDocumentLinkByName(baseId, W1doc1Name, ref1, true);
        
        // Get the document back again
        DocumentLink link = service().getDocumentLink(baseId, name1, ref1.id);        
        assertEquals(originalText, getDocText(link));

        // Get the document back again using implicit root
        DocumentLink link2 = service().getDocumentLink(null, name0.addAll(name1), ref1.id);
        assertEquals(originalText, getDocText(link2));
    }
    
    @Test
    public void testJsonRepresentation() throws RepositoryService.BaseException {
        QualifiedName name1 = randomQualifiedName();
        service().createWorkspaceByName(ROOT_ID, name1, State.Open, EMPTY_METADATA);
        String originalText = randomText();
        Reference ref1 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(originalText), EMPTY_METADATA, null, false);
        Document doc1 = (Document)service().getDocument(ref1);
        JsonObject json = doc1.toJson(service(), navigator(), 0, 0);
        assertEquals(ref1.id, json.getString("id"));
        assertEquals(ref1.version, json.getString("version"));
        assertEquals(MediaType.TEXT_PLAIN, json.getString("mediaType"));
        assertEquals(false, json.getBoolean("navigable"));
	}
    
    @Test
    public void testJsonRepresentationDocumentPart() throws IOException, DocumentFormatException, PartNotFoundException {
        DocumentImpl zipDoc = new DocumentImpl(new Reference("test"), MediaTypes.ZIP, ()->BaseRepositoryServiceTest.class.getResourceAsStream("/testzipdir.zip"), EMPTY_METADATA);
        DocumentPart testDocx = navigator().getPartByName(zipDoc, QualifiedName.of("test", "subdir", "testdoc.docx"));
        JsonObject json = testDocx.toJson(service(), navigator(), 0, 0);
        assertEquals("test", json.getJsonObject("document").getString("id"));
        assertEquals(MediaTypes.MICROSOFT_WORD_XML.toString(), json.getString("mediaType"));
        assertEquals(false, json.getBoolean("navigable"));
	}
    
    	@Test
	public void testRepositoryRoundtrip() throws IOException, RepositoryService.BaseException {
        String originalText = randomText();
        Reference ref1 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(originalText), EMPTY_METADATA, null, false);
        Document result = service().getDocument(ref1);
        assertEquals(originalText, IOUtils.toString(result.getData(), "UTF-8"));
	}
		
	@Test(expected = InvalidReference.class)
	public void testRepositoryFetchWithInvalidVersion() throws IOException, RepositoryService.BaseException {
        String originalText = randomText();
        Reference ref1 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(originalText), EMPTY_METADATA, null, false);
        Document result = service().getDocument(new Reference(ref1.id, "777"));
	}
	
	@Test
	public void testRepositoryFetchWithNoVersion() throws IOException, RepositoryService.BaseException {
        String originalText = randomText();
        Reference ref1 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(originalText), EMPTY_METADATA, null, false);
        Document result = service().getDocument(new Reference(ref1.id, null));
        assertEquals(originalText, getDocText(result));
	}
		
	@Test
	public void testRepositorySearch() throws IOException, InvalidWorkspace {
        
        ArrayList<byte[]> dataValues = new ArrayList<>();
        ArrayList<JsonObject> metadataValues = new ArrayList<>();
        
        generateDocs(4, this::randomMetadata, (data, metadata, type) -> {
            dataValues.add(data);
            metadataValues.add(metadata);
            try {
                service().createDocument(type, ()->new ByteArrayInputStream(data), metadata, null, false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        
        int itemToSearch = 3;
        String indexField = uniqueMetadataField();
        JsonValue indexValue = metadataValues.get(itemToSearch).get(indexField);
        
        Query search = Query.from("metadata", Query.from(indexField, Range.equals(indexValue)));
        
		RepositoryObject[] result = service().catalogue(search, false).toArray(RepositoryObject[]::new);
		
        assertEquals(result.length, 1);
		assertEquals(getDocText((Document)result[0]), IOUtils.toString(dataValues.get(itemToSearch), "UTF-8"));
	}
	
	
	@Test 
	public void testWorkspaceUpdate() throws RepositoryService.BaseException {
		String workspace_id = randomWorkspaceId();
        String originalText = randomText();
        Reference ref1 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(originalText), EMPTY_METADATA, null, false);
		service().updateDocument(ref1.id, null, null, null, workspace_id, true);
		assertEquals(service().catalogueById(workspace_id, null, false).count(), 1);
		service().deleteDocument(workspace_id, QualifiedName.ROOT, ref1.id);
		assertEquals(service().catalogueById(workspace_id, null, false).count(), 0);
	}
	
	@Test 
	public void testListWorkspaces() throws RepositoryService.BaseException {
		String workspace1 = randomWorkspaceId();
		String workspace2 = randomWorkspaceId();
        Reference ref1 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(randomText()), EMPTY_METADATA, null, false);
        Reference ref2 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(randomText()), EMPTY_METADATA, null, false);
        Reference ref3 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(randomText()), EMPTY_METADATA, null, false);
		service().updateDocument(ref1.id, null, null, null, workspace1, true);
		service().updateDocument(ref1.id, null, null, null, workspace2, true);
		service().updateDocument(ref2.id, null, null, null, workspace2, true);
		service().updateDocument(ref3.id, null, null, null, workspace2, true);
		assertEquals(2, service().listWorkspaces(ref1.id, null, Query.UNBOUNDED).count());
		assertEquals(1, service().listWorkspaces(ref2.id, null, Query.UNBOUNDED).count());
		assertEquals(1, service().listWorkspaces(ref3.id, null, Query.UNBOUNDED).count());
	}
    
    
    
    //////------- Versioning tests --------//////
  
    /** 
     *  Important - what should be returned when we have multiple versions of a document matching criteria.Definiton 
     * is that we return the latest version which matches the criteria.
	 * 
	 *
     * @throws RepositoryService.BaseException
	 */
	@Test 
	public void testRepositoryCatalogWithVersions() throws RepositoryService.BaseException {
		long count1 = service().catalogue(Query.UNBOUNDED, true).count();
		Reference ref1 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(randomText()), EMPTY_METADATA, null, false);
		service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(randomText()), EMPTY_METADATA, null, false);
		service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(randomText()), EMPTY_METADATA, null, false);
		service().updateDocument(ref1.id, null, ()->toStream(randomText()), EMPTY_METADATA, null, false);
		long count2 = service().catalogue(Query.UNBOUNDED, true).count();
		assertEquals(3, count2 - count1);
	}
    
	@Test
	public void testGetDocumentWithWorkspaceId() throws IOException, RepositoryService.BaseException {
		String wsId = service().createWorkspaceById(null, State.Open, EMPTY_METADATA);
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
		String wsDoc = getDocText(service().getDocumentLink(wsId, QualifiedName.ROOT, ref1.id));
		assertEquals(originalText, wsDoc); 
	}
    
    @Test
    public void testGetDocumentVersions() throws RepositoryService.BaseException {
		Reference ref1 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(randomText()), EMPTY_METADATA, null, false);
		Reference ref2 = service().updateDocument(ref1.id, MediaType.TEXT_PLAIN_TYPE, ()->toStream(randomText()), EMPTY_METADATA, null, false);
 		Reference ref3 = service().updateDocument(ref1.id, MediaType.TEXT_PLAIN_TYPE, ()->toStream(randomText()), EMPTY_METADATA, null, false);
      
        Reference[] refs = { ref1, ref2, ref3 };
        
        // Check the base document id is the same
        assertEquals(ref1.id, ref2.id);
        assertEquals(ref1.id, ref3.id);
      
        // Check the version id is different
        assertNotEquals(ref1.version, ref2.version);
        assertNotEquals(ref1.version, ref3.version);
        assertNotEquals(ref2.version, ref3.version);
        
        Document[] versions = service().catalogueHistory(ref3, Query.UNBOUNDED).toArray(Document[]::new);
        
        assertEquals(3, versions.length);
        
        assertThat(versions[0].getReference(), isIn(refs));
        assertThat(versions[1].getReference(), isIn(refs));
        assertThat(versions[2].getReference(), isIn(refs));
    }
    
    @Test
    public void testGetVersionedDocumentFromWorkspace() throws RepositoryService.BaseException, IOException {
        // Create  a document
        String textv1 = randomText();
        Reference ref1 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(textv1), EMPTY_METADATA, null, false);
        // Create a workspace
        QualifiedName workspaceName = randomQualifiedName();
        service().createWorkspaceByName(ROOT_ID, workspaceName, State.Open, EMPTY_METADATA);
        // Link document to workspace
        service().createDocumentLink(ROOT_ID, workspaceName, ref1, true, true);
        // Update document
        String textv2 = randomText();
		Reference ref2 = service().updateDocument(ref1.id, MediaType.TEXT_PLAIN_TYPE, ()->toStream(textv2), null, null, false);
        // Close workspace
        service().updateWorkspaceByName(ROOT_ID, workspaceName, null, State.Closed, null, false);
        // Update document again
        String textv3 = randomText();
 		Reference ref3 = service().updateDocument(ref1.id, MediaType.TEXT_PLAIN_TYPE, ()->toStream(textv3), null, null, false);
        // Now retrieve document from workspace
        DocumentLink doc = service().getDocumentLink(ROOT_ID, workspaceName, ref1.id);
        // We should see the version of the document that was current when the workspace was closed
        assertEquals(ref2, doc.getReference());
        // Double check
        assertEquals(textv2, getDocText(doc));
    }
    
    @Test(expected = InvalidWorkspaceState.class)
    public void testWorkspaceUpdateDoesntWorkIfWorkspaceClosed() throws RepositoryService.BaseException {
        // Create  a document
        String textv1 = randomText();
        Reference ref1 = service().createDocument(MediaType.TEXT_PLAIN_TYPE, ()->toStream(textv1), EMPTY_METADATA, null, false);
        // Create a workspace
        QualifiedName workspaceName = randomQualifiedName();
        service().createWorkspaceByName(ROOT_ID, workspaceName, State.Open, EMPTY_METADATA);
        // Link document to workspace
        QualifiedName documentName = workspaceName.add(randomUrlSafeName());
        service().createDocumentLinkByName(ROOT_ID, documentName, ref1, true);
        // Close workspace
        service().updateWorkspaceByName(ROOT_ID, workspaceName, null, State.Closed, null, false);
        // Update document again
        String textv2 = randomText();
 		Reference ref3 = service().updateDocumentByName(ROOT_ID, documentName, MediaType.WILDCARD_TYPE, ()->toStream(textv2), EMPTY_METADATA, true, true);
    }
    
    //////------- End Versioning tests --------//////
}
