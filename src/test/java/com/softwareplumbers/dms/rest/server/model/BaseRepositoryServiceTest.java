package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.dms.DocumentNavigatorService;
import com.softwareplumbers.dms.NamedRepositoryObject;
import com.softwareplumbers.dms.Workspace;
import com.softwareplumbers.dms.RepositoryObject;
import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.DocumentPart;
import com.softwareplumbers.dms.DocumentLink;
import com.softwareplumbers.dms.Document;
import com.softwareplumbers.dms.RepositoryService;
import com.softwareplumbers.dms.Options;
import com.softwareplumbers.dms.Exceptions.*;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;

import org.junit.Test;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractquery.JsonUtil;
import static com.softwareplumbers.common.abstractquery.JsonUtil.parseObject;
import com.softwareplumbers.common.abstractquery.Query;
import com.softwareplumbers.common.abstractquery.Range;
import com.softwareplumbers.dms.Constants;
import com.softwareplumbers.dms.rest.server.core.MediaTypes;
import com.softwareplumbers.dms.Exceptions.*;
import com.softwareplumbers.dms.Workspace.State;

import static com.softwareplumbers.dms.Constants.*;
import com.softwareplumbers.dms.DocumentNavigatorService.DocumentFormatException;
import com.softwareplumbers.dms.DocumentNavigatorService.PartNotFoundException;
import com.softwareplumbers.dms.common.test.DocumentServiceTest;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static com.softwareplumbers.dms.common.test.TestUtils.*;

import static org.hamcrest.Matchers.*;

/** Unit tests that should pass for all implementations of RepositoryService. 
 * 
 * Note that the underlying repository must support at least the metadata field "Description" in both workspaces and
 * documents. This is important in implementations for systems such as Filenet which have fixed schemas for content
 * metadata.
 * 
 */
public abstract class BaseRepositoryServiceTest extends DocumentServiceTest {
	
    @Override
	public abstract RepositoryService service();
    @Override
	public abstract Reference randomDocumentReference();
    @Override
    public abstract JsonObject randomDocumentMetadata();
            
    public abstract DocumentNavigatorService navigator();
 	public abstract String randomWorkspaceId();
    public abstract JsonObject randomWorkspaceMetadata();
    public abstract String uniqueMetadataField();
		
	@Test
	public void testCreateAndFindWorkspaceWithURLSafeName() throws BaseException {
		QualifiedName name = QualifiedName.of(randomUrlSafeName());
		Workspace ws1 = service().createWorkspaceByName(ROOT_ID, name, State.Open, null);
		Workspace ws = (Workspace)service().getObjectByName(ROOT_ID, name);
		assertEquals(ws1.getId(), ws.getId());
	}
       
    @Test 
	public void testWorkspaceCopyById() throws BaseException {
        JsonObject metadata = randomWorkspaceMetadata();
		Workspace workspace = service().createWorkspaceByName(ROOT_ID, randomQualifiedName(), State.Open , metadata, Options.CREATE_MISSING_PARENT);
        QualifiedName newName = randomQualifiedName();
        service().copyWorkspace(workspace.getId(), QualifiedName.ROOT, Constants.ROOT_ID, newName, true);
        Workspace result = service().getWorkspaceByName(ROOT_ID, newName);
        assertEquals(metadata, result.getMetadata());
	}
    
    @Test 
	public void testWorkspaceCopyByName() throws BaseException {
        JsonObject metadata = randomWorkspaceMetadata();
		Workspace workspace = service().createWorkspaceByName(ROOT_ID, randomQualifiedName(), State.Open , metadata, Options.CREATE_MISSING_PARENT);
        QualifiedName newName = randomQualifiedName();
        service().copyWorkspace(Constants.ROOT_ID, workspace.getName(), Constants.ROOT_ID, newName, true);
        Workspace result = service().getWorkspaceByName(ROOT_ID, newName);
        assertEquals(metadata, result.getMetadata());
	}

    @Test(expected = InvalidWorkspace.class)
	public void testWorkspaceCopyByNameOriginalMustExist() throws BaseException {
        service().copyWorkspace(Constants.ROOT_ID, randomQualifiedName(), Constants.ROOT_ID, randomQualifiedName(), true);
	}

   @Test(expected = InvalidWorkspace.class)
	public void testWorkspaceCopyByIdWithNewPathOriginalMustExist() throws BaseException {
        service().copyWorkspace(randomWorkspaceId(), QualifiedName.ROOT, Constants.ROOT_ID, randomQualifiedName(), true);
	}

	
	@Test (expected = InvalidObjectName.class)
	public void testGetWorkspaceNotFoundByNameError() throws BaseException {
		QualifiedName name = QualifiedName.of(randomUrlSafeName());
		Workspace test = (Workspace)service().getObjectByName(ROOT_ID,name);
	}

	@Test (expected = InvalidWorkspace.class)
	public void testUpdateWorkspaceNotFoundError() throws BaseException {
		QualifiedName name = QualifiedName.of(randomUrlSafeName());
		service().updateWorkspaceByName(ROOT_ID, name, Workspace.State.Closed, null);
	}
    
	@Test (expected = InvalidDocumentId.class)
	public void testDeleteDocumentInvalidDocumentError() throws BaseException {
		Workspace workspace = service().createWorkspaceAndName(ROOT_ID, null, State.Open, null);
		service().deleteDocument(workspace.getId(), QualifiedName.ROOT, randomDocumentReference().getId());
	}

