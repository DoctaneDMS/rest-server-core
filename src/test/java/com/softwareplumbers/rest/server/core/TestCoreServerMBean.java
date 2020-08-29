/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.rest.server.core;

import com.softwareplumbers.keymanager.BadKeyException;
import com.softwareplumbers.keymanager.InitializationFailure;
import java.security.KeyStoreException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ImportResource;
import org.springframework.test.context.junit4.SpringRunner;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.*;
import org.hamcrest.Matchers;

/**
 *
 * @author jonat
 */
@RunWith(SpringRunner.class)
@ImportResource({"classpath*:config/services.xml"})
@EnableConfigurationProperties
public class TestCoreServerMBean {
    
    @Autowired
    CoreServerMBean manager;
    
    @Test
    public void testGetStatus() throws InitializationFailure, BadKeyException, KeyStoreException {
        String status = manager.getStatus();
        System.out.println(status);
        assertThat(status, not(isEmptyOrNullString()));        
    }          
    
    @Test
    public void testGetAccessToken() {
        String token = manager.getAccessToken("testuser");
        System.out.println(token);
        assertThat(token, not(isEmptyOrNullString()));        
    }
}
