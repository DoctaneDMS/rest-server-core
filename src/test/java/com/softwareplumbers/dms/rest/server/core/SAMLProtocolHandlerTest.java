package com.softwareplumbers.dms.rest.server.core;

import com.softwareplumbers.dms.rest.server.model.SAMLProtocolHandlerService;
import com.softwareplumbers.dms.rest.server.model.SAMLProtocolHandlerService.SAMLInitialisationError;
import com.softwareplumbers.dms.rest.server.model.SAMLProtocolHandlerService.SAMLOutputError;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Base64;
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
        assertEquals("https://auth.softwareplumbers.com/auth/realms/doctane-test/protocol/saml/clients/doctaneAPILocal",samlHandler.getIDPEndpoint());
    }
    
    @Test 
    public void testGetCredential() throws SAMLInitialisationError {
        SAMLProtocolHandlerService samlHandler = new SAMLProtocolHandlerService();
        assertEquals("https://auth.softwareplumbers.com/auth/realms/doctane-test",samlHandler.getIDPCredential().getEntityId());        
    }
    
    @Test 
    public void testFormatRequest() throws SAMLInitialisationError, SAMLOutputError, IOException {
        SAMLProtocolHandlerService samlHandler = new SAMLProtocolHandlerService();
        String response = samlHandler.formatRequest("https://api.doctane.com/auth/tmp/saml");
        InputStream is = new InflaterInputStream(Base64.getUrlDecoder().wrap(new ByteArrayInputStream(response.getBytes())));
        String decoded = IOUtils.toString(is, Charset.defaultCharset());
        System.out.println(decoded);
        assertTrue("response has correct ACS URI", decoded.contains("AssertionConsumerServiceURL=\"https://api.doctane.com/auth/tmp/saml\""));
    }


}