	@Test (expected = InvalidWorkspace.class)
	public void testDeleteDocumentInvalidWorkspaceId() throws BaseException {
		Reference ref = service().createDocument(
			"text/plain", 
			()->toStream(randomText()), null
		);
		service().deleteDocument(randomWorkspaceId(), QualifiedName.ROOT, ref.id);
	}
	
	@Test(expected = InvalidDocumentId.class)
	public void testListWorkspacesInvalidDocumentId() throws InvalidDocumentId {
		service().listWorkspaces(randomDocumentReference().id, QualifiedName.of("*"), Query.UNBOUNDED);
	}


	@Test
	public void testCopyWorkspace() throws BaseException {
        JsonObject metadata = randomWorkspaceMetadata();
		Workspace workspace = service().createWorkspaceAndName(Constants.ROOT_ID, QualifiedName.ROOT, State.Open, metadata);
        service().createDocumentLinkAndName(workspace, "text/plain", ()->toStream(randomText()), Constants.EMPTY_METADATA);
		QualifiedName wsName = QualifiedName.of(randomUrlSafeName());
		service().copyWorkspace(workspace.getId(), QualifiedName.ROOT, Constants.ROOT_ID, wsName, false);
		Workspace copy = (Workspace)service().getObjectByName(ROOT_ID, wsName);
		assertEquals(workspace.getMetadata(), copy.getMetadata());
        assertEquals(1, service().catalogueByName(copy, QualifiedName.of("*"), Query.UNBOUNDED, false).count());
	}
	
	@Test  (expected = InvalidWorkspace.class)
	public void testCopyFolderExistingName() throws BaseException {
		QualifiedName wsName = QualifiedName.of(randomUrlSafeName());
		Workspace a = service().createWorkspaceByName(ROOT_ID, wsName, State.Open, null);
		Workspace b = service().createWorkspaceAndName(ROOT_ID, QualifiedName.ROOT, State.Open, Constants.EMPTY_METADATA);
		service().copyWorkspace(ROOT_ID, b.getName(), ROOT_ID, wsName, false);
	}
	
	@Test
	public void testSearchForWorkspaceByWildcard() throws BaseException {
		QualifiedName base = QualifiedName.of(randomUrlSafeName());
		QualifiedName jones = base.add("jones");
		QualifiedName carter = base.add("carter");
		QualifiedName peter_jones = jones.add("peter");
		QualifiedName peter_carter = carter.add("peter");
		QualifiedName pamela_jones = jones.add("pamela");
		QualifiedName roger_carter = carter.add("roger");
		service().createWorkspaceByName(ROOT_ID, peter_jones, State.Open, null, Options.CREATE_MISSING_PARENT);
		service().createWorkspaceByName(ROOT_ID, peter_carter, State.Open, null, Options.CREATE_MISSING_PARENT);
		service().createWorkspaceByName(ROOT_ID, pamela_jones, State.Open, null, Options.CREATE_MISSING_PARENT);
		service().createWorkspaceByName(ROOT_ID, roger_carter, State.Open, null, Options.CREATE_MISSING_PARENT);
				
		assertEquals(4, service().catalogueByName(ROOT_ID, base.addAll("*","*"), Query.UNBOUNDED, false).count());
		assertEquals(2, service().catalogueByName(ROOT_ID,base.addAll("jones","*"), Query.UNBOUNDED, false).count());
		assertEquals(2, service().catalogueByName(ROOT_ID,base.addAll("carter","*"), Query.UNBOUNDED, false).count());
		assertEquals(3, service().catalogueByName(ROOT_ID,base.addAll("*","p*"), Query.UNBOUNDED, false).count());
		assertEquals(3, service().catalogueByName(ROOT_ID,base.addAll("*","*r"), Query.UNBOUNDED, false).count());
	}
	
	@Test
	public void testWorkspaceMetadataRoundtrip() throws BaseException {
		QualifiedName base = QualifiedName.of(randomUrlSafeName());
		JsonObject testMetadata = Json.createObjectBuilder().add("Branch", "slartibartfast").build();
		service().createWorkspaceByName(ROOT_ID, base, State.Open, testMetadata, Options.CREATE_MISSING_PARENT);
		Workspace fetched =  (Workspace)service().getObjectByName(ROOT_ID,base);
		assertEquals("slartibartfast", fetched.getMetadata().getString("Branch"));
	}
	
	@Test
	public void testCatalogueWorkspaceWithMixedEntyTypes() throws BaseException {
		QualifiedName base = QualifiedName.of(randomUrlSafeName());
		QualifiedName jones = base.add("jones");
		QualifiedName carter = base.add("carter");
		service().createWorkspaceByName(ROOT_ID, base, State.Open, null, Options.CREATE_MISSING_PARENT);
		service().createWorkspaceByName(ROOT_ID, jones, State.Open, null, Options.CREATE_MISSING_PARENT);
		service().createDocumentLink(null, carter, "text/plain", ()->toStream(randomText()), null);
		assertEquals(2,service().catalogueByName(ROOT_ID, base.add("*"), Query.UNBOUNDED, false).count());
	}
	
