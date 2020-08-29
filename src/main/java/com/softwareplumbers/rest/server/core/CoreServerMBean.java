/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.rest.server.core;

import com.softwareplumbers.common.resourcepath.ResourcePath;
import com.softwareplumbers.keymanager.BadKeyException;
import com.softwareplumbers.keymanager.InitializationFailure;
import com.softwareplumbers.keymanager.KeyManager;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.util.Iterator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;

/**
 *
 * @author Jonathan Essex
 */
public class CoreServerMBean {

    @Autowired(required = false)
    ResourcePath resourcePath;
    @Autowired(required = false)
    KeyManager keyManager;
    @Autowired(required = false)
    CookieRequestValidationService cookieRequestValidationService;
    
    String getStatus() throws InitializationFailure, BadKeyException, KeyStoreException {
        StringBuilder statusString = new StringBuilder();
        if (resourcePath != null) statusString.append("ResourcePath:").append(Stream.of(resourcePath.getLocations()).collect(Collectors.joining(","))).append("\n");
        statusString.append("Key Manager\n");
        if (keyManager != null) {
            Iterator<String> secretKeys = keyManager.getSecretKeyNames();
            while (secretKeys.hasNext()) {
                String alias = secretKeys.next();
                Key key = keyManager.getKey(alias);
                statusString
                    .append("\tPrivate Key: ").append(alias).append(" digest: ").append(KeyManager.keyDigest(key)).append("\n");
            }
            Iterator<String> privateKeys = keyManager.getPrivateKeyNames();
            while (privateKeys.hasNext()) {
                String alias = privateKeys.next();
                KeyPair pair = keyManager.getKeyPair(alias);
                statusString.append("\tCertificate: ").append(alias).append(" public key digest: ").append(KeyManager.keyDigest(pair.getPublic())).append(" private key digest: ").append(KeyManager.keyDigest(pair.getPrivate())).append("\n");
            }            
            Iterator<String> certificates = keyManager.getCertificateNames();
            while (certificates.hasNext()) {
                String alias = certificates.next();
                Certificate cert = keyManager.getCertificate(alias);
                statusString.append("\tCertificate: ").append(alias).append(" public key digest: ").append(KeyManager.keyDigest(cert.getPublicKey())).append("\n");
            }
        }
        return statusString.toString();
    }
    
    String getAccessToken(String uid) {
        return cookieRequestValidationService.generateCookie(uid);
    }
    
}
