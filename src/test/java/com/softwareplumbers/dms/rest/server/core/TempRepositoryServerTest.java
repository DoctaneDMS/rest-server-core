package com.softwareplumbers.dms.rest.server.core;

import com.softwareplumbers.common.QualifiedName;
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
import org.w3c.dom.NodeList;

import com.softwareplumbers.dms.rest.server.model.StreamableRepositoryObjectImpl;
import com.softwareplumbers.dms.Document;
import com.softwareplumbers.dms.rest.server.model.DocumentImpl;
import com.softwareplumbers.dms.rest.server.model.StreamableDocumentPartImpl;
import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.RepositoryObject;
import com.softwareplumbers.dms.StreamableRepositoryObject;
import com.softwareplumbers.dms.rest.server.model.UpdateType;
import com.softwareplumbers.keymanager.BadKeyException;
import com.softwareplumbers.keymanager.InitializationFailure;
import com.softwareplumbers.keymanager.KeyManager;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Arrays;
import javax.json.JsonReader;
import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.client.ClientProperties;

import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT, classes = Application.class)
@EnableConfigurationProperties
public class TempRepositoryServerTest {
	
	@SuppressWarnings("serial")
    private static final Map<String,MediaType> mediaTypesByExtension = new HashMap<String,MediaType>() {{
		put("txt", MediaType.TEXT_PLAIN_TYPE);
		put("docx", MediaType.valueOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
		put("msg", MediaType.valueOf("application/vnd.ms-outlook"));
        put("doc", MediaType.valueOf("application/msword"));
        put("zip", MediaType.valueOf("application/zip"));
        put("eml", MediaType.valueOf("message/rfc822"));
	}};

	int port = 8080;
    
    /* Register a client that will use jersey's JSON processing 
     * and Multipart processing features.
     */
    Client client = ClientBuilder.newClient(new ClientConfig()
    		.register(MultiPartFeature.class)
            .register(JsonProcessingFeature.class));
    
    @Autowired
    CookieRequestValidationService cookieHandler;
    @Autowired
    KeyManager<?,?> keyManager;
    
    private static InputStream getTestFile(String name) {
        return TempRepositoryServerTest.class.getResourceAsStream(name);
    }
    
    private static JsonObject getTestMetadata(String name) throws IOException {
		try (InputStream stream = getTestFile(name)) {
			JsonReader reader = Json.createReader(stream);
			return reader.readObject();
		}
	}

    private static boolean docEquals(String name, StreamableRepositoryObject document) throws IOException {
		byte[] testfile = IOUtils.toByteArray(getTestFile("/" + name + ".txt"));
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		document.writeDocument(stream);
		JsonObject metadata = getTestMetadata("/" + name + ".json");
		return metadata.equals(document.getMetadata()) && Arrays.equals(testfile, stream.toByteArray());
	}

    
    /** *  Utility function to post a document using the Jersey client API.Test documents are held in src/test/resources in this project.Two files
    <I>name</I>.txt and <I>name</I>.json make up a single test document, 
 where the json file contains the metadata.
     * 
     * 
     * @param name Name of test document file (without extension)
     * @param workspace Path to workspace
     * @param ext file extension
     * @throws java.io.IOException
     * @return The result of posting the document to the test server.
     * 
     */
    public JsonObject postDocument(String name, String workspace, String ext) throws IOException {
    	WebTarget target;
        if (workspace == null) 
            target = client.target("http://localhost:" + port + "/docs/tmp");
    	else
            target = client.target("http://localhost:" + port + "/ws/tmp/~" + workspace);
    	
        MultiPart multiPart = new MultiPart();
        multiPart.setMediaType(MediaType.MULTIPART_FORM_DATA_TYPE);
        
        MediaType type = mediaTypesByExtension.get(ext);
        
        StreamDataBodyPart file = new StreamDataBodyPart(
        	"file",
            getTestFile("/"+name+"."+ext),
            null,
            type);

        FormDataBodyPart metadata = new FormDataBodyPart(
            	"metadata",
                 getTestMetadata("/"+name+".json"),
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
             getTestFile("/"+name+"."+ext),
             null,
             mediaTypesByExtension.get(ext));

        FormDataBodyPart metadata = new FormDataBodyPart(
            	"metadata",
                 getTestMetadata("/"+name+".json"),
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
    
    /** 
     *  Utility function to put a document using the Jersey client API.
     * 
     * Test documents are held in src/test/resources in this project.
     * 
     * Two files <I>name</I>.txt and <I>name</I>.json make up a single test document, 
     * where the json file contains the metadata.
     * 
     * @param path Path of document to update (including base, so /docs/tmp/id or /ws/tmp/workspace/docName)
     * @param id Id of document to link to
     * @param type UpdateType parameter
     * @return The result of posting the document link to the test server.
     * @throws java.io.IOException
     * 
     */
    public JsonObject postDocumentLink(String path, String id, UpdateType type) throws IOException {
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
                .post(Entity.entity(link, MediaType.APPLICATION_JSON_TYPE));
        
        if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {
            System.out.println(response.toString());
            throw new RuntimeException("Bad put");
        }
        
        return response.readEntity(JsonObject.class);
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
    public StreamableRepositoryObjectImpl getDocument(String id) throws IOException, ParseException {
		
    	WebTarget target = client.target("http://localhost:" + port + "/docs/tmp/" + id);

    	return getDocumentFromTarget(target);
    } 
    

    /** Utility function to get a document from the local test server
     * 
     * @param id The id of the document to get
     * @param operation The operation to perform
     * @param resultType the type of result to return
     * @return The document if it exists
     * @throws IOException In the case of low-level IO error
     * @throws ParseException If response cannot be parsed
     */
    public <T extends JsonValue> T getDocumentJson(String id, String operation, Class<T> resultType) throws IOException, ParseException {
        
        WebTarget target = client.target("http://localhost:" + port + "/docs/tmp/" + id +"/" + operation);

        Response response = target
                .request(MediaType.APPLICATION_JSON)
                .cookie(cookieHandler.generateCookie("test_user"))
                .get();
            
        if (response.getStatus() == Response.Status.OK.getStatusCode()) {
            return response.readEntity(resultType);
        } else {
            throw new RuntimeException(String.format("Bad get (%s) returns %s", response.getStatusInfo().getReasonPhrase(), response.readEntity(String.class)));
        }

    } 
    
    
    /** Utility function to get a document from the local test server
     * 
     * @param path The workspace path of the document to get
     * @return The document if it exists
     * @throws IOException In the case of low-level IO error
     * @throws ParseException If response cannot be parsed
     */
    public StreamableRepositoryObjectImpl getDocumentFromWorkspace(String path) throws IOException, ParseException {
        
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
    public StreamableRepositoryObjectImpl getDocumentFromTarget(WebTarget target) throws IOException, ParseException {
        
        Response response = target
                .request(MediaType.MULTIPART_FORM_DATA)
                .cookie(cookieHandler.generateCookie("test_user"))
                .get();
        
        JsonObject metadata = null;
        JsonObject data = null;
        InputStream is = null;
        MediaType mediaType = null;
        Reference reference = null;
        
        if (response.getStatus() == Response.Status.OK.getStatusCode()) {
            MultiPart entity = response.readEntity(MultiPart.class);
            for (BodyPart part : entity.getBodyParts()) {
                String cd = part.getHeaders().getFirst("Content-Disposition");
                String name = new FormDataContentDisposition(cd).getName();
                if (name.equals("metadata")) {
                    data = part.getEntityAs(JsonObject.class);
                    metadata = data.getJsonObject("metadata");
                }
                if (name.equals("file")) {
                    mediaType = part.getMediaType();
                    is = part.getEntityAs(InputStream.class);
                }
            }
            if (metadata != null && is != null) {
                InputStream doc_source = is;
                if (data.getString("type").equals(RepositoryObject.Type.STREAMABLE_DOCUMENT_PART.toString()))
                    return new StreamableDocumentPartImpl(null, QualifiedName.parse(data.getString("name"),"/"), mediaType, ()->doc_source, metadata);                    
                else
                    return new DocumentImpl(new Reference(data.getString("id"), data.getString("version")), mediaType, ()->doc_source, metadata);
            }
        } 

        throw new RuntimeException("Bad status " + response.getStatusInfo().getReasonPhrase());
    } 
    
    /** Utility function to get a document from the local test server
     * 
     * @param target The target url
     * @return The document if it exists
     * @throws IOException In the case of low-level IO error
     * @throws ParseException If response cannot be parsed
     */
    public org.w3c.dom.Document getXMLFromTarget(MediaType type, WebTarget target) throws IOException, ParseException {
        
        Response response = target
                .request(type)
                .cookie(cookieHandler.generateCookie("test_user"))
                .get();
        
        if (response.getStatus() == Response.Status.OK.getStatusCode()) {
        	return response.readEntity(org.w3c.dom.Document.class);
        } 

        throw new RuntimeException(String.format("Bad get (%s) returns %s", response.getStatusInfo().getReasonPhrase(), response.readEntity(String.class)));
    } 

    /** Utility function to get a document from the local test server
     * 
     * @param id The id of the document to get
     * @return The document if it exists
     * @throws IOException In the case of low-level IO error
     * @throws ParseException If response cannot be parsed
     */
    public org.w3c.dom.Document getXMLDocument(MediaType type, String id) throws IOException, ParseException {
    	//WebTarget target = client.target("http://localhost:" + port + "/docs/tmp/" + id + "/xhtml");
    	WebTarget target = client.target("http://localhost:" + port + "/docs/tmp/" + id + "/file");
    	return getXMLFromTarget(type, target);
    } 
    
    /** Utility function to get a document from the local test server
     * 
     * @param path The path of the document to get
     * @return The document if it exists
     * @throws IOException In the case of low-level IO error
     * @throws ParseException If response cannot be parsed
     */
    public org.w3c.dom.Document getXMLDocumentFromWorkspace(MediaType type, String path) throws IOException, ParseException {
    	WebTarget target = client.target("http://localhost:" + port + "/ws/tmp/" + path);
    	return getXMLFromTarget(type, target);
    } 
    
    public static void printDocument(Node doc) throws IOException, TransformerException {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
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

        throw new RuntimeException(String.format("Bad get (%s) returns %s", response.getStatusInfo().getReasonPhrase(), response.readEntity(String.class)));
    }
    
    public void clear() {
       	WebTarget target = client.target("http://localhost:" + port + "/admin/temp/tmp/clear" );

    	Response response = target
    			.request(MediaType.APPLICATION_JSON)
    			.get();
    	
		if (!(response.getStatus() == Response.Status.OK.getStatusCode())) {
            throw new RuntimeException(String.format("Bad get (%s) returns %s", response.getStatusInfo().getReasonPhrase(), response.readEntity(String.class)));
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
    	
        InputStream file = getTestFile("/test1.txt");

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
    
    /** Test that posting a test file returns a non-null ID.
     * 
     * This tests that no cookie is required when using dummy authentication for a
     * repository.
     */
	@Test
	public void postFileTestDummyAuth() throws IllegalStateException, IOException {

    	WebTarget target = client.target("http://localhost:" + port + "/docs/dummy/file");
    	
        InputStream file = getTestFile("/test1.txt");

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

		JsonObject response = postDocument("test1", null, "txt");
		
		String id = response.getString("id");
		
		assertNotNull(id);
		
		StreamableRepositoryObjectImpl doc = getDocument(id);
		
		assertNotNull(doc);
		
		assertTrue(docEquals("test1", doc));
		
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
        Document doc = (Document)getDocumentFromWorkspace("wsname/doc1");
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
        Document doc = (Document)getDocumentFromWorkspace("anotherws/myDoc");
        assertEquals(wsId, doc.getId());
    }
    
    @Test
    public void testCreateDocumentLinkGeneratesName() throws IOException, ParseException {
        JsonObject response1 = postDocument("anon", null, "txt");
        String docId = response1.getString("id");
        assertNotNull(docId);
        JsonObject response2 = postDocumentLink("/ws/tmp/anotherws", docId, UpdateType.CREATE);
        String docName = response2.getString("name");
        Document doc = (Document)getDocumentFromWorkspace(docName);
        assertEquals(docId, doc.getId());
    }

    @Test
    public void testWildcardWithId() throws IOException, ParseException {
        JsonObject response1 = putDocument("test2", "/ws/tmp/wsname1/doc1", "txt");
        String wsId = response1.getString("id");
        putDocumentLink("/ws/tmp/anotherws1/myDoc", wsId, UpdateType.CREATE);
        JsonArray result = getWorkspaceJson("/*/~"+wsId, JsonArray.class);
        assertEquals(2, result.size());
    }
    
    @Test
    public void testServiceAccountLogin() throws InitializationFailure, InvalidKeyException, NoSuchAlgorithmException, NoSuchProviderException, BadKeyException, SignatureException {
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
            .target("http://localhost:" + port + "/auth/tmp/service")
            .queryParam("request", Base64.getUrlEncoder().encodeToString(requestBytes))
            .queryParam("signature", Base64.getUrlEncoder().encodeToString(signatureBytes));
  
        Response response = target.request().get();
            
        if (response.getStatus() == Response.Status.OK.getStatusCode()) {
            NewCookie cookie = response.getCookies().get("DoctaneUserToken/tmp");
            assertNotNull(cookie);
        } else {
            System.out.println(response.toString());
            throw new RuntimeException("Bad service auth request");
        }
    }
    
    @Test
    public void testSignonServiceRedirect() {
        WebTarget target = client
            .property(ClientProperties.FOLLOW_REDIRECTS, Boolean.FALSE)
            .target("http://localhost:" + port + "/auth/tmp/signon?relayState=abcdef");
  
        Response response = target.request().get();
            
        assertEquals(Response.Status.SEE_OTHER.getStatusCode(), response.getStatus());
        URI location = response.getLocation();
        //These values come from the services.xml config
        assertEquals("auth.softwareplumbers.com", location.getHost());
        System.out.println (location.getQuery());
        assertTrue(location.getQuery().contains("elayState=abcdef"));
    }

    @Test
    public void testGetWordDocumentXML() throws IOException, ParseException, TransformerException {
		JsonObject response1 = postDocument("testdoc", null, "docx");
		String id = response1.getString("id");
		org.w3c.dom.Document xmlDoc = getXMLDocument(MediaType.APPLICATION_XHTML_XML_TYPE, id);
		NodeList h1s = xmlDoc.getElementsByTagName("h1");
		assertEquals(1, h1s.getLength());
		NodeList tds = xmlDoc.getElementsByTagName("td");
		assertEquals(4, tds.getLength());
    }
    
    @Test
    public void testGetOutlook2010DocumentXML() throws IOException, ParseException, TransformerException {
		JsonObject response1 = postDocument("testdoc_outlook2010", null, "msg");
		String id = response1.getString("id");
		org.w3c.dom.Document xmlDoc = getXMLDocument(MediaType.APPLICATION_XHTML_XML_TYPE, id);
		assertNotNull(xmlDoc);
        NodeList h1s = xmlDoc.getElementsByTagName("h1");
        assertEquals(1, h1s.getLength());
        assertEquals("Microsoft Outlook Test Message", h1s.item(0).getTextContent());
    }
    
    @Test
    public void testGetWord2004DocumentXML() throws IOException, ParseException, TransformerException {
		JsonObject response1 = postDocument("testdoc_word2004", null, "doc");
		String id = response1.getString("id");
		org.w3c.dom.Document xmlDoc = getXMLDocument(MediaType.APPLICATION_XHTML_XML_TYPE, id);
		assertNotNull(xmlDoc);
 		NodeList h1s = xmlDoc.getElementsByTagName("h1");
		assertEquals(1, h1s.getLength());
		NodeList tds = xmlDoc.getElementsByTagName("td");
		assertEquals(4, tds.getLength());
    }

    @Test
    public void testGetDocumentXMLFromWorkspace() throws IOException, ParseException, TransformerException {
        JsonObject response1 = putDocument("testdoc", "/ws/tmp/wsname/doc1", "docx");
        assertNotNull(response1);
        StreamableRepositoryObjectImpl doc = getDocumentFromWorkspace("wsname/doc1");
        assertNotNull(doc);
        org.w3c.dom.Document xmlDoc = getXMLDocumentFromWorkspace(MediaType.APPLICATION_XHTML_XML_TYPE, "wsname/doc1");
        NodeList h1s = xmlDoc.getElementsByTagName("h1");
        assertEquals(1, h1s.getLength());
        NodeList tds = xmlDoc.getElementsByTagName("td");
        assertEquals(4, tds.getLength());
    }

    @Test
    public void testListWorkspaces() throws IOException, ParseException {
        JsonObject response1 = putDocument("test2", "/ws/tmp/wsname2/doc2", "txt");
        String docId = response1.getString("id");
        putDocumentLink("/ws/tmp/anotherws2/myDoc", docId, UpdateType.CREATE);
        JsonArray result = getDocumentJson(docId, "workspaces", JsonArray.class);
        assertEquals(2, result.size());
    }
    
    @Test
    public void testGetDocumentXMLFromWorkspaceWithContentType() throws IOException, ParseException, TransformerException {
        JsonObject response1 = putDocument("testdoc", "/ws/tmp/wsname/doc4", "docx");
        assertNotNull(response1);
        org.w3c.dom.Document xmlDoc = getXMLDocumentFromWorkspace(MediaType.WILDCARD_TYPE, "wsname/doc4?contentType=" + URLEncoder.encode(MediaType.APPLICATION_XHTML_XML, "UTF-8"));
        NodeList h1s = xmlDoc.getElementsByTagName("h1");
        assertEquals(1, h1s.getLength());
        NodeList tds = xmlDoc.getElementsByTagName("td");
        assertEquals(4, tds.getLength());
    }
    
    @Test
    public void testGetEMLDocumentXMLFromWorkspaceWithContentType() throws IOException, ParseException, TransformerException {
        JsonObject response1 = putDocument("testEmail", "/ws/tmp/wsname/doc5", "eml");
        assertNotNull(response1);
        org.w3c.dom.Document xmlDoc = getXMLDocumentFromWorkspace(MediaType.WILDCARD_TYPE, "wsname/doc5?contentType=" + URLEncoder.encode(MediaType.APPLICATION_XHTML_XML, "UTF-8"));
        NodeList tds = xmlDoc.getElementsByTagName("td");
        assertEquals(27, tds.getLength());
    }
    
    @Test
    public void testCreateDocumentLinkWithPost() throws IOException, ParseException {
        JsonObject response1 = putDocument("test2", "/ws/tmp/wsname/doc1", "txt");
        String wsId = response1.getString("id");
        assertNotNull(wsId);
        JsonObject response2 = postDocumentLink("/ws/tmp/anotherws2", wsId, UpdateType.CREATE);
        QualifiedName name = QualifiedName.parse(response2.getString("name"),"/");
        Document doc = (Document)getDocumentFromWorkspace("anotherws2/" + name.part);
        assertEquals(wsId, doc.getId());
    }
    
    @Test
    public void testGetZipFilePart() throws IOException, ParseException {
        JsonObject response1 = putDocument("testzipdir", "/ws/tmp/wsname3/testzip", "zip");
        StreamableRepositoryObjectImpl doc = getDocumentFromWorkspace("/wsname3/testzip/~/test/test1.txt");
        byte[] original = IOUtils.resourceToByteArray("/test1.txt");
        byte[] unzipped = IOUtils.toByteArray(doc.getData());
        assertArrayEquals(original, unzipped);   
    }
    
    @Test
    public void testGetZipFileParts() throws IOException, ParseException {
        JsonObject response1 = putDocument("testzipdir", "/ws/tmp/wsname3/testzip", "zip");
        JsonArray result = getWorkspaceJson("/wsname3/testzip/~/test/*", JsonArray.class);
        assertEquals(3, result.size());   
        JsonArray result2 = getWorkspaceJson("/wsname3/testzip/~/test/subdir/*", JsonArray.class);
        assertEquals(1, result2.size());   
    }
    
    @Test
    public void testGetZipFileDirectoryPart() throws IOException, ParseException {
        JsonObject response1 = putDocument("testzipdir", "/ws/tmp/wsname3/testzip", "zip");
        JsonObject dir = getWorkspaceJson("/wsname3/testzip/~/test/subdir", JsonObject.class);
        assertEquals(RepositoryObject.Type.DOCUMENT_PART.toString(), dir.getString("type"));   
        assertEquals(true, dir.getBoolean("navigable"));   
    }

}

