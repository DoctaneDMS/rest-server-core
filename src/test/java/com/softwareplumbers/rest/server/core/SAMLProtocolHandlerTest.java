package com.softwareplumbers.rest.server.core;

import com.softwareplumbers.rest.server.model.SAMLProtocolHandlerService;
import com.softwareplumbers.rest.server.model.SAMLProtocolHandlerService.SAMLInitialisationError;
import com.softwareplumbers.rest.server.model.SAMLProtocolHandlerService.SAMLOutputError;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Optional;
import java.util.zip.InflaterInputStream;
import org.apache.commons.io.IOUtils;
import static org.junit.Assert.assertTrue;

public class SAMLProtocolHandlerTest {
    
    @Test
    public void testGetEntityId() throws SAMLInitialisationError {
        
        SAMLProtocolHandlerService samlHandler = new SAMLProtocolHandlerService();
        assertEquals("https://auth.softwareplumbers.com/auth/realms/doctane-test",samlHandler.getEntityId());
    }

    @Test
    public void testGetIDPEndpoint() throws SAMLInitialisationError {       
        SAMLProtocolHandlerService samlHandler = new SAMLProtocolHandlerService();
        assertEquals("https://auth.softwareplumbers.com/auth/realms/doctane-test/protocol/saml",samlHandler.getIDPEndpoint());
    }
    
    @Test 
    public void testGetCredential() throws SAMLInitialisationError {
        SAMLProtocolHandlerService samlHandler = new SAMLProtocolHandlerService();
        assertEquals("https://auth.softwareplumbers.com/auth/realms/doctane-test",samlHandler.getIDPCredential().getEntityId());        
    }
    
    @Test 
    public void testFormatRequest() throws SAMLInitialisationError, SAMLOutputError, IOException {
        SAMLProtocolHandlerService samlHandler = new SAMLProtocolHandlerService();
        String response = samlHandler.formatRequest("https://api.doctane.com/auth/tmp/saml", Optional.of("doctane-api-saml2"));
        System.out.println(response);
        InputStream is = SAMLProtocolHandlerService.decode(new ByteArrayInputStream(response.getBytes()));
        String decoded = IOUtils.toString(is, Charset.defaultCharset());
        System.out.println(decoded);
        assertTrue("response has correct ACS URI", decoded.contains("AssertionConsumerServiceURL=\"https://api.doctane.com/auth/tmp/saml\""));
    }
    
    @Test
    public void testEncode() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        OutputStream encodingStream = SAMLProtocolHandlerService.encode(out);
        encodingStream.write("peter piper picked a peck of pickled peppers".getBytes());
        encodingStream.close();
        assertEquals("K0gtSS1SKMgsAJPJ2akpCokKBanJ2Qr5aWCBHKBIQWoBUL4YAA==", out.toString());
    }


}