	@Test
	public void testWorkspaceMetadataMerge() throws BaseException {
		QualifiedName base = QualifiedName.of(randomUrlSafeName());
		JsonObject testMetadata1 = Json.createObjectBuilder().add("Branch", "slartibartfast").build();
		JsonObject testMetadata2 = Json.createObjectBuilder().add("Team", "alcatraz").build();
		service().createWorkspaceByName(ROOT_ID, base, State.Open, testMetadata1, Options.CREATE_MISSING_PARENT);
		service().updateWorkspaceByName(ROOT_ID, base, null, testMetadata2);
		Workspace fetched = (Workspace) service().getObjectByName(ROOT_ID,base);
		assertEquals("slartibartfast", fetched.getMetadata().getString("Branch"));
		assertEquals("alcatraz", fetched.getMetadata().getString("Team"));
		JsonObject testMetadata3 = Json.createObjectBuilder().add("Branch", JsonValue.NULL).build();
		service().updateWorkspaceByName(ROOT_ID,base, null, testMetadata3);
		Workspace fetched2 = (Workspace)service().getObjectByName(ROOT_ID,base);
		assertEquals(null, fetched2.getMetadata().getString("Branch",null));
		assertEquals("alcatraz", fetched2.getMetadata().getString("Team"));
	}
	
	@Test
	public void testRepositoryCatalog() throws IOException, BaseException {
		long count1 = service().catalogue(Query.UNBOUNDED, false).count();
		Reference ref1 = service().createDocument("text/plain", ()->toStream(randomText()), EMPTY_METADATA);
		service().createDocument("text/plain", ()->toStream(randomText()), EMPTY_METADATA);
		service().createDocument("text/plain", ()->toStream(randomText()), EMPTY_METADATA);
		service().updateDocument(ref1.id, null, ()->toStream(randomText()), EMPTY_METADATA);
		long count2 = service().catalogue(Query.UNBOUNDED, false).count();
		assertEquals(3, count2 - count1);
	}

	@Test
	public void testWorkspaceNameRoundtrip() throws BaseException {
		QualifiedName name = QualifiedName.of(randomUrlSafeName());
		Workspace workspace0 = service().createWorkspaceByName(ROOT_ID, name, State.Open, EMPTY_METADATA, Options.CREATE_MISSING_PARENT);
		Workspace workspace = service().getWorkspaceByName(workspace0.getId(), null);
		assertEquals(name, workspace0.getName());
		assertEquals(name, workspace.getName());
	}
	
	@Test
	public void testCreateAndFindWorkspaceWithPath() throws BaseException {
		QualifiedName name = randomQualifiedName();
		Workspace workspace0 = service().createWorkspaceByName(ROOT_ID, name, State.Open, EMPTY_METADATA, Options.CREATE_MISSING_PARENT);
		Workspace workspace = (Workspace)service().getObjectByName(ROOT_ID, name);
		assertEquals(workspace0.getId(), workspace.getId());
	}

	@Test
	public void testGetObjectById() throws BaseException {
		Workspace workspace0 = service().createWorkspaceAndName(ROOT_ID, QualifiedName.ROOT, State.Open, EMPTY_METADATA, Options.CREATE_MISSING_PARENT);
		RepositoryObject ro = service().getObjectByName(workspace0.getId(), QualifiedName.ROOT);
		assertEquals(RepositoryObject.Type.WORKSPACE, ro.getType());
	}
	
	@Test
	public void testGeneratedWorkspaceName() throws BaseException {
		Workspace workspace0 = service().createWorkspaceAndName(ROOT_ID, QualifiedName.ROOT, State.Open, EMPTY_METADATA, Options.CREATE_MISSING_PARENT);
		Workspace workspace1 = service().createWorkspaceAndName(workspace0, State.Open, EMPTY_METADATA);
		assertEquals(1, service().refresh(workspace0).getName().size());
		assertEquals(2, service().refresh(workspace1).getName().size());
	}

	@Test
	public void testGeneratedDocumentName() throws InvalidWorkspace, InvalidWorkspaceState, InvalidObjectName, InvalidReference {
        QualifiedName name1 = randomQualifiedName();
        service().createWorkspaceByName(ROOT_ID, name1, State.Open, EMPTY_METADATA, Options.CREATE_MISSING_PARENT);
        String originalText = randomText();
        Reference ref1 = service().createDocument("text/plain", ()->toStream(originalText), EMPTY_METADATA);
        DocumentLink link = service().createDocumentLinkAndName(ROOT_ID, name1, ref1, Options.RETURN_EXISTING_LINK_TO_SAME_DOCUMENT, Options.CREATE_MISSING_PARENT);
	    assertEquals(ref1, link.getReference());
        assertThat(link.getName().toString(), startsWith(name1.toString()));
        assertThat(link.getName().size(), greaterThan(name1.size()));
        assertThat(link.getName().part, not(isEmptyString()));
	}
    
    @Test
	public void testUpdateWorkspaceReturnsSameId() throws BaseException {
		JsonObject DUMMY_METADATA = Json.createObjectBuilder()
				.add("Branch", "XYZABC")
				.add("Team", "TEAM1")
				.build();
		Workspace workspace = service().createWorkspaceAndName(Constants.ROOT_ID, QualifiedName.ROOT, State.Open, EMPTY_METADATA);
		Workspace result = service().updateWorkspace(workspace, null, DUMMY_METADATA);
		assertEquals(workspace.getId(), result.getId());
        assertEquals(DUMMY_METADATA, result.getMetadata());
	}
	
