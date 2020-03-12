/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.core;

import com.softwareplumbers.dms.RepositoryService;
import com.softwareplumbers.keymanager.KeyManager;
import java.io.IOException;
import java.security.KeyStoreException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import com.softwareplumbers.dms.common.test.TestModel;
import com.softwareplumbers.dms.common.test.TestModel.StringField;
import com.softwareplumbers.dms.common.test.TestModel.BooleanField;
import com.softwareplumbers.dms.common.test.TestModel.SessionIdField;
import com.softwareplumbers.dms.common.test.TestModel.Field;
import com.softwareplumbers.dms.rest.client.spring.DocumentServiceImpl;
import com.softwareplumbers.dms.rest.client.spring.KeyPairs;
import com.softwareplumbers.dms.rest.client.spring.LoginHandler;
import com.softwareplumbers.dms.rest.client.spring.SecretKeys;
import com.softwareplumbers.dms.rest.client.spring.SignedRequestLoginHandler;
/**
 *
 * @author jonathan
 */
@Configuration
public class ClientConfig {
    
    @Autowired
	Environment env;

    @Autowired
	KeyManager keyManager;

    @Bean LoginHandler loginHandler() throws KeyStoreException, IOException {
        SignedRequestLoginHandler handler = new SignedRequestLoginHandler();
        handler.setKeyManager(keyManager);
        handler.setAuthURI("http://localhost:8080/auth/tmp/service?request={request}&signature={signature}");
        handler.setRepository("tmp");
        return handler;
    }
    
    @Bean
    public RepositoryService testService() throws KeyStoreException, IOException {
        DocumentServiceImpl service = new DocumentServiceImpl();
        service.setDocumentAPIURL("http://localhost:8080/docs/tmp/");
        service.setWorkspaceAPIURL("http://localhost:8080/ws/tmp/");
        service.setCatalogueAPIURL("http://localhost:8080/cat/tmp/");
        service.setLoginHandler(loginHandler());
        return service;
    }
    
    @Bean TestModel documentMetadataModel() {
        Field uniqueField = new TestModel.IdField("idfield");
        TestModel model = new TestModel(
                new StringField("TradeDescription", "BR001", "BR002", "BR003", "BR004"),
                new StringField("DocFaceRef", "Ref01", "Ref02", "Ref03", "Ref04"),
                new BooleanField("BankDocument"),
                new SessionIdField("BatchID"),
                uniqueField
        );
        model.setUniqueField(uniqueField);
        return model;
    }

    @Bean TestModel workspaceMetadataModel() {
        return new TestModel(
                new StringField("EventDescription", "Event01", "Event02", "Event03", "Event04"),
                new StringField("Branch", "BR001", "BR002", "BR003", "BR004"),
                new SessionIdField("TheirReference")
        );
    }

}
