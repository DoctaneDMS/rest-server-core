package com.softwareplumbers.dms.rest.server.core;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Signature;
import java.security.SignatureException;
import java.text.ParseException;
import java.util.Base64;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import javax.json.JsonWriter;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.w3c.dom.Node;

import com.softwareplumbers.dms.rest.server.model.DocumentImpl;
import com.softwareplumbers.dms.rest.server.model.Document;
import com.softwareplumbers.dms.rest.server.model.Reference;
import com.softwareplumbers.dms.rest.server.model.UpdateType;
import com.softwareplumbers.dms.rest.server.test.TestRepository;
import com.softwareplumbers.keymanager.KeyManager;

import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT, classes = Application.class)
@EnableConfigurationProperties
public class TempRepositoryServerTest {
	
	private static final Map<String,MediaType> mediaTypesByExtension = new HashMap<String,MediaType>() {{
		put("txt", MediaType.TEXT_PLAIN_TYPE);
		put("docx", MediaType.valueOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
	}};

	int port = 8080;
    
    /* Register a client that will use jersey's JSON processing 
     * and Multipart processing features.
     */
    Client client = ClientBuilder.newClient(new ClientConfig()
    		.register(MultiPartFeature.class)
            .register(JsonProcessingFeature.class));
    
    @Autowired
    AuthenticationService cookieHandler;
    @Autowired
    KeyManager keyManager;
    
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
    public JsonObject postDocument(String name, String workspace, String ext) throws IOException {
    	WebTarget target = client.target("http://localhost:" + port + "/docs/tmp");
    	if (workspace != null) target = target.queryParam("workspace", workspace).queryParam("createWorkspace", true);
    	MultiPart multiPart = new MultiPart();
        multiPart.setMediaType(MediaType.MULTIPART_FORM_DATA_TYPE);
        
        MediaType type = mediaTypesByExtension.get(ext);
        
        StreamDataBodyPart file = new StreamDataBodyPart(
        	"file",
             TestRepository.getTestFile("/"+name+"."+ext),
             type.toString());

        FormDataBodyPart metadata = new FormDataBodyPart(
            	"metadata",
                 TestRepository.getTestMetadata("/"+name+".json"),
                 MediaType.APPLICATION_JSON_TYPE);
        
        multiPart.bodyPart(file);
        multiPart.bodyPart(metadata);

    	Response response = target
    			.request(MediaType.APPLICATION_JSON)
                .cookie(cookieHandler.generateCookie("test_user"))
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
     * @param path Path of document to update (including base, so /docs/tmp/id or /ws/tmp/workspace/docName)
     * @param ext File extension
     * @return The result of posting the document to the test server.
     * 
     */
    public JsonObject putDocument(String name, String path, String ext) throws IOException {
    	WebTarget target = client.target("http://localhost:" + port + path);
    	    	
    	MultiPart multiPart = new MultiPart();
        multiPart.setMediaType(MediaType.MULTIPART_FORM_DATA_TYPE);
        
        StreamDataBodyPart file = new StreamDataBodyPart(
        	"file",
             TestRepository.getTestFile("/"+name+"."+ext),
             mediaTypesByExtension.get(ext).toString());

        FormDataBodyPart metadata = new FormDataBodyPart(
            	"metadata",
                 TestRepository.getTestMetadata("/"+name+".json"),
                 MediaType.APPLICATION_JSON_TYPE);
        
        multiPart.bodyPart(file);
        multiPart.bodyPart(metadata);

    	Response response = target
    			.request(MediaType.APPLICATION_JSON)
                .cookie(cookieHandler.generateCookie("test_user"))
    			.put(Entity.entity(multiPart, multiPart.getMediaType()));
    	
			
		if (response.getStatus() == Response.Status.ACCEPTED.getStatusCode()) {
			return response.readEntity(JsonObject.class);
		} else {
			System.out.println(response.toString());
			throw new RuntimeException("Bad put");
		}
    }
    
    /** Utility function to put a document using the Jersey client API.
     * 
     * Test documents are held in src/test/resources in this project. Two files
     * <I>name</I>.txt and <I>name</I>.json make up a single test document, 
     * where the json file contains the metadata.
     * 
     * @param name Name of test document file (without extension)
     * @param path Path of document to update (including base, so /docs/tmp/id or /ws/tmp/workspace/docName)
     * @return The result of posting the document to the test server.
     * 
     */
    public void putDocumentLink(String path, String id, UpdateType type) throws IOException {
        WebTarget target = client.target("http://localhost:" + port + path + "?createWorkspace=true&updateType=" + type);
        
        JsonObject link = Json.createObjectBuilder()
            .add("type", "DOCUMENT_LINK")
            .add("reference", Json.createObjectBuilder()
                 .add("id", id)
                 .build())
            .build();
        
        Response response = target
                .request(MediaType.APPLICATION_JSON)
                .cookie(cookieHandler.generateCookie("test_user"))
                .put(Entity.entity(link, MediaType.APPLICATION_JSON_TYPE));
        
        if (response.getStatus() != Response.Status.ACCEPTED.getStatusCode()) {
            System.out.println(response.toString());
            throw new RuntimeException("Bad put");
        }
    }
    
    /** Utility function to put a workspace using the Jersey client API.
     * 
     * @param path Path of workspace
     * @param data for workspace
     * @return Json object containing the id of the workspace
     * 
     */
    public JsonObject putWorkspace(String path, JsonObject data) throws IOException {
    	WebTarget target = client.target("http://localhost:" + port + "/ws/tmp/" + path);
    	
    	Response response = target
    			.request(MediaType.APPLICATION_JSON)
                .cookie(cookieHandler.generateCookie("test_user"))
    			.put(Entity.entity(data, MediaType.APPLICATION_JSON_TYPE));
			
		if (response.getStatus() == Response.Status.ACCEPTED.getStatusCode()) {
			return response.readEntity(JsonObject.class);
		} else {
			System.out.println(response.toString());
			throw new RuntimeException("Bad put");
		}
    }
    
    /** Utility function to get a workspace using the Jersey client API.
     * 
     * @param path Path of workspace
     * @return Json object containing data for the workspace
     * 
     */
    public <T extends JsonValue> T getWorkspaceJson(String path, Class<T> resultType) throws IOException {
    	WebTarget target = client.target("http://localhost:" + port + "/ws/tmp/" + path);
    	
    	Response response = target
    			.request(MediaType.APPLICATION_JSON)
                .cookie(cookieHandler.generateCookie("test_user"))
    			.get();
			
		if (response.getStatus() == Response.Status.OK.getStatusCode()) {
			return response.readEntity(resultType);
		} else {
			System.out.println(response.toString());
			throw new RuntimeException("Bad get");
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

    	return getDocumentFromTarget(target);
    } 
    
    /** Utility function to get a document from the local test server
     * 
     * @param path The workspace path of the document to get
     * @return The document if it exists
     * @throws IOException In the case of low-level IO error
     * @throws ParseException If response cannot be parsed
     */
    public DocumentImpl getDocumentFromWorkspace(String path) throws IOException, ParseException {
        
        WebTarget target = client.target("http://localhost:" + port + "/ws/tmp/" + path);

        return getDocumentFromTarget(target);
    } 

    /** Utility function to get a document from the local test server
     * 
     * @param target The target url
     * @return The document if it exists
     * @throws IOException In the case of low-level IO error
     * @throws ParseException If response cannot be parsed
     */
    public DocumentImpl getDocumentFromTarget(WebTarget target) throws IOException, ParseException {
        
        Response response = target
                .request(MediaType.MULTIPART_FORM_DATA)
                .cookie(cookieHandler.generateCookie("test_user"))
                .get();
        
        JsonObject metadata = null;
        InputStream is = null;
        MediaType mediaType = null;
        Reference reference = null;
        
        if (response.getStatus() == Response.Status.OK.getStatusCode()) {
            MultiPart entity = response.readEntity(MultiPart.class);
            for (BodyPart part : entity.getBodyParts()) {
                String cd = part.getHeaders().getFirst("Content-Disposition");
                String name = new FormDataContentDisposition(cd).getName();
                if (name.equals("metadata")) {
                    JsonObject data = part.getEntityAs(JsonObject.class);
                    metadata = data.getJsonObject("metadata");
                    reference = new Reference(data.getString("id"), data.getString("version"));
                }
                if (name.equals("file")) {
                    mediaType = part.getMediaType();
                    is = part.getEntityAs(InputStream.class);
                }
            }
            if (metadata != null && is != null) {
                InputStream doc_source = is;
                return new DocumentImpl(reference, mediaType, ()->doc_source, metadata);
            }
        } 

        System.out.println(response.getEntity().toString());
        throw new RuntimeException("Bad get");
    } 
    
    /** Utility function to get a document from the local test server
     * 
     * @param target The target url
     * @return The document if it exists
     * @throws IOException In the case of low-level IO error
     * @throws ParseException If response cannot be parsed
     */
    public Node getXMLFromTarget(WebTarget target) throws IOException, ParseException {
        
        Response response = target
                .request(MediaType.APPLICATION_XHTML_XML_TYPE)
                .cookie(cookieHandler.generateCookie("test_user"))
                .get();
        
        if (response.getStatus() == Response.Status.OK.getStatusCode()) {
        	return response.readEntity(org.w3c.dom.Document.class);
        } 

        System.out.println(response.getEntity().toString());
        throw new RuntimeException("Bad get");
    } 

    /** Utility function to get a document from the local test server
     * 
     * @param id The id of the document to get
     * @return The document if it exists
     * @throws IOException In the case of low-level IO error
     * @throws ParseException If response cannot be parsed
     */
    public Node getXMLDocument(String id) throws IOException, ParseException {
		
    	WebTarget target = client.target("http://localhost:" + port + "/docs/tmp/" + id + "/xhtml");

    	return getXMLFromTarget(target);
    } 
    
    public static void printDocument(Node doc) throws IOException, TransformerException {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        transformer.transform(new DOMSource(doc), 
             new StreamResult(new OutputStreamWriter(System.out, "UTF-8")));
    }

    /** Utility function to get a catalog from the local test server
     * 
     * @param the catalog to get (may be "/")
     * @return a list of references
     * @throws IOException In the case of low-level IO error
     * @throws ParseException If response cannot be parsed
     */
    public JsonArray getCatalog(String id, String workspace) throws IOException, ParseException {
		
    	WebTarget target = client.target("http://localhost:" + port + "/cat/tmp" + id);

    	if (workspace != null) target = target.queryParam("workspace", workspace);
    	
    	Response response = target
    			.request(MediaType.APPLICATION_JSON)
    			.cookie(cookieHandler.generateCookie("test_user"))
    			.get();
    	
		if (response.getStatus() == Response.Status.OK.getStatusCode()) {
			JsonArray result = response.readEntity(JsonArray.class);
			return result;
		} 

		System.out.println(response.getEntity().toString());
		throw new RuntimeException("Bad get: " + response.getStatus());
    }
    
    public void clear() {
       	WebTarget target = client.target("http://localhost:" + port + "/admin/temp/tmp/clear" );

    	Response response = target
    			.request(MediaType.APPLICATION_JSON)
    			.get();
    	
		if (!(response.getStatus() == Response.Status.OK.getStatusCode())) {
			throw new RuntimeException("Bad get: " + response.getStatus());
		}
    }


    /** Test that the server responds on its heartbeat URL.
     * 
     */
	@Test
	public void heartbeatTest() {
	   	WebTarget target = client.target("http://localhost:" + port + "/heartbeat");

    	Response response = target
    			.request(MediaType.TEXT_PLAIN)
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

		JsonObject response = postDocument("test1", null,"txt");
		
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
                .cookie(cookieHandler.generateCookie("test_user"))
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

		JsonObject response = postDocument("test1", null, "txt");
		
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

		JsonObject response1 = postDocument("test1", null, "txt");
		
		assertNotNull(response1.getString("id"));
		
		JsonObject response2 = putDocument("test2", "/docs/tmp/" + response1.getString("id"),"txt");
		
		assertNotNull(response2.getString("id"));
		
		assertEquals(response1.getString("id"), response2.getString("id"));
		//TODO: fixme
		//assertNotEquals(response1.getInt("version"), response2.getInt("version"));
		
	}
	
    /** Test that we can create a named document in a workspace by a put to a specific url
     * 
     * Puts to /ws/{repository}/path may either be multi-part content containing
     * a document, or simple content containing an update to a workspace. The response contains a
     * json object, with either id and version property or...?
     */
	@Test
	public void putDocumentInWorkspaceTest() throws IllegalStateException, IOException, ParseException {
		JsonObject response1 = putDocument("test2", "/ws/tmp/wsname/doc1","txt");
		String wsId = response1.getString("id");
		assertNotNull(wsId);
		JsonArray response2 = getWorkspaceJson("wsname/*", JsonArray.class);
		assertEquals(1, response2.size());		
	}
	
	private static Reference getRef(JsonValue value) {
	    JsonObject obj = (JsonObject)value;
	    return new Reference(obj.getString("id", null), obj.getString("version", null));
	}
	
	@Test
	public void searchDocumentTest() throws IllegalStateException, IOException, ParseException {

		JsonArray catalog0 = getCatalog("/",null);
		JsonObject response1 = postDocument("test1", null, "txt");
		JsonObject response2 = putDocument("test2", "/docs/tmp/" + response1.getString("id"),"txt");
		JsonObject response3 = postDocument("test3", null, "txt");
		JsonArray catalog1 = getCatalog("/",null);
		assertEquals(2, catalog1.size() - catalog0.size());
		assertTrue(catalog1.stream().anyMatch(item->getRef(item).equals(Reference.fromJson(response2))));
		assertTrue(catalog1.stream().anyMatch(item->getRef(item).equals(Reference.fromJson(response3))));
		assertFalse(catalog1.stream().anyMatch(item->getRef(item).equals(Reference.fromJson(response1))));
	}
	
	@Test
	public void searchWorkspaceTest() throws IOException, ParseException 
	{
		String workspaceA = UUID.randomUUID().toString();
		String workspaceB = UUID.randomUUID().toString();
		
		clear();
		postDocument("test1", workspaceA, "txt");
		postDocument("test2", workspaceA, "txt");
		postDocument("test3", workspaceB, "txt");
		JsonArray catalog0 = getCatalog("/", workspaceA);
		assertEquals(2, catalog0.size());
		JsonArray catalog1 = getCatalog("/", workspaceB);
		assertEquals(1, catalog1.size());
	}
	
	@Test
	public void createWorkspaceTest() throws IOException {
		JsonObject workspace = Json.createObjectBuilder()
			.add("state","Open")
			.build();
		
		JsonObject result = putWorkspace("test4/test5", workspace);
		
		assertTrue(result.containsKey("id"));
		
		JsonObject result2 = getWorkspaceJson("test4/test5", JsonObject.class);
		
		assertEquals("test4/test5", result2.getString("name"));
		assertEquals("Open", result2.getString("state"));
	}
	
	@Test
	public void getWorkspaceByIdTest() throws IOException {
		JsonObject workspace = Json.createObjectBuilder()
				.add("state","Open")
				.build();
			
			JsonObject result = putWorkspace("test4/test5", workspace);
			
			assertTrue(result.containsKey("id"));
			
			JsonObject result2 = getWorkspaceJson("~"+result.getString("id"), JsonObject.class);
			
			assertEquals("test4/test5", result2.getString("name"));
			assertEquals("Open", result2.getString("state"));		
	}
	
    /** Test that we can get a document from a workspace either as json or a full multipart response
     * 
     */
    @Test
    public void getDocumentFromWorkspaceTest() throws IllegalStateException, IOException, ParseException {
        JsonObject response1 = putDocument("test2", "/ws/tmp/wsname/doc1", "txt");
        String wsId = response1.getString("id");
        assertNotNull(wsId);
        JsonObject response2 = getWorkspaceJson("wsname/doc1", JsonObject.class);
        assertEquals(wsId, response2.getString("id"));      
        DocumentImpl doc = getDocumentFromWorkspace("wsname/doc1");
        assertEquals(wsId, doc.getId());
    }
    
    @Test
    public void testOptionsHasCORSHeaders() {
        WebTarget target = client.target("http://localhost:" + port + "/ws/tmp/");
        
        Response response = target
                .request()
                .header("Origin","http://localhost:"+ port) // IDK why this does't work. Security-through-obscurity sucks.
                .options();
            
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertNotNull(response.getHeaderString("Access-Control-Allow-Headers"));
    }
    
    @Test
    public void testCreateDocumentLink() throws IOException, ParseException {
        JsonObject response1 = putDocument("test2", "/ws/tmp/wsname/doc1", "txt");
        String wsId = response1.getString("id");
        assertNotNull(wsId);
        putDocumentLink("/ws/tmp/anotherws/myDoc", wsId, UpdateType.CREATE);
        DocumentImpl doc = getDocumentFromWorkspace("anotherws/myDoc");
        assertEquals(wsId, doc.getId());
        
    }

    @Test
    public void testListWorkspaces() throws IOException, ParseException {
        JsonObject response1 = putDocument("test2", "/ws/tmp/wsname/doc1", "txt");
        String wsId = response1.getString("id");
        putDocumentLink("/ws/tmp/anotherws/myDoc", wsId, UpdateType.CREATE);
        DocumentImpl doc = getDocumentFromWorkspace("anotherws/myDoc");
        JsonArray result = getWorkspaceJson("/*/~"+wsId, JsonArray.class);
        assertEquals(2, result.size());
    }
    
    @Test
    public void testServiceAccountLogin() throws InvalidKeyException, NoSuchAlgorithmException, NoSuchProviderException, SignatureException {
        String account = SystemKeyPairs.DEFAULT_SERVICE_ACCOUNT.name();
        KeyPair pair = keyManager.getKeyPair(account);
        JsonObjectBuilder request = Json.createObjectBuilder(); 
        request.add("account", account);
        request.add("instant", System.currentTimeMillis());
        JsonObject requestObject = request.build();
        ByteArrayOutputStream out = new ByteArrayOutputStream(); 
        try (JsonWriter writer = Json.createWriter(out)) {
            writer.write(requestObject);
        } 
        byte[] requestBytes = out.toByteArray();
        Signature sig = Signature.getInstance("SHA1withDSA", "SUN");
        sig.initSign(pair.getPrivate());
        sig.update(requestBytes);
        byte[] signatureBytes = sig.sign();
        
        WebTarget target = client
            .target("http://localhost:" + port + "/auth/service")
            .queryParam("request", Base64.getUrlEncoder().encodeToString(requestBytes))
            .queryParam("signature", Base64.getUrlEncoder().encodeToString(signatureBytes));
  
        Response response = target.request().get();
            
        if (response.getStatus() == Response.Status.OK.getStatusCode()) {
            NewCookie cookie = response.getCookies().get("DoctaneUserToken");
            assertNotNull(cookie);
        } else {
            System.out.println(response.toString());
            throw new RuntimeException("Bad service auth request");
        }
    }

    @Test
    public void testGetDocumentXML() throws IOException, ParseException, TransformerException {
		JsonObject response1 = postDocument("testdoc", null, "docx");
		String id = response1.getString("id");
		Node xmlDoc = getXMLDocument(id);
		printDocument(xmlDoc);
    }
}

