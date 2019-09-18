package com.softwareplumbers.dms.rest.server.tmp;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractquery.Query;
import com.softwareplumbers.dms.rest.server.model.BaseRepositoryServiceTest;
import com.softwareplumbers.dms.rest.server.model.Document;
import com.softwareplumbers.dms.rest.server.model.DocumentNavigatorService;
import com.softwareplumbers.dms.rest.server.model.Reference;
import com.softwareplumbers.dms.rest.server.model.RepositoryObject;
import com.softwareplumbers.dms.rest.server.model.RepositoryService;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidDocumentId;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidReference;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspace;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspaceState;
import com.softwareplumbers.dms.rest.server.model.ZipFileNavigatorService;
import com.softwareplumbers.dms.rest.server.test.TestRepository;

public class TempRepositoryServiceTest extends BaseRepositoryServiceTest {
	
	public TempRepositoryService service;
    public ZipFileNavigatorService navigator;
	
	@Before
	public void createService() {
		service = new TempRepositoryService(new ZipFileNavigatorService(), QualifiedName.of("filename"));
        navigator = new ZipFileNavigatorService();
	}
	
	public TestRepository getTestRepository() {
		return new TestRepository(service);
	}
	
	@Test
	public void testRepositoryRoundtrip() throws IOException {
		TestRepository repository = getTestRepository();
		assertTrue(TestRepository.docEquals("test1", repository.doc1));
		assertTrue(TestRepository.docEquals("test2", repository.doc2));
		assertTrue(TestRepository.docEquals("test3", repository.doc3));
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
	public void testRepositorySearch() throws IOException, InvalidWorkspace {
		TestRepository repository = getTestRepository();
		RepositoryObject[] result = repository.service.catalogue(Query.fromJson("{ 'metadata': { 'filename': 'partiphuckborlz'}}"), false).toArray(RepositoryObject[]::new);
		assertEquals(result.length, 1);
		assertEquals(((Document)result[0]).getReference(), repository.ref2);
	}
	
	
	@Test 
	public void testWorkspaceUpdate() throws InvalidDocumentId, InvalidWorkspace, InvalidWorkspaceState {
		TestRepository repository = getTestRepository();
		String workspace_id = UUID.randomUUID().toString();
		repository.service.updateDocument(repository.ref1.id, null, null, null, workspace_id, true);
		assertEquals(repository.service.catalogueById(workspace_id, null, false).count(), 1);
		repository.service.deleteDocument(workspace_id, QualifiedName.ROOT, repository.ref1.id);
		assertEquals(repository.service.catalogueById(workspace_id, null, false).count(), 0);
	}
	
	@Test 
	public void testListWorkspaces() throws InvalidDocumentId, InvalidWorkspace, InvalidWorkspaceState {
		TestRepository repository = getTestRepository();
		String workspace1 = UUID.randomUUID().toString();
		String workspace2 = UUID.randomUUID().toString();
		repository.service.updateDocument(repository.ref1.id, null, null, null, workspace1, true);
		repository.service.updateDocument(repository.ref1.id, null, null, null, workspace2, true);
		repository.service.updateDocument(repository.ref2.id, null, null, null, workspace2, true);
		repository.service.updateDocument(repository.ref3.id, null, null, null, workspace2, true);
		assertEquals(2,repository.service.listWorkspaces(repository.ref1.id, null).count());
		assertEquals(1,repository.service.listWorkspaces(repository.ref2.id, null).count());
		assertEquals(1,repository.service.listWorkspaces(repository.ref3.id, null).count());
	}
	
	@Override
	public RepositoryService service() {
		return service;
	}
    
    @Override
    public DocumentNavigatorService navigator() {
        return navigator;
    }

	@Override
	public Reference randomDocumentReference() {
		return new Reference(UUID.randomUUID().toString(), null);
	}

	@Override
	public String randomWorkspaceId() {
		return UUID.randomUUID().toString();
	}
	

	

}
