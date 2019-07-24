/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.core;

import com.softwareplumbers.dms.rest.server.model.SAMLProtocolHandlerService;
import com.softwareplumbers.dms.rest.server.model.SAMLProtocolHandlerService.SAMLOutputError;
import com.softwareplumbers.dms.rest.server.model.SignonService;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;


/** Redirects caller to a sign-on service using a simple URI template.
 *
 * @author jonathan.local
 */
public class SAMLSignonService implements SignonService {
    
    public SAMLProtocolHandlerService samlService;
    public String assertionConsumerURI;
            
    public SAMLSignonService(SAMLProtocolHandlerService samlService, String assertionConsumerURI) {
        this.samlService = samlService;
        this.assertionConsumerURI = assertionConsumerURI;
    }

    @Override
    public Response redirect(String relayState) {
        
        try {
            UriBuilder builder = UriBuilder
                .fromUri(samlService.getIDPEndpoint())
                .queryParam("SamlRequest", samlService.formatRequest(assertionConsumerURI))
                .queryParam("RelayState", relayState);
            return Response.seeOther(builder.build()).build();
        } catch (SAMLOutputError err) {
            throw new RuntimeException(err);
        }
    }
    
}
