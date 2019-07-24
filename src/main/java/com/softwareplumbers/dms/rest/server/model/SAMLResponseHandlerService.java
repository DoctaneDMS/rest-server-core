/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.dms.rest.server.core.Authentication;
import com.softwareplumbers.dms.rest.server.util.Log;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Base64;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.ResolverException;
import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.Unmarshaller;
import org.opensaml.core.xml.io.UnmarshallerFactory;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.saml.criterion.EntityRoleCriterion;
import org.opensaml.saml.metadata.resolver.impl.FilesystemMetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.PredicateRoleDescriptorResolver;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.security.impl.MetadataCredentialResolver;
import org.opensaml.saml.security.impl.SAMLSignatureProfileValidator;
import org.opensaml.security.credential.Credential;
import org.opensaml.xmlsec.config.DefaultSecurityConfigurationBootstrap;
import org.opensaml.xmlsec.keyinfo.KeyInfoCredentialResolver;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.opensaml.xmlsec.signature.support.SignatureValidator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/** Encapsulate handling of a SAML Response
 * 
 * An instance of this class is created for each SAML identity provider in order to verify SAML responses. 
 *
 * @author jonathan.local
 */
public class SAMLResponseHandlerService {
    
    public static class SAMLParsingError extends Exception {
        public SAMLParsingError(String msg, Exception cause) {
            super(msg, cause);
        }
    }
    
    private static final Log LOG = new Log(SAMLResponseHandlerService.class);
    
    private final Credential idpCredential;
    private final UnmarshallerFactory unmarshallerFactory;
    private final DocumentBuilderFactory documentBuilderFactory;
    
    public SAMLResponseHandlerService(String entityId) throws ComponentInitializationException, ResolverException, InitializationException {
        LOG.logEntering("<constructor>", entityId);
        InitializationService.initialize();
        idpCredential = getIDPCredential(entityId);
        documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        unmarshallerFactory = XMLObjectProviderRegistrySupport.getUnmarshallerFactory();
        LOG.logExiting("<constructor>");
    }
    
    public SAMLResponseHandlerService() throws ComponentInitializationException, ResolverException, InitializationException {
        this("https://auth.softwareplumbers.com/auth/realms/doctane-test");
    }

    
    /** Get the SAML2 nameId from a SAML response
     * @param samlResponse
     * @return the name of the principal encoded in the SAML response
     */
    public static String getName(org.opensaml.saml.saml2.core.Response samlResponse) {
        Assertion assertion = samlResponse.getAssertions().get(0);
        return assertion.getSubject().getNameID().getValue();
    }
    
    
    /** Determines the Credential used to validate signatures from the SAML2 IDP 
     * 
     * @param entityId
     * @return the Credential used to validate signatures from the SAML2 IDP
     * @throws ComponentInitializationException
     * @throws ResolverException 
     */
    public Credential getIDPCredential(String entityId) throws ComponentInitializationException, ResolverException {
        try {
            URL metadata = Authentication.class.getResource("/idp-metadata.xml");
            FilesystemMetadataResolver idpMetadataResolver = new FilesystemMetadataResolver(new File(metadata.toURI()));
            idpMetadataResolver.setRequireValidMetadata(true);
            idpMetadataResolver.setParserPool(XMLObjectProviderRegistrySupport.getParserPool());
            idpMetadataResolver.setId(entityId);
            idpMetadataResolver.initialize();
    
            KeyInfoCredentialResolver keyResolver = DefaultSecurityConfigurationBootstrap.buildBasicInlineKeyInfoCredentialResolver();
            
            PredicateRoleDescriptorResolver predResolver = new PredicateRoleDescriptorResolver(idpMetadataResolver);
            predResolver.initialize();
            
            MetadataCredentialResolver credentialResolver = new MetadataCredentialResolver();
            credentialResolver.setRoleDescriptorResolver(predResolver);
            credentialResolver.setKeyInfoCredentialResolver(keyResolver);
            credentialResolver.initialize();
            CriteriaSet criteriaSet = new CriteriaSet();
            criteriaSet.add(new EntityIdCriterion(entityId));
            criteriaSet.add(new EntityRoleCriterion(IDPSSODescriptor.DEFAULT_ELEMENT_NAME));
            return credentialResolver.resolveSingle(criteriaSet);
        } catch (URISyntaxException exp) {
            throw new RuntimeException(exp);
        }
    }
    
    public boolean validateSignature(Response response) {
        SAMLSignatureProfileValidator profileValidator = new SAMLSignatureProfileValidator();
        try {
            org.opensaml.xmlsec.signature.Signature signature = response.getSignature();
            if (signature == null) return false;
            profileValidator.validate(signature);
            SignatureValidator.validate(signature, idpCredential);
        } catch (SignatureException exp) {
            return false;
        }       
        return true;
    }
    
    public boolean hasDocumentViewerRole(Response response) {
        //TODO: implement
        return true;
    }
    
    public Response parseSamlResponse(String samlResponse) throws SAMLParsingError {
        try {
            ByteArrayInputStream is = new ByteArrayInputStream(Base64.getDecoder().decode(samlResponse));
            DocumentBuilder docBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = docBuilder.parse(is);

            Element element = document.getDocumentElement();
            if (element == null) throw new RuntimeException("Malformed SAML Response");
            Unmarshaller unmarshaller = unmarshallerFactory.getUnmarshaller(element);
            if (unmarshaller == null) throw new RuntimeException("Can't create XML unmarshaller");
            XMLObject responseXmlObj = unmarshaller.unmarshall(element);
            return (Response)responseXmlObj;
        } catch (ParserConfigurationException | SAXException | IOException | UnmarshallingException e) {
            throw new SAMLParsingError("Could not parse SAML response", e);
        } 
    }
}
