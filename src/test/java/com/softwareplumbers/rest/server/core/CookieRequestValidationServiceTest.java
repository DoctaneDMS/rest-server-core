/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.rest.server.core;

import com.softwareplumbers.keymanager.KeyManager;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStoreException;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.NewCookie;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.*;

/**
 *
 * @author jonat
 */
public class CookieRequestValidationServiceTest {
    
    private Path file;
    private Path folder;
    private KeyManager<SystemSecretKeys,SystemKeyPairs> kmgr;
    
    @Before
    public void setup() throws IOException, KeyStoreException {
        String tmpDir = System.getProperty("java.io.tmpdir");
        file = FileSystems.getDefault().getPath(tmpDir, "Doctane_TEST.keystore");
        folder = FileSystems.getDefault().getPath(tmpDir, "Doctane_TEST_exports");
        Files.createDirectories(folder);
        kmgr = new KeyManager<>(file.toString(), folder.toString(), "password", SystemSecretKeys.class, SystemKeyPairs.class);     
    }
    
    @After 
    public void cleanup() throws IOException {
        file.toFile().delete();
        Files.list(folder).forEach(path -> path.toFile().delete());
        folder.toFile().delete();
    }    
        
    @Test 
    public void testSameSiteAttributeDefaultsToLax() {
        CookieRequestValidationService service = new CookieRequestValidationService(kmgr, "test");
        String cookie = service.generateCookie("testuser");
        assertThat(cookie, containsString("SameSite=Lax"));
    }

    @Test 
    public void testSameSiteAttributeCanBeSetToNone() {
        CookieRequestValidationService service = new CookieRequestValidationService(kmgr, "test", CookieRequestValidationService.SameSite.None);
        String cookie = service.generateCookie("testuser");
        assertThat(cookie, containsString("SameSite=None"));
    }
    
}