	@Test
	public void testCreateDocumentLink() throws BaseException {
        QualifiedName name1 = randomQualifiedName();
        service().createWorkspaceByName(ROOT_ID, name1, State.Open, EMPTY_METADATA, Options.CREATE_MISSING_PARENT);
        String originalText = randomText();
        Reference ref1 = service().createDocument("text/plain", ()->toStream(originalText), EMPTY_METADATA);
        QualifiedName docName = name1.add(randomUrlSafeName());
        service().createDocumentLink(ROOT_ID, docName, ref1, Options.CREATE_MISSING_PARENT);
	    Document doc1 = (Document)service().getObjectByName(ROOT_ID, docName);
	    assertEquals(ref1, doc1.getReference());
	}
    
    @Test
	public void testCreateDocumentLinkGeneratedName() throws BaseException {
        Workspace workspace = service().createWorkspaceByName(ROOT_ID, randomQualifiedName(), State.Open, EMPTY_METADATA, Options.CREATE_MISSING_PARENT);
        String originalText = randomText();
        Reference ref1 = service().createDocument("text/plain", ()->toStream(originalText), EMPTY_METADATA);
        DocumentLink newLink = service().createDocumentLinkAndName(workspace, ref1, Options.RETURN_EXISTING_LINK_TO_SAME_DOCUMENT);
	    Document doc1 = (Document)service().getObjectByName(ROOT_ID, newLink.getName());
	    assertEquals(ref1, doc1.getReference());
	}
    
    @Test
	public void testCreateDocumentLinkGeneratedNameSequence() throws BaseException {
        Workspace workspace = service().createWorkspaceByName(ROOT_ID, randomQualifiedName(), State.Open, EMPTY_METADATA, Options.CREATE_MISSING_PARENT);
        String originalText = randomText();
        Reference ref1 = service().createDocument("text/plain", ()->toStream(originalText), EMPTY_METADATA);
        String originalText2 = randomText();
        Reference ref2 = service().createDocument("text/plain", ()->toStream(originalText2), EMPTY_METADATA);
        DocumentLink link1 = service().createDocumentLinkAndName(workspace, ref1, Options.RETURN_EXISTING_LINK_TO_SAME_DOCUMENT);
        DocumentLink link2 = service().createDocumentLinkAndName(workspace, ref2, Options.RETURN_EXISTING_LINK_TO_SAME_DOCUMENT);
	    assertNotEquals(link1.getName(), link2.getName());
	}

    @Test
	public void testCreateDocumentLinkGeneratedNameSameObject() throws BaseException {
        QualifiedName name1 = randomQualifiedName();
        Workspace workspace = service().createWorkspaceByName(ROOT_ID, randomQualifiedName(), State.Open, EMPTY_METADATA, Options.CREATE_MISSING_PARENT);
        String originalText = randomText();
        Reference ref1 = service().createDocument("text/plain", ()->toStream(originalText), EMPTY_METADATA);
        DocumentLink link1 = service().createDocumentLinkAndName(workspace, ref1, Options.RETURN_EXISTING_LINK_TO_SAME_DOCUMENT);
        DocumentLink link2 = service().createDocumentLinkAndName(workspace, ref1, Options.RETURN_EXISTING_LINK_TO_SAME_DOCUMENT);
	    assertEquals(link1.getName(), link2.getName());
	}
    
    @Test(expected = InvalidReference.class)
	public void testCreateDocumentLinkGeneratedNameSameObjectError() throws BaseException {
        Workspace workspace = service().createWorkspaceByName(ROOT_ID, randomQualifiedName(), State.Open, EMPTY_METADATA, Options.CREATE_MISSING_PARENT);
        String originalText = randomText();
        Reference ref1 = service().createDocument("text/plain", ()->toStream(originalText), EMPTY_METADATA);
        DocumentLink link1 = service().createDocumentLinkAndName(workspace, ref1, Options.RETURN_EXISTING_LINK_TO_SAME_DOCUMENT);
        DocumentLink link2 = service().createDocumentLinkAndName(workspace, ref1); // no option to return existing - so should be an error
	    assertEquals(link1.getName(), link2.getName());
	}
    
    @Test
	public void testUpdateDocumentLink() throws BaseException {
        Workspace workspace = service().createWorkspaceByName(ROOT_ID, randomQualifiedName(), State.Open, EMPTY_METADATA, Options.CREATE_MISSING_PARENT);
        String originalText = randomText();
        Reference ref1 = service().createDocument("text/plain", ()->toStream(originalText), EMPTY_METADATA);
        Reference ref2 = service().createDocument("text/plain", ()->toStream(originalText), EMPTY_METADATA);
        DocumentLink link1 = service().createDocumentLink(workspace, randomUrlSafeName(), ref1);
	    Document doc1 = service().refresh(link1);
	    assertEquals(ref1, doc1.getReference());
        service().updateDocumentLink(link1, ref2);
	    Document doc2 = (Document)service().refresh(link1);
	    assertEquals(ref2, doc2.getReference());
	}
    
    @Test
	public void testUpdateDocumentLink2() throws BaseException {
        Workspace workspace = service().createWorkspaceByName(ROOT_ID, randomQualifiedName(), State.Open, EMPTY_METADATA, Options.CREATE_MISSING_PARENT);
        String originalText = randomText();
        Reference ref1 = service().createDocument("text/plain", ()->toStream(originalText), EMPTY_METADATA);
        DocumentLink link = service().createDocumentLink(workspace, randomUrlSafeName(), ref1);
	    Document doc1 = service().refresh(link);
	    assertEquals(ref1, doc1.getReference());
        JsonObject testMetadata = randomDocumentMetadata();
        service().updateDocumentLink(link, null, null, testMetadata);
	    Document doc2 = (Document)service().refresh(link);
	    assertEquals(testMetadata, doc2.getMetadata());
	}
    
