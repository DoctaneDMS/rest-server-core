/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.rest.server.model;

import com.softwareplumbers.rest.server.util.IdioticShibbolethSpringResourceBridge;
import org.slf4j.ext.XLogger;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.ResolverException;
import org.joda.time.DateTime;
import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.Marshaller;
import org.opensaml.core.xml.io.MarshallerFactory;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.core.xml.io.Unmarshaller;
import org.opensaml.core.xml.io.UnmarshallerFactory;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.saml.criterion.EntityRoleCriterion;
import org.opensaml.saml.metadata.resolver.MetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.FilesystemMetadataResolver;
import org.opensaml.saml.metadata.resolver.impl.PredicateRoleDescriptorResolver;
import org.opensaml.saml.metadata.resolver.impl.ResourceBackedMetadataResolver;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.NameIDPolicy;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.impl.AuthnRequestBuilder;
import org.opensaml.saml.saml2.core.impl.IssuerBuilder;
import org.opensaml.saml.saml2.core.impl.NameIDPolicyBuilder;
import org.opensaml.saml.saml2.metadata.Endpoint;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.security.impl.MetadataCredentialResolver;
import org.opensaml.saml.security.impl.SAMLSignatureProfileValidator;
import org.opensaml.security.credential.Credential;
import org.opensaml.xmlsec.config.DefaultSecurityConfigurationBootstrap;
import org.opensaml.xmlsec.keyinfo.KeyInfoCredentialResolver;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.opensaml.xmlsec.signature.support.SignatureValidator;
import org.slf4j.ext.XLoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/** Encapsulate handling of a SAML Response
 * 
 * An instance of this class is created for each SAML identity provider in order to verify SAML responses. 
 *
 * @author jonathan.local
 */
public class SAMLProtocolHandlerService {
    
    public static final String SAML2_PROTOCOL = "urn:oasis:names:tc:SAML:2.0:protocol";
    public static final String SAML2_NAMEID_POLICY = "urn:oasis:names:tc:SAML:2.0:nameid-format:transient";
    public static final String SAML2_POST_BINDING = "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST";
    public static final String SAML2_SSO_SERVICE = "SingleSignOnService";

    
    public static class SAMLParsingError extends Exception {
        public SAMLParsingError(String msg, Exception cause) {
            super(msg, cause);
        }
    }
    
    public static class SAMLOutputError extends Exception {
        public SAMLOutputError(String msg, Exception cause) {
            super(msg, cause);
        }
        public SAMLOutputError(String msg) {
            super(msg);
        }
    }
    
    
    public static class SAMLInitialisationError extends Exception {
        public SAMLInitialisationError(String msg, Exception cause) {
            super(msg, cause);
        }
        public SAMLInitialisationError(String msg) {
            super(msg);
        }
    }
    
    private static final XLogger LOG = XLoggerFactory.getXLogger(SAMLProtocolHandlerService.class);
    
    private final UnmarshallerFactory unmarshallerFactory;
    private final MarshallerFactory marshallerFactory;
    private final DocumentBuilderFactory documentBuilderFactory;
    private String entityId;
    private Resource metadataResource;
    
    private static class ProviderData {
        public final MetadataResolver idpMetadataResolver;
        public final Credential idpCredential;
        public final String idpEndpoint;
        
        public ProviderData(String entityId, Resource metadataResource) throws IOException, SAMLInitialisationError {
            LOG.entry(entityId, metadataResource);
            idpMetadataResolver = initialiseMetadataResolver(metadataResource, entityId);
            idpCredential = getIDPCredential(idpMetadataResolver, entityId);
            idpEndpoint = getIDPEndpoint(idpMetadataResolver, entityId).orElseThrow(()->new SAMLInitialisationError("can't locate endpoint"));     
            LOG.exit();
        }
    }
    
    private ProviderData providerData = null;

    private final synchronized ProviderData getProviderData() {
        LOG.entry();
        if (this.providerData == null) {
            try {
                this.providerData = new ProviderData(entityId, metadataResource);
            } catch (IOException | SAMLInitialisationError e) {
                throw new RuntimeException(e);
            }
        }
        return LOG.exit(providerData);
    }
    
    public SAMLProtocolHandlerService(String entityId, Resource metadataResource) throws SAMLInitialisationError {
        LOG.entry(entityId, metadataResource);        
        try {
            InitializationService.initialize();        
            this.entityId = entityId;
            this.metadataResource = metadataResource;
            documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setNamespaceAware(true);
            unmarshallerFactory = XMLObjectProviderRegistrySupport.getUnmarshallerFactory();
            marshallerFactory = XMLObjectProviderRegistrySupport.getMarshallerFactory();
        } catch (InitializationException e) {
            throw LOG.throwing(new SAMLInitialisationError("can't initialize SAML subsystem", e));
        }
        LOG.exit();
    }
    

    
    public SAMLProtocolHandlerService() throws SAMLInitialisationError {
        this("https://auth.softwareplumbers.com/auth/realms/doctane-test", new ClassPathResource("/config/idp-metadata.xml"));
    }
 
