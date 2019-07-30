/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.dms.rest.server.core.SystemKeyPairs;
import com.softwareplumbers.dms.rest.server.core.SystemSecretKeys;
import com.softwareplumbers.dms.rest.server.util.Log;
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

/** Service for validating an access request signed by a private key.
 *
 * @author Jonathan Essex
 */
public class SignedRequestValidationService {

    private static final Log LOG = new Log(SignedRequestValidationService.class);
    
    private final KeyManager<SystemSecretKeys,SystemKeyPairs> keyManager;
    
    public static class RequestValidationError extends Exception {
        public RequestValidationError(String msg, Exception cause) {
            super(msg, cause);
        }
    }
    
    public SignedRequestValidationService(KeyManager<SystemSecretKeys,SystemKeyPairs> keyManager) {
        this.keyManager = keyManager;
    }
    
    public boolean validateSignature(byte[] serviceRequest, byte[] signature, String account) throws NoSuchAlgorithmException, BadKeyException, NoSuchProviderException, InvalidKeyException, java.security.SignatureException {
        LOG.logEntering("validateSignature", serviceRequest, signature, account);
        Key key = keyManager.getKey(account);
        if (key == null) return false;
        Signature sig = Signature.getInstance(KeyManager.PUBLIC_KEY_SIGNATURE_ALGORITHM, "SUN");
        sig.initVerify((PublicKey)key);
        sig.update(serviceRequest);
        return LOG.logReturn("validateSignature", sig.verify(signature));
    }
    
    public boolean validateInstant(long instant) {
        LOG.logEntering("validateInstant", instant);
        return LOG.logReturn("validateinstant", Math.abs(instant - System.currentTimeMillis()) < 60000L);
    }
    
    public Optional<String> validateSignature(String request, String signature) throws RequestValidationError {
        LOG.logEntering("validateSignature", request, signature);
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
                return LOG.logReturn("validateSignature",Optional.of(account));
            else
                return LOG.logReturn("validateSignature",Optional.empty());
        
        } catch (IOException e) {
            throw LOG.logThrow("validateSignature", new RequestValidationError("could not read request", e));
        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidKeyException | BadKeyException | SignatureException e) {
            throw LOG.logThrow("validateSignature", new RequestValidationError("could not validate signature", e));            
        }
    }
    
}