    @Test
	public void testUpdateDocumentLinkWithCreateFolder() throws BaseException, IOException {

        JsonObject metadata = randomDocumentMetadata();
        String content = randomText();
        DocumentLink link = service().updateDocumentLink(ROOT_ID, randomQualifiedName(), "text/plain", ()->toStream(content), metadata, Options.CREATE_MISSING_ITEM, Options.CREATE_MISSING_PARENT);
	    Document doc2 = (Document)service().refresh(link);
	    assertEquals(content, getDocText(doc2));
	    assertEquals(metadata, doc2.getMetadata());
	}
    
        
    /* broadly speaking, when we have a folder id and name, test that we get the same result when reading/updating
    * the document either using the full path or the relative path from the folder id.
    */
    @Test
	public void testEquivalenceOfNameAndId() throws BaseException {
        Workspace workspace = service().createWorkspaceByName(ROOT_ID, randomQualifiedName(), State.Open, EMPTY_METADATA, Options.CREATE_MISSING_PARENT);
        String originalText = randomText();
        Reference ref1 = service().createDocument("text/plain", ()->toStream(originalText), EMPTY_METADATA);
        DocumentLink link1 = service().createDocumentLink(workspace, randomUrlSafeName(), ref1);
	    Document doc1 = service().getDocumentLink(workspace, link1.getName().part);
	    Document doc2 = service().getDocumentLink(Constants.ROOT_ID, link1.getName());
	    assertEquals(doc1.getReference(), doc2.getReference());
        JsonObject metadata1 = randomDocumentMetadata();
        JsonObject metadata2 = randomDocumentMetadata();
        service().updateDocumentLink(Constants.ROOT_ID, link1.getName(), null, null, metadata1, Options.CREATE_MISSING_ITEM, Options.CREATE_MISSING_PARENT);
	    Document doc3 = service().refresh(link1);
	    assertEquals(metadata1, doc3.getMetadata());
        service().updateDocumentLink(workspace, link1.getName().part, null, null, metadata2, Options.CREATE_MISSING_ITEM);
	    Document doc4 = service().refresh(link1);
	    assertEquals(metadata2, doc4.getMetadata());
	}
    
    @Test
	public void testUpdateDocumentLinkInCreateMode() throws BaseException {
        Workspace workspace = service().createWorkspaceByName(ROOT_ID, randomQualifiedName(), State.Open, EMPTY_METADATA, Options.CREATE_MISSING_PARENT);
        String originalText = randomText();
        Reference ref1 = service().createDocument("text/plain", ()->toStream(originalText), EMPTY_METADATA);
        Reference ref2 = service().createDocument("text/plain", ()->toStream(originalText), EMPTY_METADATA);
        DocumentLink link = service().updateDocumentLink(workspace, randomUrlSafeName(), ref1, Options.CREATE_MISSING_ITEM);
	    Document doc1 = service().refresh(link);
	    assertEquals(ref1, doc1.getReference());
    }
	
	@Test 
	public void testSearchByDocumentId() throws BaseException {
	    Workspace workspace1 = service().createWorkspaceByName(ROOT_ID, randomQualifiedName(), State.Open, EMPTY_METADATA, Options.CREATE_MISSING_PARENT);
	    Workspace workspace2 = service().createWorkspaceByName(ROOT_ID, randomQualifiedName(), State.Open, EMPTY_METADATA, Options.CREATE_MISSING_PARENT);
	    String originalText = randomText();
	    Reference ref1 = service().createDocument("text/plain", ()->toStream(originalText), EMPTY_METADATA);
	    service().createDocumentLink(workspace1, "one", ref1);
	    service().createDocumentLink(workspace2, "two", ref1);
	    Stream<DocumentLink> result = service().listWorkspaces(ref1.id, null, Query.UNBOUNDED);
	    assertEquals(2, result.count());
	}
    
    @Test
	public void testRepositorySearchByMediaType() throws IOException, BaseException {
        Workspace workspace1 = service().createWorkspaceByName(ROOT_ID, randomQualifiedName(), State.Open, EMPTY_METADATA, Options.CREATE_MISSING_PARENT);
        String originalText = randomText();
        Reference ref1 = service().createDocument("text/plain", ()->toStream(originalText), EMPTY_METADATA);
        Reference ref2 = service().createDocument("application/octet-stream", ()->toStream(originalText), EMPTY_METADATA);
        service().createDocumentLink(workspace1, randomUrlSafeName(), ref1);
        service().createDocumentLink(workspace1, randomUrlSafeName(), ref2);
		RepositoryObject[] result = service().catalogueByName(ROOT_ID, workspace1.getName().add("*"), Query.fromJson("{ 'mediaType': 'text/plain'}"), false).toArray(RepositoryObject[]::new);
		assertEquals(1, result.length);
		assertEquals(((Document)result[0]).getReference(), ref1);
	}
    