    /** Get the SAML2 nameId from a SAML response
     * @param samlResponse
     * @return the name of the principal encoded in the SAML response
     */
    public static String getName(org.opensaml.saml.saml2.core.Response samlResponse) {
        LOG.entry(samlResponse);
        Assertion assertion = samlResponse.getAssertions().get(0);
        return LOG.exit(assertion.getSubject().getNameID().getValue());
    }
    
    public static MetadataResolver initialiseMetadataResolver(Resource resource, String entityId) throws SAMLInitialisationError, IOException {
        LOG.entry(resource, entityId);
        try {
            ResourceBackedMetadataResolver idpMetadataResolver = new ResourceBackedMetadataResolver(new IdioticShibbolethSpringResourceBridge(resource));
            idpMetadataResolver.setRequireValidMetadata(true);
            idpMetadataResolver.setParserPool(XMLObjectProviderRegistrySupport.getParserPool());
            idpMetadataResolver.setId(entityId);
            idpMetadataResolver.initialize();
            return LOG.exit(idpMetadataResolver);
        } catch (ComponentInitializationException e) {
            throw LOG.throwing(new SAMLInitialisationError("Could not initialse SAML subsystem", e));
        }
    }
    
    public static boolean filterEndpoint(Endpoint endpoint) {
        QName elementName = endpoint.getElementQName();
        return endpoint.getElementQName().getLocalPart().equals(SAML2_SSO_SERVICE);
    }
    
    public static Optional<String> getIDPEndpoint(MetadataResolver idpMetadataResolver, String entityId) throws SAMLInitialisationError {
        LOG.entry(idpMetadataResolver, entityId);
        CriteriaSet criteriaSet = new CriteriaSet();
        criteriaSet.add(new EntityIdCriterion(entityId));
        try {
            EntityDescriptor entity = idpMetadataResolver.resolveSingle(criteriaSet);
            if (entity == null) throw new SAMLInitialisationError("could not find SAML entity " + entityId + " in SAML config file");
            IDPSSODescriptor sso = entity.getIDPSSODescriptor(SAML2_PROTOCOL);
            return LOG.exit(sso.getEndpoints()
                    .stream()
                    .filter(SAMLProtocolHandlerService::filterEndpoint)
                    .map(endpoint->endpoint.getLocation())
                    .findAny()
            );
        } catch (ResolverException e) {
            throw LOG.throwing(new SAMLInitialisationError("could not initialse SAML subsystem", e));
        }
    }
    
    /** Determines the Credential used to validate signatures from the SAML2 IDP 
     * 
     * @param idpMetadataResolver resolver to use to find information
     * @param entityId entity id to search for
     * @return the Credential used to validate signatures from the SAML2 IDP
     */
    public static Credential getIDPCredential(MetadataResolver idpMetadataResolver, String entityId) throws SAMLInitialisationError {
        LOG.entry(idpMetadataResolver, entityId);
        try {
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
            return LOG.exit(credentialResolver.resolveSingle(criteriaSet));
        } catch (ComponentInitializationException | ResolverException e) {
            throw LOG.throwing(new SAMLInitialisationError("Could not initialse SAML subsystem", e));
        }
    }
    
    public boolean validateSignature(Response response) {
        LOG.entry(response);
        SAMLSignatureProfileValidator profileValidator = new SAMLSignatureProfileValidator();
        try {
            org.opensaml.xmlsec.signature.Signature signature = response.getSignature();
            if (signature == null) return false;
            profileValidator.validate(signature);
            SignatureValidator.validate(signature, getIDPCredential());
        } catch (SignatureException exp) {
            return LOG.exit(false);
        }       
        return LOG.exit(true);
    }
    
    public boolean hasDocumentViewerRole(Response response) {
        //TODO: implement
        return true;
    }
    
    public Response parseSamlResponse(String samlResponse) throws SAMLParsingError {
        LOG.entry(samlResponse);
        try {
            ByteArrayInputStream is = new ByteArrayInputStream(Base64.getDecoder().decode(samlResponse));
            DocumentBuilder docBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = docBuilder.parse(is);

            Element element = document.getDocumentElement();
            if (element == null) throw new RuntimeException("Malformed SAML Response");
            Unmarshaller unmarshaller = unmarshallerFactory.getUnmarshaller(element);
            if (unmarshaller == null) throw new RuntimeException("Can't create XML unmarshaller");
            XMLObject responseXmlObj = unmarshaller.unmarshall(element);
            return LOG.exit((Response)responseXmlObj);
        } catch (ParserConfigurationException | SAXException | IOException | UnmarshallingException e) {
            throw LOG.throwing(new SAMLParsingError("Could not parse SAML response", e));
        } 
    }
    
