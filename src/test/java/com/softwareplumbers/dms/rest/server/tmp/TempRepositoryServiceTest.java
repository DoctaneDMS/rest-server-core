package com.softwareplumbers.dms.rest.server.tmp;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;

import org.junit.Test;

import com.softwareplumbers.common.abstractquery.Query;
import com.softwareplumbers.dms.rest.server.model.Reference;
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
	
	@Test
	public void testRepositoryFetchWithInvalidRef() throws IOException {
		TestRepository repository = getTestRepository();
		Reference ref1 = new Reference("xxx");
		assertNull(repository.service.getDocument(ref1));
	}
	
	@Test
	public void testRepositoryFetchWithInvalidVersion() throws IOException {
		TestRepository repository = getTestRepository();
		Reference ref1 = new Reference(repository.ref1.id, 777);
		assertNull(repository.service.getDocument(ref1));
	}
	
	@Test
	public void testRepositoryFetchWithNoVersion() throws IOException {
		TestRepository repository = getTestRepository();
		Reference ref1 = new Reference(repository.ref1.id);
		assertTrue(TestRepository.docEquals("test1", repository.service.getDocument(ref1)));
		Reference ref2 = new Reference(repository.ref2.id);
		assertTrue(TestRepository.docEquals("test2", repository.service.getDocument(ref2)));
		Reference ref3 = new Reference(repository.ref3.id);
		assertTrue(TestRepository.docEquals("test3", repository.service.getDocument(ref3)));
	}
	
	@Test
	public void testRepositoryCatalog() throws IOException {
		TestRepository repository = getTestRepository();
		List<Reference> result = repository.service.catalogue(Query.UNBOUNDED);
		assertEquals(result.size(), 3);
	}
	
	@Test
	public void testRepositoryCatalogWithVersions() throws IOException {
		TestRepository repository = getTestRepository();
		Reference ref4 = repository.service.updateDocument(repository.ref2.id, repository.doc3.getMediaType(), null, repository.doc3.getMetadata() );
		assertEquals(1, (int)ref4.version);
		List<Reference> result = repository.service.catalogue(Query.UNBOUNDED);
		assertEquals(result.size(), 3);
	}
	
	@Test
	public void testRepositorySearch() throws IOException {
		TestRepository repository = getTestRepository();
		List<Reference> result = repository.service.catalogue(Query.from("{ 'filename': 'partiphuckborlz'}"));
		assertEquals(result.size(), 1);
		assertEquals(result.get(0), repository.ref2);
	}
}