    @Test
	public void testRepositorySearchByFolderState() throws IOException, BaseException {
        Workspace workspace1 = service().createWorkspaceByName(ROOT_ID, randomQualifiedName(), State.Open, EMPTY_METADATA, Options.CREATE_MISSING_PARENT);
        
        Workspace workspace2 = service().createWorkspaceByName(workspace1, randomUrlSafeName(), State.Closed, EMPTY_METADATA);
        Workspace workspace3 = service().createWorkspaceByName(workspace1, randomUrlSafeName(), State.Open, EMPTY_METADATA);
		RepositoryObject[] resultAll = service().catalogueByName(workspace1, QualifiedName.of("*"), Query.UNBOUNDED, false).toArray(RepositoryObject[]::new);
		RepositoryObject[] result = service().catalogueByName(workspace1, QualifiedName.of("*"), Query.fromJson("{ 'state': 'Closed'}"), false).toArray(RepositoryObject[]::new);
		assertEquals(2, resultAll.length);
		assertEquals(1, result.length);
		assertEquals(workspace2.getName(), ((Workspace)result[0]).getName());
	}
    
    @Test
	public void testRepositorySearchByParentFolderStateAndMediaType() throws IOException, BaseException {
        QualifiedName name0 = randomQualifiedName();
        Workspace workspace1 = service().createWorkspaceByName(ROOT_ID, randomQualifiedName(), State.Open, EMPTY_METADATA, Options.CREATE_MISSING_PARENT);

        QualifiedName name1 = QualifiedName.ROOT.add(randomUrlSafeName());
        QualifiedName name2 = QualifiedName.ROOT.add(randomUrlSafeName());
        String originalText = randomText();
        Reference ref1 = service().createDocument("text/plain", ()->toStream(originalText), EMPTY_METADATA);
        Reference ref2 = service().createDocument("application/octet-stream", ()->toStream(originalText), EMPTY_METADATA);
        QualifiedName W1doc1Name = name1.add(randomUrlSafeName());
        QualifiedName W1doc2Name = name1.add(randomUrlSafeName());
        QualifiedName W2doc1Name = name2.add(randomUrlSafeName());
        QualifiedName W2doc2Name = name2.add(randomUrlSafeName());
        service().createDocumentLink(workspace1.getId(), W1doc1Name, ref1, Options.CREATE_MISSING_PARENT);
        service().createDocumentLink(workspace1.getId(), W1doc2Name, ref2, Options.CREATE_MISSING_PARENT);
        service().createDocumentLink(workspace1.getId(), W2doc1Name, ref1, Options.CREATE_MISSING_PARENT);
        service().createDocumentLink(workspace1.getId(), W2doc2Name, ref2, Options.CREATE_MISSING_PARENT);
        // Close worspace 2
        service().updateWorkspaceByName(workspace1.getId(), name2, State.Closed, EMPTY_METADATA);
		JsonObject[] resultAll = service().catalogueByName(workspace1, QualifiedName.of("*", "*"), Query.UNBOUNDED, false).map(item->item.toJson()).toArray(JsonObject[]::new);
		RepositoryObject[] resultClosed = service().catalogueByName(workspace1, QualifiedName.of("*", "*"), Query.fromJson("{ 'parent': { 'state': 'Closed'} }"), false).toArray(RepositoryObject[]::new);
		RepositoryObject[] resultText = service().catalogueByName(workspace1, QualifiedName.of("*", "*"), Query.fromJson("{ 'mediaType': 'text/plain'}"), false).toArray(RepositoryObject[]::new);
		JsonObject[] resultClosedAndText = service().catalogueByName(workspace1, QualifiedName.of("*", "*"), Query.fromJson("{ 'mediaType': 'text/plain', 'parent': { 'state': 'Closed'}}"), false).map(item->item.toJson()).toArray(JsonObject[]::new);
		assertEquals(4, resultAll.length);
		assertEquals(2, resultClosed.length);
		assertEquals(2, resultText.length);
		assertEquals(1, resultClosedAndText.length);
	}
    
    private long countMatchingMetadata(List<NamedRepositoryObject> items, Predicate<JsonObject> match) {
        return items.stream().map(item->item.getMetadata()).filter(match).count();
    }
        
    @Test
	public void testRepositorySearchContent() throws IOException, BaseException {
        QualifiedName name0 = randomQualifiedName();
        String baseId = service().createWorkspaceByName(ROOT_ID, name0, State.Open, EMPTY_METADATA, Options.CREATE_MISSING_PARENT).getId();

        QualifiedName name1 = QualifiedName.ROOT.add(randomUrlSafeName());
        QualifiedName name2 = QualifiedName.ROOT.add(randomUrlSafeName());
        JsonObject metadata1 = randomDocumentMetadata();
        JsonObject metadata2 = randomDocumentMetadata();
        JsonObject metadataSearch2 = Json.createObjectBuilder().add("metadata", Json.createObjectBuilder(metadata2)).build();
        String originalText = randomText();
        Reference ref1 = service().createDocument("text/plain", ()->toStream(originalText), metadata1);
        Reference ref2 = service().createDocument("application/octet-stream", ()->toStream(originalText), metadata2);
        QualifiedName W1doc1Name = name1.add(randomUrlSafeName());
        QualifiedName W1doc2Name = name1.add(randomUrlSafeName());
        QualifiedName W2doc1Name = name2.add(randomUrlSafeName());
        QualifiedName W2doc2Name = name2.add(randomUrlSafeName());
        service().createDocumentLink(baseId, W1doc1Name, ref1, Options.CREATE_MISSING_PARENT);
        service().createDocumentLink(baseId, W1doc2Name, ref2, Options.CREATE_MISSING_PARENT);
        service().createDocumentLink(baseId, W2doc1Name, ref1, Options.CREATE_MISSING_PARENT);
        service().createDocumentLink(baseId, W2doc2Name, ref2, Options.CREATE_MISSING_PARENT);
        service().updateWorkspaceByName(baseId, name2, State.Closed, null);
		List<NamedRepositoryObject> resultAll = service().catalogueByName(baseId, QualifiedName.of("*", "*"), Query.from(metadataSearch2), false).collect(Collectors.toList());
        
        assertEquals(2, resultAll.size());
	}

