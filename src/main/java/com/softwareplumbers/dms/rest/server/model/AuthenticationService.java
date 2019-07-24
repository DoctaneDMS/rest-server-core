/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.model;

/**
 *
 * @author jonathan.local
 */
public class AuthenticationService {
    
    private RequestValidationService requestValidationService;
    private SignonService signonService;
    private SAMLResponseHandlerService samlResponseHandlerService;
    private SignedRequestValidationService signedRequestValidationService;
    
    public RequestValidationService getRequestValidationService() {
        return requestValidationService;
    }
    
    public void setRequestValidationService(RequestValidationService requestValidationService) {
        this.requestValidationService = requestValidationService;
    }
    
    public SignonService getSignonService() {
        return signonService;
    }
    
    public void setSignonService(SignonService signonService) {
        this.signonService = signonService;
    }
    
    public SAMLResponseHandlerService getSAMLResponseHandlerService() {
        return samlResponseHandlerService;
    }
    
    public void setSAMLResponseHandlerService(SAMLResponseHandlerService samlResponseHandlerService) {
        this.samlResponseHandlerService = samlResponseHandlerService;
    }
    
    public SignedRequestValidationService getSignedRequestValidationService() {
        return signedRequestValidationService;
    }
    
    public void setSignedRequestValidationService(SignedRequestValidationService signedRequestValidationService) {
        this.signedRequestValidationService = signedRequestValidationService;
    }
}
