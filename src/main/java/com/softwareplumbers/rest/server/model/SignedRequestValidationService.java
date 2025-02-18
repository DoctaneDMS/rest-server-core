/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.rest.server.model;

import com.softwareplumbers.rest.server.core.SystemKeyPairs;
import com.softwareplumbers.rest.server.core.SystemSecretKeys;
import org.slf4j.ext.XLogger;
import com.softwareplumbers.keymanager.InitializationFailure;
import com.softwareplumbers.keymanager.BadKeyException;
import com.softwareplumbers.keymanager.KeyManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Base64;
import java.util.Optional;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import org.slf4j.ext.XLoggerFactory;

/** Service for validating an access request signed by a private key.
 *
 * @author Jonathan Essex
 */
public class SignedRequestValidationService {

    private static final XLogger LOG = XLoggerFactory.getXLogger(SignedRequestValidationService.class);
    
    private final KeyManager<SystemSecretKeys,SystemKeyPairs> keyManager;
    
    public static class RequestValidationError extends Exception {
        public RequestValidationError(String msg, Exception cause) {
            super(msg, cause);
        }
    }
    
    public SignedRequestValidationService(KeyManager<SystemSecretKeys,SystemKeyPairs> keyManager) {
        this.keyManager = keyManager;
    }
    
    public boolean validateSignature(byte[] serviceRequest, byte[] signature, String account) throws InitializationFailure, NoSuchAlgorithmException, BadKeyException, NoSuchProviderException, InvalidKeyException, java.security.SignatureException {
        LOG.entry(serviceRequest, signature, account);
        Key key = keyManager.getKey(account);
        if (key == null) return false;
        Signature sig = Signature.getInstance(KeyManager.PUBLIC_KEY_SIGNATURE_ALGORITHM, "SUN");
        sig.initVerify((PublicKey)key);
        sig.update(serviceRequest);
        return LOG.exit(sig.verify(signature));
    }
    
    public boolean validateInstant(long instant) {
        LOG.entry(instant);
        return LOG.exit(Math.abs(instant - System.currentTimeMillis()) < 60000L);
    }
    
    public Optional<String> validateSignature(String request, String signature) throws RequestValidationError {
        LOG.entry(request, signature);
        byte[] requestBinary = Base64.getUrlDecoder().decode(request);
        byte[] signatureBinary = Base64.getUrlDecoder().decode(signature);

        try (
            ByteArrayInputStream is = new ByteArrayInputStream(requestBinary);
            JsonReader reader = Json.createReader(is); 
        ) {
            JsonObject requestObject = reader.readObject();
            String account = requestObject.getString("account");
            long instant = requestObject.getJsonNumber("instant").longValueExact();
            
            if (validateInstant(instant) && validateSignature(requestBinary, signatureBinary, account))
                return LOG.exit(Optional.of(account));
            else
                return LOG.exit(Optional.empty());
        
        } catch (IOException e) {
            throw LOG.throwing(new RequestValidationError("could not read request", e));
        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidKeyException | BadKeyException | InitializationFailure | SignatureException e) {
            throw LOG.throwing(new RequestValidationError("could not validate signature", e));            
        }
    }
    
}
