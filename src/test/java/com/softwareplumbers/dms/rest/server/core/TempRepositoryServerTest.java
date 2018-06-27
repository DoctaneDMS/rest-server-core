package com.softwareplumbers.dms.rest.server.core;

import static org.junit.Assert.*;


import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.List;
import java.util.stream.Collectors;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.jsonp.JsonProcessingFeature;
import org.glassfish.jersey.media.multipart.BodyPart;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.softwareplumbers.dms.rest.server.model.DocumentImpl;
import com.softwareplumbers.dms.rest.server.model.Info;
import com.softwareplumbers.dms.rest.server.model.Document;
import com.softwareplumbers.dms.rest.server.model.Reference;
import com.softwareplumbers.dms.rest.server.test.TestRepository;

import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class TempRepositoryServerTest {

    @LocalServerPort
    private int port;
    
    /* Register a client that will use jersey's JSON processing 
     * and Multipart processing features.
     */
    Client client = ClientBuilder.newClient(new ClientConfig()
    		.register(MultiPartFeature.class)
            .register(JsonProcessingFeature.class));
    
    /** Utility function to post a document using the Jersey client API.
     * 
     * Test documents are held in src/test/resources in this project. Two files
     * <I>name</I>.txt and <I>name</I>.json make up a single test document, 
     * where the json file contains the metadata.
     * 
     * @Param name Name of test document file (without extension)
     * @return The result of posting the document to the test server.
     * 
     */
    public JsonObject postDocument(String name) throws IOException {
    	WebTarget target = client.target("http://localhost:" + port + "/docs/tmp");
    	
    	MultiPart multiPart = new MultiPart();
        multiPart.setMediaType(MediaType.MULTIPART_FORM_DATA_TYPE);
        
        StreamDataBodyPart file = new StreamDataBodyPart(
        	"file",
             TestRepository.getTestFile("/"+name+".txt"),
             MediaType.TEXT_PLAIN);

        FormDataBodyPart metadata = new FormDataBodyPart(
            	"metadata",
                 TestRepository.getTestMetadata("/"+name+".json"),
                 MediaType.APPLICATION_JSON_TYPE);
        
        multiPart.bodyPart(file);
        multiPart.bodyPart(metadata);

    	Response response = target
    			.request(MediaType.APPLICATION_JSON)
    			.post(Entity.entity(multiPart, multiPart.getMediaType()));
    	
			
		if (response.getStatus() == Response.Status.CREATED.getStatusCode()) {
			return response.readEntity(JsonObject.class);
		} else {
			System.out.println(response.toString());
			throw new RuntimeException("Bad post");
		}
    }
    
    /** Utility function to put a document using the Jersey client API.
     * 
     * Test documents are held in src/test/resources in this project. Two files
     * <I>name</I>.txt and <I>name</I>.json make up a single test document, 
     * where the json file contains the metadata.
     * 
     * @param name Name of test document file (without extension)
     * @param id Id of document to update
     * @return The result of posting the document to the test server.
     * 
     */
    public JsonObject putDocument(String name, String id) throws IOException {
    	WebTarget target = client.target("http://localhost:" + port + "/docs/tmp/" + id);
    	
    	MultiPart multiPart = new MultiPart();
        multiPart.setMediaType(MediaType.MULTIPART_FORM_DATA_TYPE);
        
        StreamDataBodyPart file = new StreamDataBodyPart(
        	"file",
             TestRepository.getTestFile("/"+name+".txt"),
             MediaType.TEXT_PLAIN);

        FormDataBodyPart metadata = new FormDataBodyPart(
            	"metadata",
                 TestRepository.getTestMetadata("/"+name+".json"),
                 MediaType.APPLICATION_JSON_TYPE);
        
        multiPart.bodyPart(file);
        multiPart.bodyPart(metadata);

    	Response response = target
    			.request(MediaType.APPLICATION_JSON)
    			.put(Entity.entity(multiPart, multiPart.getMediaType()));
    	
			
		if (response.getStatus() == Response.Status.ACCEPTED.getStatusCode()) {
			return response.readEntity(JsonObject.class);
		} else {
			System.out.println(response.toString());
			throw new RuntimeException("Bad put");
		}
    }

    
    /** Utility function to get a document from the local test server
     * 
     * @param id The id of the document to get
     * @return The document if it exists
     * @throws IOException In the case of low-level IO error
     * @throws ParseException If response cannot be parsed
     */
    public DocumentImpl getDocument(String id) throws IOException, ParseException {
		
    	WebTarget target = client.target("http://localhost:" + port + "/docs/tmp/" + id);

    	Response response = target
    			.request(MediaType.APPLICATION_JSON, MediaType.MULTIPART_FORM_DATA)
    			.get();
    	
		JsonObject metadata = null;
		InputStream is = null;
		MediaType mediaType = null;
    	
		if (response.getStatus() == Response.Status.OK.getStatusCode()) {
			MultiPart entity = response.readEntity(MultiPart.class);
			for (BodyPart part : entity.getBodyParts()) {
				String cd = part.getHeaders().getFirst("Content-Disposition");
				String name = new FormDataContentDisposition(cd).getName();
				if (name.equals("metadata")) {
					metadata = part.getEntityAs(JsonObject.class);
				}
				if (name.equals("file")) {
					mediaType = part.getMediaType();
					is = part.getEntityAs(InputStream.class);
				}
			}
			if (metadata != null && is != null) {
				InputStream doc_source = is;
				return new DocumentImpl(mediaType, ()->doc_source, metadata);
			}
		} 

		System.out.println(response.getEntity().toString());
		throw new RuntimeException("Bad post");
    }

    /** Utility function to get a catalog from the local test server
     * 
     * @param the catalog to get (may be "/")
     * @return a list of references
     * @throws IOException In the case of low-level IO error
     * @throws ParseException If response cannot be parsed
     */
    public List<Info> getCatalog(String id) throws IOException, ParseException {
		
    	WebTarget target = client.target("http://localhost:" + port + "/cat/tmp" + id);

    	Response response = target
    			.request(MediaType.APPLICATION_JSON)
    			.get();
    	
		if (response.getStatus() == Response.Status.OK.getStatusCode()) {
			JsonArray result = response.readEntity(JsonArray.class);
			return result
				.stream()
				.map(value -> Info.fromJson((JsonObject)value))
				.collect(Collectors.toList());
		} 

		System.out.println(response.getEntity().toString());
		throw new RuntimeException("Bad get: " + response.getStatus());
    }

    /** Test that the server responds on its heartbeat URL.
     * 
     */
	@Test
	public void heartbeatTest() {
	   	WebTarget target = client.target("http://localhost:" + port + "/heartbeat");

    	Response response = target
    			.request(MediaType.APPLICATION_JSON)
    			.get();
		assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
	}
	
    /** Test that posting a test document returns a non-null ID.
     * 
     * Posts to /docs/{repository}/ are expected to be multipart posts containing
     * two parts, one named 'file' and one named 'metadata'.
     */
	@Test
	public void postDocumentTest() throws IllegalStateException, IOException {

		JsonObject response = postDocument("test1");
		
		String id = response.getString("id");
		
		assertNotNull(id);
	}
	
    /** Test that posting a test file returns a non-null ID.
     * 
     * Posts to /docs/{repository}/file are expected to be simple posts containing
     * binary document data and a content type. The metadata in the resulting documetn
     * will be empty.
     */
	@Test
	public void postFileTest() throws IllegalStateException, IOException {

    	WebTarget target = client.target("http://localhost:" + port + "/docs/tmp/file");
    	
        InputStream file = TestRepository.getTestFile("/test1.txt");

    	Response response = target
    			.request(MediaType.APPLICATION_JSON_TYPE)
    			.post(Entity.entity(file, MediaType.TEXT_PLAIN));
    	
			
		if (response.getStatus() == Response.Status.CREATED.getStatusCode()) {
			JsonObject result = response.readEntity(JsonObject.class);
			assertNotNull(result.get("id"));
		} else {
			System.out.println(response.toString());
			throw new RuntimeException("Bad post");
		}

	}
	
    /** Test that getting a document that was posted returns a document equal to the original.
     * 
     * Posts to /docs/{repository}/ are expected to be multipart posts containing
     * two parts, one named 'file' and one named 'metadata'. The response contains a
     * json object with an id property. Getting /docs/{repository}/{id} should return
     * a multipart response with file and metadata components identical to the original.
     */
	@Test
	public void roundtripDocumentTest() throws IllegalStateException, IOException, ParseException {

		JsonObject response = postDocument("test1");
		
		String id = response.getString("id");
		
		assertNotNull(id);
		
		Document doc = getDocument(id);
		
		assertNotNull(doc);
		
		assertTrue(TestRepository.docEquals("test1", doc));
		
	}
	
    /** Test that updating a document returns a new version.
     * 
     * Puts to /docs/{repository}/{id} are expected to be multi-part posts containing
     * two parts, one named 'file' and one named 'metadata'. The response contains a
     * json object with id and version property. The returned id should be the same
     * as the original document, whereas the version should be incremented.   
     */
	@Test
	public void putDocumentTest() throws IllegalStateException, IOException, ParseException {

		JsonObject response1 = postDocument("test1");
		
		assertNotNull(response1.getString("id"));
		
		JsonObject response2 = putDocument("test2", response1.getString("id"));
		
		assertNotNull(response2.getString("id"));
		
		assertEquals(response1.getString("id"), response2.getString("id"));
		//TODO: fixme
		//assertNotEquals(response1.getInt("version"), response2.getInt("version"));
		
	}
	
	@Test
	public void searchDocumentTest() throws IllegalStateException, IOException, ParseException {

		List<Info> catalog0 = getCatalog("/");
		JsonObject response1 = postDocument("test1");
		JsonObject response2 = putDocument("test2", response1.getString("id"));
		JsonObject response3 = postDocument("test3");
		List<Info> catalog1 = getCatalog("/");
		assertEquals(2, catalog1.size() - catalog0.size());
		assertTrue(catalog1.stream().anyMatch(item->item.reference.equals(Reference.fromJson(response2))));
		assertTrue(catalog1.stream().anyMatch(item->item.reference.equals(Reference.fromJson(response3))));
		assertFalse(catalog1.stream().anyMatch(item->item.reference.equals(Reference.fromJson(response1))));
	}
}
