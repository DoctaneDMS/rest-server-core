package com.softwareplumbers.dms.rest.server.core;

import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.RepositoryService;
import com.softwareplumbers.dms.common.test.DocumentServiceTest;
import com.softwareplumbers.dms.common.test.TestModel;
import java.util.UUID;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author jonathan
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, classes = Application.class)
@EnableConfigurationProperties
public class CoreServerTest extends DocumentServiceTest  {
    
    @Autowired @Qualifier("testService")
    RepositoryService service;
    
    @Autowired @Qualifier("workspaceMetadataModel")
    TestModel workspaceMetadataModel;
    
    @Autowired @Qualifier("documentMetadataModel")
    TestModel documentMetadataModel;
    
    @Override
    public RepositoryService service() {
        return service;
    }

    @Override
    public Reference randomDocumentReference() {
        return new Reference(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }
    
    @Override
    public String randomWorkspaceId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public TestModel documentMetadataModel() {
        return documentMetadataModel;
    }
    
    @Override
    public TestModel workspaceMetadataModel() {
        return workspaceMetadataModel;
    }
    
    
}
