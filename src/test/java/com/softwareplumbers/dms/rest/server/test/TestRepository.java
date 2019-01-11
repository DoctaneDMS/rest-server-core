package com.softwareplumbers.dms.rest.server.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParser.Event;
import javax.ws.rs.core.MediaType;

import org.apache.commons.io.IOUtils;

import com.softwareplumbers.dms.rest.server.model.Document;
import com.softwareplumbers.dms.rest.server.model.Reference;
import com.softwareplumbers.dms.rest.server.model.RepositoryService;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidReference;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspaceName;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspaceState;
import com.softwareplumbers.dms.rest.server.tmp.TempRepositoryService;
import com.softwareplumbers.dms.rest.server.tmp.TempRepositoryServiceTest;

public class TestRepository {
	
	public static InputStream getTestFile(String name) {
		return TestRepository.class.getResourceAsStream(name);
	}
	
	public static JsonObject getTestMetadata(String name) throws IOException {
		try (InputStream stream = TempRepositoryServiceTest.class.getResourceAsStream(name)) {
			JsonReader reader = Json.createReader(stream);
			return reader.readObject();
		}
	}
	
	public static Reference getTestDocument(RepositoryService service, String name) {
		try {
			return service.createDocument(
					MediaType.TEXT_PLAIN_TYPE, 
					() -> getTestFile("/" + name + ".txt"), 
					getTestMetadata("/" + name + ".json"),
					null,
					false
				);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public static boolean docEquals(String name, Document document) throws IOException {
		byte[] testfile = IOUtils.toByteArray(getTestFile("/" + name + ".txt"));
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		document.writeDocument(stream);
		JsonObject metadata = getTestMetadata("/" + name + ".json");
		return metadata.equals(document.getMetadata()) && Arrays.equals(testfile, stream.toByteArray());
	}
	
	public TestRepository(RepositoryService service) {
		this.service = service;
		ref1 = getTestDocument(service,"test1");
		ref2 = getTestDocument(service,"test2");
		ref3 = getTestDocument(service,"test3");
		try {
			doc1 = service.getDocument(ref1);
			doc2 = service.getDocument(ref2);
			doc3 = service.getDocument(ref3);
		} catch (InvalidReference err) {
			throw new RuntimeException(err);
		}
	}
	
	public RepositoryService service;
	public Reference ref1;
	public Reference ref2;
	public Reference ref3;
	public Document doc1;
	public Document doc2;
	public Document doc3;		
}