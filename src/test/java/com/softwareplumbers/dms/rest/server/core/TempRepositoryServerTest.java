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
import java.util.Map;

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

import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.RepositoryBrowser;
import com.softwareplumbers.dms.StreamableRepositoryObject;
import com.softwareplumbers.dms.common.impl.RepositoryObjectFactory;
import com.softwareplumbers.dms.rest.server.model.UpdateType;
import com.softwareplumbers.dms.service.sql.Schema;
import com.softwareplumbers.keymanager.BadKeyException;
import com.softwareplumbers.keymanager.InitializationFailure;
import com.softwareplumbers.keymanager.KeyManager;
import java.net.URI;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.util.Arrays;
import javax.json.JsonReader;
import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.Before;

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

    class HttpError extends RuntimeException {
        public final int code;
        public HttpError(int code) { super("HTTP Error " + code); this.code = code; }
    }
    
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
    @Autowired
    Schema schema;
    
    @Before
    public void clear() throws SQLException {
        schema.dropSchema();
        schema.createSchema();
        schema.updateSchema();
    }
    
    private static InputStream getTestFile(String name) {
        return TempRepositoryServerTest.class.getResourceAsStream(name);
    }
    
    private static JsonObject getTestMetadata(String name) throws IOException {
		try (InputStream stream = getTestFile(name)) {
			JsonReader reader = Json.createReader(stream);
			return reader.readObject();
		}
	}

    private static boolean docEquals(String name, StreamableRepositoryObject document, RepositoryBrowser service) throws IOException {
		byte[] testfile = IOUtils.toByteArray(getTestFile("/" + name + ".txt"));
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		document.writeDocument(service, stream);
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
			throw new HttpError(response.getStatus());
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
        
        multiPart.bodyPart(metadata);
        multiPart.bodyPart(file);

    	Response response = target
    			.request(MediaType.APPLICATION_JSON)
                .cookie(cookieHandler.generateCookie("test_user"))
    			.put(Entity.entity(multiPart, multiPart.getMediaType()));
    	
			
		if (response.getStatus() == Response.Status.ACCEPTED.getStatusCode()) {
			return response.readEntity(JsonObject.class);
		} else {
			System.out.println(response.toString());
			throw new HttpError(response.getStatus());
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
			throw new HttpError(response.getStatus());
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
			throw new HttpError(response.getStatus());
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
    	WebTarget target = client.target("http://localhost:" + port + "/ws/tmp/" + path + "?createWorkspace=true");
    	
    	Response response = target
    			.request(MediaType.APPLICATION_JSON)
                .cookie(cookieHandler.generateCookie("test_user"))
    			.put(Entity.entity(data, MediaType.APPLICATION_JSON_TYPE));
			
		if (response.getStatus() == Response.Status.ACCEPTED.getStatusCode()) {
			return response.readEntity(JsonObject.class);
		} else {
			System.out.println(response.toString());
			throw new HttpError(response.getStatus());
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
			throw new HttpError(response.getStatus());
		}
    }


    
    /** Utility function to get a document from the local test server
     * 
     * @param id The id of the document to get
     * @return The document if it exists
     * @throws IOException In the case of low-level IO error
     * @throws ParseException If response cannot be parsed
     */
    public StreamableRepositoryObject getDocument(String id) throws IOException, ParseException {
		
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
            throw new HttpError(response.getStatus());
        }

    } 
    
    
    /** Utility function to get a document from the local test server
     * 
     * @param path The workspace path of the document to get
     * @return The document if it exists
     * @throws IOException In the case of low-level IO error
     * @throws ParseException If response cannot be parsed
     */
    public StreamableRepositoryObject getDocumentFromWorkspace(String path) throws IOException, ParseException {
        
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
    public StreamableRepositoryObject getDocumentFromTarget(WebTarget target) throws IOException, ParseException {
        
        Response response = target
                .request(MediaType.MULTIPART_FORM_DATA)
                .cookie(cookieHandler.generateCookie("test_user"))
                .get();
        
        JsonObject data = null;
        InputStream is = null;
        MediaType mediaType = null;
        
        if (response.getStatus() == Response.Status.OK.getStatusCode()) {
            MultiPart entity = response.readEntity(MultiPart.class);
            for (BodyPart part : entity.getBodyParts()) {
                String cd = part.getHeaders().getFirst("Content-Disposition");
                String name = new FormDataContentDisposition(cd).getName();
                if (name.equals("metadata")) {
                    data = part.getEntityAs(JsonObject.class);
                }
                if (name.equals("file")) {
                    is = part.getEntityAs(InputStream.class);
                    mediaType = part.getMediaType();
                }
            }
            if (data != null && is != null) {
                final InputStream doc_source = is;
                return RepositoryObjectFactory.getInstance().build(data, mediaType.toString(), ()->doc_source);
            }
        } 

        throw new HttpError(response.getStatus());
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
    
	private static Reference getRef(JsonValue value) {
	    JsonObject obj = (JsonObject)value;
	    return new Reference(obj.getString("id", null), obj.getString("version", null));
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
        StreamableRepositoryObject doc = getDocumentFromWorkspace("wsname/doc1");
        assertNotNull(doc);
        org.w3c.dom.Document xmlDoc = getXMLDocumentFromWorkspace(MediaType.APPLICATION_XHTML_XML_TYPE, "wsname/doc1");
        NodeList h1s = xmlDoc.getElementsByTagName("h1");
        assertEquals(1, h1s.getLength());
        NodeList tds = xmlDoc.getElementsByTagName("td");
        assertEquals(4, tds.getLength());
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

    
}

