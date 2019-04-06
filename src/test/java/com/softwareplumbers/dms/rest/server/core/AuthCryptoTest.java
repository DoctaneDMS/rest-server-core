package com.softwareplumbers.dms.rest.server.core;

import static org.junit.Assert.assertEquals;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.security.KeyStoreException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensaml.core.config.InitializationException;
import org.opensaml.security.credential.Credential;

import com.softwareplumbers.dms.rest.server.util.KeyManager;

import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.resolver.ResolverException;

public class AuthCryptoTest {
    
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
    public void testGetCredential() throws InitializationException, ComponentInitializationException, ResolverException, KeyStoreException {
        Authentication authResource = new Authentication(new AuthenticationService(new KeyManager(file.toString(),"")));
        Credential credential = Authentication.getIDPCredential();
        assertEquals("https://auth.softwareplumbers.com/auth/realms/doctane-test",credential.getEntityId());
    }
}