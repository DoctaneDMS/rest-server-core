package com.softwareplumbers.dms.rest.server.tmp;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.stream.JsonParser;
import javax.ws.rs.core.MediaType;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import com.softwareplumbers.dms.rest.server.model.Document;
import com.softwareplumbers.dms.rest.server.model.RepositoryService;

public class TempRepositoryServiceTest {
	
	static InputStream getTestFile(String name) {
		return TempRepositoryServiceTest.class.getResourceAsStream(name);
	}
	
	static JsonObject getTestMetadata(String name) throws IOException {
		try (InputStream stream = TempRepositoryServiceTest.class.getResourceAsStream(name)) {
			JsonParser parser = Json.createParser(stream);
			parser.next();
			return parser.getObject();
		}
	}
	
	static Document.Reference getTestDocument(RepositoryService service, String name) {
		try {
			return service.createDocument(
					MediaType.TEXT_PLAIN_TYPE, 
					() -> getTestFile("/" + name + ".txt"), 
					getTestMetadata("/" + name + ".json")
				);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	static boolean docEquals(String name, Document document) throws IOException {
		byte[] testfile = IOUtils.toByteArray(getTestFile("/" + name + ".txt"));
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		document.writeDocument(stream);
		JsonObject metadata = getTestMetadata("/" + name + ".json");
		return metadata.equals(document.getMetadata()) && Arrays.equals(testfile, stream.toByteArray());
	}
	
	static class TestRepository {
		public RepositoryService service = new TempRepositoryService();
		public Document.Reference ref1 = getTestDocument(service,"test1");
		public Document.Reference ref2 = getTestDocument(service,"test2");
		public Document.Reference ref3 = getTestDocument(service,"test3");
		public Document doc1 = service.getDocument(ref1);
		public Document doc2 = service.getDocument(ref2);
		public Document doc3 = service.getDocument(ref3);		
	}

	@Test
	public void testRepositoryRoundtrip() throws IOException {
		TestRepository repository = new TestRepository();
		assertTrue(docEquals("test1", repository.doc1));
		assertTrue(docEquals("test2", repository.doc2));
		assertTrue(docEquals("test3", repository.doc3));
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
		assertTrue(docEquals("test1", repository.service.getDocument(ref1)));
		Document.Reference ref2 = new Document.Reference(repository.ref2.id);
		assertTrue(docEquals("test2", repository.service.getDocument(ref2)));
		Document.Reference ref3 = new Document.Reference(repository.ref3.id);
		assertTrue(docEquals("test3", repository.service.getDocument(ref3)));
	}
}
