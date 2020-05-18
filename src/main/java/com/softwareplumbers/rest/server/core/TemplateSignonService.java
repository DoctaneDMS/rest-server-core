/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.rest.server.core;

import com.softwareplumbers.rest.server.model.SignonService;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

/** Redirects caller to a sign-on service using a simple URI template.
 *
 * @author jonathan.local
 */
public class TemplateSignonService implements SignonService {
    
    UriBuilder templateURI;
    
    public TemplateSignonService(String templateURI) {
        this.templateURI = UriBuilder.fromUri(templateURI);
    }

    @Override
    public Response redirect(String relayState) {
        return Response.seeOther(templateURI.build(relayState)).build();
    }
    
}