    public String getEntityId() {
        LOG.entry();
        return LOG.exit(entityId);
    }
    
    public void setEntityId(String entityId) {
        LOG.entry(entityId);
        this.providerData = null;
        this.entityId = entityId;
        LOG.exit();
    }
    
    public Resource getProviderMetadata() {
        LOG.entry();
        return LOG.exit(metadataResource);
    }

    public void setProviderMetadata(Resource metadataResource) {
        LOG.entry(metadataResource);
        this.providerData = null;
        this.metadataResource = metadataResource;
        LOG.exit();
    }
    
    public String getIDPEndpoint() {
        LOG.entry();
        return LOG.exit(getProviderData().idpEndpoint);
    }
    
    public Credential getIDPCredential() {
        LOG.entry();
        return LOG.exit(getProviderData().idpCredential);
    }
    
    /**
     * Build the issuer object
     *
     * @return Issuer object
     */
    private static Issuer buildIssuer(String issuerId) {
        IssuerBuilder issuerBuilder = new IssuerBuilder();
        Issuer issuer = issuerBuilder.buildObject();
        issuer.setValue(issuerId);
        return issuer;
    }

    /**
     * Build the NameIDPolicy object
     *
     * @return NameIDPolicy object
     */
    private static NameIDPolicy buildNameIdPolicy() {
        NameIDPolicy nameIDPolicy = new NameIDPolicyBuilder().buildObject();
        nameIDPolicy.setFormat(SAML2_NAMEID_POLICY);
        nameIDPolicy.setAllowCreate(Boolean.TRUE);
        return nameIDPolicy;
    }
    
    public static OutputStream encode(OutputStream out) {
        //Note the 'true' parameter appears important for generating SAML requests that can
        //actually be understood by most SAML implementations.
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        //Wierd. I'd have thought we should user getURLEncoder here, but in the majority
        //of cases it does not seem to work. Maybe because SAML really expects the POST
        //binding where the 'regular' base-64 encoding is better.
        return new DeflaterOutputStream(Base64.getEncoder().wrap(out), deflater);
    }
    
    public static InputStream decode(InputStream in) {
        Inflater inflater = new Inflater(true);
        return new InflaterInputStream(Base64.getDecoder().wrap(in), inflater);
    }
    
    /** Formate a SAML request
     * 
     * @param ACSUrl - ACS url to handle SAML responses
     * @param issuerId - defaults to the entity Id provided in the constructor
     * @return A formatted SAML request
     * @throws com.softwareplumbers.dms.rest.server.model.SAMLProtocolHandlerService.SAMLOutputError 
     */
    public String formatRequest(String ACSUrl, Optional<String> issuerId) throws SAMLOutputError {
        LOG.entry(ACSUrl, issuerId);
        AuthnRequestBuilder authRequestBuilder = new AuthnRequestBuilder();
        AuthnRequest authRequest = authRequestBuilder.buildObject(SAML2_PROTOCOL, "AuthnRequest", "saml2p");
        authRequest.setAssertionConsumerServiceURL(ACSUrl);
        authRequest.setID(UUID.randomUUID().toString());
        authRequest.setIssueInstant(DateTime.now());
        authRequest.setIssuer(buildIssuer(issuerId.orElse(this.entityId)));
        authRequest.setNameIDPolicy(buildNameIdPolicy());
        authRequest.setDestination(getIDPEndpoint());
        authRequest.setProtocolBinding(SAML2_POST_BINDING);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        try (OutputStream out = encode(encoded)) {
            Marshaller marshaller = marshallerFactory.getMarshaller(authRequest);
            if (marshaller == null) throw new SAMLOutputError("could not create SAML marshaller");
            Element element = marshaller.marshall(authRequest);
            if (element == null) throw new SAMLOutputError("could not marshall authorisation request");
            TransformerFactory tFactory = TransformerFactory.newInstance();
            Transformer transformer = tFactory.newTransformer();
            DOMSource source = new DOMSource(element);
            StreamResult result = new StreamResult(out);
            transformer.transform(source, result);
            out.flush();
        } catch (MarshallingException | TransformerException | IOException e) {
            throw LOG.throwing(new SAMLOutputError("Error creating SAML request", e));
        }
        try { encoded.close(); } catch (IOException e) { LOG.catching(e); };
        return LOG.exit(encoded.toString());
    }
}
