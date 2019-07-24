package com.softwareplumbers.dms.rest.server.core;

import com.softwareplumbers.dms.rest.server.model.SAMLResponseHandlerService;
import static org.junit.Assert.assertEquals;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.security.KeyStoreException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensaml.core.config.InitializationException;
import org.opensaml.security.credential.Credential;

import com.softwareplumbers.keymanager.KeyManager;

import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.resolver.ResolverException;

public class AuthCryptoTest {


    @Test
    public void testGetCredential() throws InitializationException, ComponentInitializationException, ResolverException, KeyStoreException {
        Credential credential = new SAMLResponseHandlerService().getIDPCredential("https://auth.softwareplumbers.com/auth/realms/doctane-test");
        assertEquals("https://auth.softwareplumbers.com/auth/realms/doctane-test",credential.getEntityId());
    }
}