package com.softwareplumbers.dms.rest.server.core;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.Signature;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;

import javax.inject.Singleton;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.Unmarshaller;
import org.opensaml.core.xml.io.UnmarshallerFactory;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.saml.criterion.EntityRoleCriterion;
import org.opensaml.saml.metadata.resolver.impl.PredicateRoleDescriptorResolver;
import org.opensaml.saml.metadata.resolver.impl.FilesystemMetadataResolver;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.security.impl.MetadataCredentialResolver;
import org.opensaml.saml.security.impl.SAMLSignatureProfileValidator;
import org.opensaml.security.credential.Credential;
import org.opensaml.xmlsec.config.DefaultSecurityConfigurationBootstrap;
import org.opensaml.xmlsec.keyinfo.KeyInfoCredentialResolver;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.opensaml.xmlsec.signature.support.SignatureValidator;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.softwareplumbers.dms.rest.server.util.JWTSecurityContext;
import com.softwareplumbers.dms.rest.server.util.KeyManager;
import com.softwareplumbers.dms.rest.server.util.Log;

import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.ResolverException;



/** Handle authentication operations
 * 
 * The main rest endpoints use a JWT token to authenticate the caller. The Authentication methods
 * provide different ways to obtain a token. 
 * 
 * @author Jonathan Essex
 *
 */
@Component
@Path("/auth")
@Singleton
public class Authentication {
    
    private static final Log LOG = new Log(Authentication.class);
    
    private final UnmarshallerFactory unmarshallerFactory;
    private final DocumentBuilderFactory documentBuilderFactory;
    private final Credential idpCredential;
    private final AuthenticationService cookieHandler;
    private final KeyManager keyManager;
    
    public Authentication(AuthenticationService cookieHandler, KeyManager keyManager) throws InitializationException, ComponentInitializationException, ResolverException {
        InitializationService.initialize();
        documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        unmarshallerFactory = XMLObjectProviderRegistrySupport.getUnmarshallerFactory();
        idpCredential = getIDPCredential();
        this.cookieHandler = cookieHandler;
        this.keyManager = keyManager;
    }
    
    public String getName(org.opensaml.saml.saml2.core.Response samlResponse) {
        Assertion assertion = samlResponse.getAssertions().get(0);
        return assertion.getSubject().getNameID().getValue();
    }
    
    
    public static Credential getIDPCredential() throws ComponentInitializationException, ResolverException {
        try {
            URL metadata = Authentication.class.getResource("/idp-metadata.xml");
            FilesystemMetadataResolver idpMetadataResolver = new FilesystemMetadataResolver(new File(metadata.toURI()));
            idpMetadataResolver.setRequireValidMetadata(true);
            idpMetadataResolver.setParserPool(XMLObjectProviderRegistrySupport.getParserPool());
            idpMetadataResolver.setId("https://auth.softwareplumbers.com/auth/realms/doctane-test");
            idpMetadataResolver.initialize();
    
            KeyInfoCredentialResolver keyResolver = DefaultSecurityConfigurationBootstrap.buildBasicInlineKeyInfoCredentialResolver();
            
            PredicateRoleDescriptorResolver predResolver = new PredicateRoleDescriptorResolver(idpMetadataResolver);
            predResolver.initialize();
            
            MetadataCredentialResolver credentialResolver = new MetadataCredentialResolver();
            credentialResolver.setRoleDescriptorResolver(predResolver);
            credentialResolver.setKeyInfoCredentialResolver(keyResolver);
            credentialResolver.initialize();
            CriteriaSet criteriaSet = new CriteriaSet();
            criteriaSet.add(new EntityIdCriterion("https://auth.softwareplumbers.com/auth/realms/doctane-test"));
            criteriaSet.add(new EntityRoleCriterion(IDPSSODescriptor.DEFAULT_ELEMENT_NAME));
            return credentialResolver.resolveSingle(criteriaSet);
        } catch (URISyntaxException exp) {
            throw new RuntimeException(exp);
        }
    }
    
    boolean validateSignature(org.opensaml.saml.saml2.core.Response response) {
        SAMLSignatureProfileValidator profileValidator = new SAMLSignatureProfileValidator();
        try {
            profileValidator.validate(response.getSignature());
            SignatureValidator.validate(response.getSignature(), idpCredential);
        } catch (SignatureException exp) {
            return false;
        }       
        return true;
    }
    
