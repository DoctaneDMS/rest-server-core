package com.softwareplumbers.dms.rest.server.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyStoreException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class KeyStoreTest {
    
    Path file;
    
    @Before
    public void setup() {
        file = FileSystems.getDefault().getPath(System.getProperty("java.io.tmpdir"), "Doctane_TEST.keystore"); 
    }
    
    @After 
    public void cleanup() {
        file.toFile().delete();
    }
    
    @Test
    public void testCreateNewKeyStore() throws KeyStoreException, IOException {
        KeyManager kmgr = new KeyManager(file.toString(), "password");
        Key key = kmgr.getKey(KeyManager.KeyName.JWT_SIGNING_KEY);
        assertNotNull(key);
    }
    
    @Test
    public void testPersistentStore() throws KeyStoreException, IOException {
        KeyManager kmgr1 = new KeyManager(file.toString(), "password");
        Key key1 = kmgr1.getKey(KeyManager.KeyName.JWT_SIGNING_KEY);
        KeyManager kmgr2 = new KeyManager(file.toString(), "password");
        Key key2 = kmgr1.getKey(KeyManager.KeyName.JWT_SIGNING_KEY);
        assertEquals(key1,key2);
    }
}


