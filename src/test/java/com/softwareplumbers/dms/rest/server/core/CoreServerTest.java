package com.softwareplumbers.dms.rest.server.core;

import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.RepositoryService;
import com.softwareplumbers.dms.common.test.DocumentServiceTest;
import com.softwareplumbers.dms.common.test.TestModel;
import com.softwareplumbers.dms.service.sql.Schema;
import java.sql.SQLException;
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
    
    @Autowired 
    Schema schema;

    boolean init = true;
    
    public CoreServerTest() {
        super(false);
    }
    
    // TODO: make this BeforeAll in junit 5
    public void initSchema() {
        try {
            schema.dropSchema();
            schema.createSchema();
            schema.updateSchema();
        } catch(SQLException e) {
            throw new RuntimeException(e);
        }        
    }

	@Override
	public RepositoryService service() {
        if (init) { init = false; initSchema(); };
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