    @Test
	public void testGetDocumentLink() throws BaseException, IOException {
        QualifiedName name0 = randomQualifiedName();
        String baseId = service().createWorkspaceByName(ROOT_ID, name0, State.Open, EMPTY_METADATA, Options.CREATE_MISSING_PARENT).getId();

        // Create a random document
        String originalText = randomText();        
        Reference ref1 = service().createDocument("text/plain", ()->toStream(originalText), JsonUtil.parseObject("{'Description':'Text Document'}"));

        // Add document to a random folder
        QualifiedName name1 = QualifiedName.ROOT.add(randomUrlSafeName());
        QualifiedName W1doc1Name = name1.add(randomUrlSafeName());
        service().createDocumentLink(baseId, W1doc1Name, ref1, Options.CREATE_MISSING_PARENT);
        
        // Get the document back again
        DocumentLink link = service().getDocumentLink(baseId, name1, ref1.id);        
        assertEquals(originalText, getDocText(link));

        // Get the document back again using implicit root
        DocumentLink link2 = service().getDocumentLink(null, name0.addAll(name1), ref1.id);
        assertEquals(originalText, getDocText(link2));
    }
    

    
    @Test
    public void testJsonRepresentationDocumentPart() throws IOException, DocumentFormatException, PartNotFoundException {
        DocumentImpl zipDoc = new DocumentImpl(new Reference("test"), MediaTypes.ZIP.toString(), ()->BaseRepositoryServiceTest.class.getResourceAsStream("/testzipdir.zip"), EMPTY_METADATA);
        DocumentPart testDocx = navigator().getPartByName(zipDoc, QualifiedName.of("test", "subdir", "testdoc.docx"));
        JsonObject json = testDocx.toJson(service(), navigator(), 0, 0);
        assertEquals("test", json.getJsonObject("document").getString("id"));
        assertEquals(MediaTypes.MICROSOFT_WORD_XML.toString(), json.getString("mediaType"));
        assertEquals(false, json.getBoolean("navigable"));
	}

