/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.rest.server.core;

import com.softwareplumbers.dms.rest.server.model.SAMLProtocolHandlerService;
import com.softwareplumbers.dms.rest.server.model.SAMLProtocolHandlerService.SAMLOutputError;
import com.softwareplumbers.dms.rest.server.model.SignonService;
import java.util.Optional;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import org.springframework.beans.factory.annotation.Required;


/** Redirects caller to a sign-on service using a simple URI template.
 *
 * @author jonathan.local
 */
public class SAMLSignonService implements SignonService {
    
    SAMLProtocolHandlerService samlService;
    String assertionConsumerURI;
    Optional<String> issuerId;
            
    public SAMLSignonService(SAMLProtocolHandlerService samlService, String assertionConsumerURI, Optional<String> issuerId) {
        this.samlService = samlService;
        this.assertionConsumerURI = assertionConsumerURI;
        this.issuerId = issuerId;
    }
    
    public SAMLSignonService() {
        samlService = null;
        assertionConsumerURI = null;
        issuerId = Optional.empty();
    }
    
    @Required
    public void setSAMLProtocolHandlerService(SAMLProtocolHandlerService samlService) {
        this.samlService = samlService;
    }

    @Required
    public void setAssertionConsumerURI(String assertionConsumerURI) {
        this.assertionConsumerURI = assertionConsumerURI;
    }
    
    public void setIssuerId(String issuerId) {
        this.issuerId = Optional.of(issuerId);
    }
    
    @Override
    public Response redirect(String relayState) {
        
        try {
            UriBuilder builder = UriBuilder
                .fromUri(samlService.getIDPEndpoint())
                .queryParam("SAMLRequest", samlService.formatRequest(assertionConsumerURI, issuerId))
                .queryParam("RelayState", relayState);
            return Response.seeOther(builder.build()).build();
        } catch (SAMLOutputError err) {
            throw new RuntimeException(err);
        }
    }
    
}
