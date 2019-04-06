package com.softwareplumbers.dms.rest.server.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;

import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

public class KeyManager {
    
    
    public static enum KeyName {
        JWT_SIGNING_KEY 
    }
    
    private static final KeyStore.PasswordProtection KEY_PASSWORD = new KeyStore.PasswordProtection("".toCharArray());
    
    private static final Log LOG = new Log(KeyManager.class);
    
    private KeyStore keystore; 
    
    private static void init(KeyStore keystore) {
        LOG.logEntering("init", Log.fmt(keystore));
        Key secret = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        try {
            keystore.setKeyEntry(KeyName.JWT_SIGNING_KEY.name(), secret, KEY_PASSWORD.getPassword(), null);
        } catch (KeyStoreException e) {
            throw LOG.logRethrow("init", new RuntimeException(e));
        }
        LOG.logExiting("init");
    }
    
    public Key getKey(KeyName name) {
        LOG.logEntering("getKey", name);
        try {
            Key key = keystore.getKey(name.name(), KEY_PASSWORD.getPassword());
            LOG.logReturn("getKey", "<redacted>");
            return key;
        } catch (NoSuchAlgorithmException | UnrecoverableEntryException | KeyStoreException e) {
            throw LOG.logRethrow("getKey", new RuntimeException(e));
        } 
    }
    
    public KeyManager(String location, String password) throws KeyStoreException {
  
        LOG.logEntering("<constructor>", location, "<redacted>");
        File file = new File(location);
        keystore = KeyStore.getInstance("JCEKS");
        
        if (file.exists()) {
            try (InputStream is = new FileInputStream(file)) {
                keystore.load(is, password.toCharArray());
            } catch (IOException | NoSuchAlgorithmException | CertificateException e) {
                throw LOG.logRethrow("<constructor>", new RuntimeException(e));
            }
        } else {
            try (OutputStream os = new FileOutputStream(file)) {
                keystore.load(null, password.toCharArray());
                init(keystore);
                keystore.store(os, password.toCharArray());
            } catch (NoSuchAlgorithmException | CertificateException | IOException e) {
                throw LOG.logRethrow("<constructor>", new RuntimeException(e));
            }
        }
        
        LOG.logExiting("<constructor>");
    }

}