    @Test
	public void testRepositorySearch() throws IOException, InvalidWorkspace {
        
        ArrayList<byte[]> dataValues = new ArrayList<>();
        ArrayList<JsonObject> metadataValues = new ArrayList<>();
        
        generateDocs(4, this::randomDocumentMetadata, (data, metadata, type) -> {
            dataValues.add(data);
            metadataValues.add(metadata);
            try {
                service().createDocument(type, ()->new ByteArrayInputStream(data), metadata);
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
	public void testWorkspaceUpdate() throws BaseException {
		String workspace_id = randomWorkspaceId();
        String originalText = randomText();
        Reference ref1 = service().createDocument("text/plain", ()->toStream(originalText), EMPTY_METADATA);
		service().createDocumentLinkAndName(workspace_id, null, ref1, Options.RETURN_EXISTING_LINK_TO_SAME_DOCUMENT, Options.CREATE_MISSING_PARENT);
		assertEquals(service().catalogueById(workspace_id, null, false).count(), 1);
		service().deleteDocument(workspace_id, QualifiedName.ROOT, ref1.id);
		assertEquals(service().catalogueById(workspace_id, null, false).count(), 0);
	}
	
	@Test 
	public void testListWorkspaces() throws BaseException {
		String workspace1 = randomWorkspaceId();
		String workspace2 = randomWorkspaceId();
        Reference ref1 = service().createDocument("text/plain", ()->toStream(randomText()), EMPTY_METADATA);
        Reference ref2 = service().createDocument("text/plain", ()->toStream(randomText()), EMPTY_METADATA);
        Reference ref3 = service().createDocument("text/plain", ()->toStream(randomText()), EMPTY_METADATA);
		service().createDocumentLinkAndName(workspace1, null, ref1, Options.RETURN_EXISTING_LINK_TO_SAME_DOCUMENT, Options.CREATE_MISSING_PARENT);
		service().createDocumentLinkAndName(workspace2, null, ref1, Options.RETURN_EXISTING_LINK_TO_SAME_DOCUMENT, Options.CREATE_MISSING_PARENT);
		service().createDocumentLinkAndName(workspace2, null, ref2, Options.RETURN_EXISTING_LINK_TO_SAME_DOCUMENT, Options.CREATE_MISSING_PARENT);
   		service().createDocumentLinkAndName(workspace2, null, ref3, Options.RETURN_EXISTING_LINK_TO_SAME_DOCUMENT, Options.CREATE_MISSING_PARENT);
		assertEquals(2, service().listWorkspaces(ref1.id, null, Query.UNBOUNDED).count());
		assertEquals(1, service().listWorkspaces(ref2.id, null, Query.UNBOUNDED).count());
		assertEquals(1, service().listWorkspaces(ref3.id, null, Query.UNBOUNDED).count());
	}
    
	@Test
	public void testSaveWorkspaceMetadata() throws BaseException {
        QualifiedName name0 = randomQualifiedName();
        String baseId = service().createWorkspaceByName(ROOT_ID, name0, State.Open, parseObject("{ 'Description': 'test metadata' }"), Options.CREATE_MISSING_PARENT).getId();
        Workspace workspace = service().getWorkspaceByName(baseId, null);
        assertEquals("test metadata", workspace.getMetadata().getString("Description"));
	}    
    
    //////------- Versioning tests --------//////
  
    /** 
     *  Important - what should be returned when we have multiple versions of a document matching criteria.Definiton 
     * is that we return the latest version which matches the criteria.
	 * 
	 *
     * @throws BaseException
	 */
	@Test 
	public void testRepositoryCatalogWithVersions() throws BaseException {
		long count1 = service().catalogue(Query.UNBOUNDED, true).count();
		Reference ref1 = service().createDocument("text/plain", ()->toStream(randomText()), EMPTY_METADATA);
		service().createDocument("text/plain", ()->toStream(randomText()), EMPTY_METADATA);
		service().createDocument("text/plain", ()->toStream(randomText()), EMPTY_METADATA);
		service().updateDocument(ref1.id, null, ()->toStream(randomText()), EMPTY_METADATA);
		long count2 = service().catalogue(Query.UNBOUNDED, true).count();
		assertEquals(3, count2 - count1);
	}
    
	@Test
	public void testGetDocumentWithWorkspaceId() throws IOException, BaseException {
		String wsId = service().createWorkspaceAndName(Constants.ROOT_ID, QualifiedName.ROOT, State.Open, EMPTY_METADATA, Options.CREATE_MISSING_PARENT).getId();
		String originalText = randomText();
		// Create a document in the workspace
		DocumentLink link1 = service().createDocumentLink(wsId, QualifiedName.of(randomUrlSafeName()), "text/plain", ()->toStream(originalText), EMPTY_METADATA);
		// now close the workspace
		service().updateWorkspaceByName(wsId, null, State.Closed, EMPTY_METADATA);
		Reference ref2 = service().updateDocument(link1.getId(), null, ()->toStream(randomText()), EMPTY_METADATA);
		assertEquals(link1.getId(), ref2.id);
		assertNotEquals(link1.getVersion(), ref2.version);
		String doc1 = getDocText(service().getDocument(link1.getReference()));
		String doc2 = getDocText(service().getDocument(ref2));
		assertNotEquals(doc1, doc2);	
		String wsDoc = getDocText(service().getDocumentLink(wsId, QualifiedName.ROOT, link1.getId()));
		assertEquals(originalText, wsDoc); 
	}
    
    @Test
    public void testGetDocumentVersions() throws BaseException {
		Reference ref1 = service().createDocument("text/plain", ()->toStream(randomText()), EMPTY_METADATA);
		Reference ref2 = service().updateDocument(ref1.id, "text/plain", ()->toStream(randomText()), EMPTY_METADATA);
 		Reference ref3 = service().updateDocument(ref1.id, "text/plain", ()->toStream(randomText()), EMPTY_METADATA);
      
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
    public void testGetVersionedDocumentFromWorkspace() throws BaseException, IOException {
        // Create  a document
        String textv1 = randomText();
        Reference ref1 = service().createDocument("text/plain", ()->toStream(textv1), EMPTY_METADATA);
        // Create a workspace
        QualifiedName workspaceName = randomQualifiedName();
        service().createWorkspaceByName(ROOT_ID, workspaceName, State.Open, EMPTY_METADATA, Options.CREATE_MISSING_PARENT);
        // Link document to workspace
        service().createDocumentLinkAndName(ROOT_ID, workspaceName, ref1, Options.RETURN_EXISTING_LINK_TO_SAME_DOCUMENT, Options.CREATE_MISSING_PARENT);
        // Update document
        String textv2 = randomText();
		Reference ref2 = service().updateDocument(ref1.id, "text/plain", ()->toStream(textv2), null);
        // Close workspace
        service().updateWorkspaceByName(ROOT_ID, workspaceName, State.Closed, null);
        // Update document again
        String textv3 = randomText();
 		Reference ref3 = service().updateDocument(ref1.id, "text/plain", ()->toStream(textv3), null);
        // Now retrieve document from workspace
        DocumentLink doc = service().getDocumentLink(ROOT_ID, workspaceName, ref1.id);
        // We should see the version of the document that was current when the workspace was closed
        assertEquals(ref2, doc.getReference());
        // Double check
        assertEquals(textv2, getDocText(doc));
    }
    
    @Test(expected = InvalidWorkspaceState.class)
    public void testWorkspaceUpdateDoesntWorkIfWorkspaceClosed() throws BaseException {
        // Create  a document
        String textv1 = randomText();
        Reference ref1 = service().createDocument("text/plain", ()->toStream(textv1), EMPTY_METADATA);
        // Create a workspace
        QualifiedName workspaceName = randomQualifiedName();
        service().createWorkspaceByName(ROOT_ID, workspaceName, State.Open, EMPTY_METADATA, Options.CREATE_MISSING_PARENT);
        // Link document to workspace
        QualifiedName documentName = workspaceName.add(randomUrlSafeName());
        service().createDocumentLink(ROOT_ID, documentName, ref1, Options.CREATE_MISSING_PARENT);
        // Close workspace
        service().updateWorkspaceByName(ROOT_ID, workspaceName, State.Closed, null);
        // Update document again
        String textv2 = randomText();
 		DocumentLink ref3 = service().updateDocumentLink(ROOT_ID, documentName, "*/*", ()->toStream(textv2), EMPTY_METADATA, Options.CREATE_MISSING_PARENT, Options.CREATE_MISSING_ITEM);
    }
    
    //////------- End Versioning tests --------//////
}
