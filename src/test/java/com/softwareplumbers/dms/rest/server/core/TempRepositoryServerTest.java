package com.softwareplumbers.dms.rest.server.core;

import static org.junit.Assert.*;


import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;

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

import com.softwareplumbers.dms.rest.server.model.Document;
import com.softwareplumbers.dms.rest.server.test.TestRepository;

import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class TempRepositoryServerTest {

    @LocalServerPort
    private int port;
    
    Client client = ClientBuilder.newClient(new ClientConfig()
    		.register(MultiPartFeature.class)
            .register(JsonProcessingFeature.class));
    
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
    
    public Document.Default getDocument(String id) throws IOException, ParseException {
		
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
				return new Document.Default(mediaType, ()->doc_source, metadata);
			}
		} 

		System.out.println(response.getEntity().toString());
		throw new RuntimeException("Bad post");
    }

	@Test
	public void heartbeatTest() {
	   	WebTarget target = client.target("http://localhost:" + port + "/heartbeat");

    	Response response = target
    			.request(MediaType.APPLICATION_JSON)
    			.get();
		assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
	}
	
	@Test
	public void postDocumentTest() throws IllegalStateException, IOException {

		JsonObject response = postDocument("test1");
		
		String id = response.getString("id");
		
		assertNotNull(id);
	}
	
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
	
	@Test
	public void roundtripDocumentTest() throws IllegalStateException, IOException, ParseException {

		JsonObject response = postDocument("test1");
		
		String id = response.getString("id");
		
		assertNotNull(id);
		
		Document doc = getDocument(id);
		
		assertNotNull(doc);
		
		assertTrue(TestRepository.docEquals("test1", doc));
		
	}
}
