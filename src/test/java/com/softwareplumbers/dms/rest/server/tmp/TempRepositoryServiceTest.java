package com.softwareplumbers.dms.rest.server.tmp;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Test;

import com.softwareplumbers.dms.rest.server.model.Document;
import com.softwareplumbers.dms.rest.server.test.TestRepository;

public class TempRepositoryServiceTest {
	

	
	@Test
	public void testRepositoryRoundtrip() throws IOException {
		TestRepository repository = new TestRepository();
		assertTrue(TestRepository.docEquals("test1", repository.doc1));
		assertTrue(TestRepository.docEquals("test2", repository.doc2));
		assertTrue(TestRepository.docEquals("test3", repository.doc3));
	}
	
	@Test
	public void testRepositoryFetchWithInvalidRef() throws IOException {
		TestRepository repository = new TestRepository();
		Document.Reference ref1 = new Document.Reference("xxx");
		assertNull(repository.service.getDocument(ref1));
	}
	
	@Test
	public void testRepositoryFetchWithInvalidVersion() throws IOException {
		TestRepository repository = new TestRepository();
		Document.Reference ref1 = new Document.Reference(repository.ref1.id, 777);
		assertNull(repository.service.getDocument(ref1));
	}
	
	@Test
	public void testRepositoryFetchWithNoVersion() throws IOException {
		TestRepository repository = new TestRepository();
		Document.Reference ref1 = new Document.Reference(repository.ref1.id);
		assertTrue(TestRepository.docEquals("test1", repository.service.getDocument(ref1)));
		Document.Reference ref2 = new Document.Reference(repository.ref2.id);
		assertTrue(TestRepository.docEquals("test2", repository.service.getDocument(ref2)));
		Document.Reference ref3 = new Document.Reference(repository.ref3.id);
		assertTrue(TestRepository.docEquals("test3", repository.service.getDocument(ref3)));
	}
}
