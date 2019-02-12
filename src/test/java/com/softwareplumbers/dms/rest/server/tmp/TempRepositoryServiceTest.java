package com.softwareplumbers.dms.rest.server.tmp;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.UUID;

import org.junit.Test;

import com.softwareplumbers.common.abstractquery.Cube;
import com.softwareplumbers.dms.rest.server.model.Document;
import com.softwareplumbers.dms.rest.server.model.Info;
import com.softwareplumbers.dms.rest.server.model.Reference;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidDocumentId;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidReference;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspaceId;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspaceState;
import com.softwareplumbers.dms.rest.server.model.Workspace;
import com.softwareplumbers.dms.rest.server.model.Workspace.State;
import com.softwareplumbers.dms.rest.server.test.TestRepository;

public class TempRepositoryServiceTest {
	
	public TestRepository getTestRepository() {
		return new TestRepository(new TempRepositoryService());
	}
	
	@Test
	public void testRepositoryRoundtrip() throws IOException {
		TestRepository repository = getTestRepository();
		assertTrue(TestRepository.docEquals("test1", repository.doc1));
		assertTrue(TestRepository.docEquals("test2", repository.doc2));
		assertTrue(TestRepository.docEquals("test3", repository.doc3));
	}
	
	@Test(expected = InvalidReference.class)
	public void testRepositoryFetchWithInvalidRef() throws IOException, InvalidReference {
		TestRepository repository = getTestRepository();
		Reference ref1 = new Reference("xxx");
		Document document = repository.service.getDocument(ref1);
	}
	
	@Test(expected = InvalidReference.class)
	public void testRepositoryFetchWithInvalidVersion() throws IOException, InvalidReference {
		TestRepository repository = getTestRepository();
		Reference ref1 = new Reference(repository.ref1.id, "777");
		Document document = repository.service.getDocument(ref1);
	}
	
	@Test
	public void testRepositoryFetchWithNoVersion() throws IOException, InvalidReference {
		TestRepository repository = getTestRepository();
		Reference ref1 = new Reference(repository.ref1.id);
		assertTrue(TestRepository.docEquals("test1", repository.service.getDocument(ref1)));
		Reference ref2 = new Reference(repository.ref2.id);
		assertTrue(TestRepository.docEquals("test2", repository.service.getDocument(ref2)));
		Reference ref3 = new Reference(repository.ref3.id);
		assertTrue(TestRepository.docEquals("test3", repository.service.getDocument(ref3)));
	}
	
	@Test
	public void testRepositoryCatalog() throws IOException, InvalidWorkspaceId {
		TestRepository repository = getTestRepository();
		Info[] result = repository.service.catalogue(Cube.UNBOUNDED, false).toArray(Info[]::new);
		assertEquals(result.length, 3);
	}
	
	@Test
	public void testRepositoryCatalogWithVersions() throws IOException, InvalidDocumentId, InvalidWorkspaceId, InvalidWorkspaceState {
		TestRepository repository = getTestRepository();
		Reference ref4 = repository.service.updateDocument(repository.ref2.id, repository.doc3.getMediaType(), null, repository.doc3.getMetadata(), null, false );
		assertEquals("0000002", ref4.version);
		Info[] result = repository.service.catalogue(Cube.UNBOUNDED, false).toArray(Info[]::new);
		assertEquals(result.length, 3);
	}
	
	@Test
	public void testRepositorySearch() throws IOException, InvalidWorkspaceId {
		TestRepository repository = getTestRepository();
		Info[] result = repository.service.catalogue(Cube.fromJson("{ 'filename': 'partiphuckborlz'}"), false).toArray(Info[]::new);
		assertEquals(result.length, 1);
		assertEquals(result[0].reference, repository.ref2);
	}
	
	
	@Test 
	public void testWorkspaceUpdate() throws InvalidDocumentId, InvalidWorkspaceId, InvalidWorkspaceState {
		TestRepository repository = getTestRepository();
		String workspace_id = UUID.randomUUID().toString();
		repository.service.updateDocument(repository.ref1.id, null, null, null, workspace_id, true);
		assertEquals(repository.service.catalogueById(workspace_id, null, false).count(), 1);
		repository.service.deleteDocument(workspace_id, repository.ref1.id);
		assertEquals(repository.service.catalogueById(workspace_id, null, false).count(), 0);
	}
	
	@Test 
	public void testListWorkspaces() throws InvalidDocumentId, InvalidWorkspaceId, InvalidWorkspaceState {
		TestRepository repository = getTestRepository();
		String workspace1 = UUID.randomUUID().toString();
		String workspace2 = UUID.randomUUID().toString();
		repository.service.updateDocument(repository.ref1.id, null, null, null, workspace1, true);
		repository.service.updateDocument(repository.ref1.id, null, null, null, workspace2, true);
		repository.service.updateDocument(repository.ref2.id, null, null, null, workspace2, true);
		repository.service.updateDocument(repository.ref3.id, null, null, null, workspace2, true);
		assertEquals(2,repository.service.listWorkspaces(repository.ref1.id).count());
		assertEquals(1,repository.service.listWorkspaces(repository.ref2.id).count());
		assertEquals(1,repository.service.listWorkspaces(repository.ref3.id).count());
	}
	
	@Test
	public void testCreateWorkspace() throws InvalidWorkspaceId {
		TestRepository repository = getTestRepository();
		String workspace = repository.service.updateWorkspaceByName("abc", null, State.Open, true);
		UUID result = UUID.fromString(workspace);
		assertNotNull(result);
	}
	
	@Test
	public void testCreateAndFindWorkspace() throws InvalidWorkspaceId {
		TestRepository repository = getTestRepository();
		String workspace = repository.service.updateWorkspaceByName("abc", "abc", State.Open, true);
		Workspace ws = repository.service.getWorkspaceByName("abc");
		assertEquals(workspace, ws.getId());
	}
}