    boolean validateSignature(byte[] serviceRequest, byte[] signature, String account) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, java.security.SignatureException {
        Key key = keyManager.getKey(account);
        if (key == null) return false;
        Signature sig = Signature.getInstance(KeyManager.DEFAULT_SIGNATURE_ALGORITHM, "SUN");
        sig.initVerify((PublicKey)key);
        sig.update(serviceRequest);
        return sig.verify(signature);
    }
    
    boolean validateInstant(long instant) {
        return Math.abs(instant - System.currentTimeMillis()) < 60000L;
    }
    
    boolean hasDocumentViewerRole(org.opensaml.saml.saml2.core.Response response) {
        //TODO: implement
        return true;
    }
    
    @Path("token")
    @Authenticated
    @GET
    @Produces({MediaType.APPLICATION_JSON})
    public Response getTokenValidity(@Context ContainerRequestContext context) {
        SecurityContext secContext = context.getSecurityContext();
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("user", secContext.getUserPrincipal().getName());
        Date validFrom = (Date)context.getProperty("validFrom");
        Date validUntil = (Date)context.getProperty("validUntil");
        if (validFrom != null) builder.add("validFrom", DateTimeFormatter.ISO_INSTANT.format(validFrom.toInstant()));
        if (validUntil != null) builder.add("validUntil", DateTimeFormatter.ISO_INSTANT.format(validUntil.toInstant()));
        return Response.ok().entity(builder.build()).build();      
    }

    @Path("saml")
    @POST
    @Consumes({ MediaType.APPLICATION_FORM_URLENCODED })
    public Response handleSamlResponse(
        @FormParam("SAMLResponse") String samlResponse,
        @FormParam("RelayState") String relayState
    ) {
        LOG.logEntering("handleSamlResponse");
        
        try {
            ByteArrayInputStream is = new ByteArrayInputStream(Base64.getUrlDecoder().decode(samlResponse));
            DocumentBuilder docBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = docBuilder.parse(is);
        
            Element element = document.getDocumentElement();
            Unmarshaller unmarshaller = unmarshallerFactory.getUnmarshaller(element);
            XMLObject responseXmlObj = unmarshaller.unmarshall(element);
            
            org.opensaml.saml.saml2.core.Response response = (org.opensaml.saml.saml2.core.Response)responseXmlObj;
        
            if (validateSignature(response) && hasDocumentViewerRole(response)) {
                URI location = new URI(relayState);
                return Response.seeOther(location).cookie(cookieHandler.generateCookie(getName(response))).build();
            } else {
                return Response.status(Status.FORBIDDEN).build();
            }
        } catch(RuntimeException e) {
            e.printStackTrace();
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
        } catch (ParserConfigurationException e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
        } catch (SAXException e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();  
        } catch (IOException e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build(); 
        } catch (UnmarshallingException e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build(); 
        } catch (URISyntaxException e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build(); 
        }
        
    }
    
    @Path("service")
    @GET
    public Response handleServiceRequest(
        @QueryParam("request") String request,
        @QueryParam("signature") String signature
    ) {
        LOG.logEntering("handleSamlResponse");
        
        if (request == null) return Response.status(Status.NOT_ACCEPTABLE).build();
        if (signature == null) return Response.status(Status.NOT_ACCEPTABLE).build();

        byte[] requestBinary = Base64.getUrlDecoder().decode(request);
        byte[] signatureBinary = Base64.getUrlDecoder().decode(signature);

        try (
            ByteArrayInputStream is = new ByteArrayInputStream(requestBinary);
            JsonReader reader = Json.createReader(is); 
        ) {

            JsonObject requestObject = reader.readObject();
            String account = requestObject.getString("account");
            long instant = requestObject.getJsonNumber("instant").longValueExact();
            
            if (validateInstant(instant) && validateSignature(requestBinary, signatureBinary, account)) {
                return Response.ok().cookie(cookieHandler.generateCookie(account)).build();
            } else {
                return Response.status(Status.FORBIDDEN).build();
            }
        } catch(RuntimeException e) {
            e.printStackTrace();
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
        } catch (IOException e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build(); 
        } catch (InvalidKeyException e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build(); 
        } catch (NoSuchAlgorithmException e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build(); 
        } catch (NoSuchProviderException e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build(); 
        } catch (java.security.SignatureException e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build(); 
        }
    }

}
