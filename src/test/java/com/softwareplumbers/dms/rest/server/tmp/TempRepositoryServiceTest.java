package com.softwareplumbers.dms.rest.server.tmp;

import java.util.UUID;

import org.junit.Before;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.dms.rest.server.model.BaseRepositoryServiceTest;
import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.RepositoryService;
import com.softwareplumbers.dms.common.test.TestModel;
import com.softwareplumbers.dms.rest.server.model.PartHandlerService;
import com.softwareplumbers.dms.rest.server.model.ZipFileHandler;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { TmpConfig.class })
public class TempRepositoryServiceTest extends BaseRepositoryServiceTest {
	
	public RepositoryService service;
	
	@Before
	public void createService() {
		service = new TempRepositoryService(QualifiedName.of("filename"));
        service = new PartHandlerService(service, new ZipFileHandler());
	}

	@Override
	public RepositoryService service() {
		return service;
	}
    
    @Autowired @Qualifier("workspaceMetadataModel")
    TestModel workspaceMetadataModel;
    
    @Autowired @Qualifier("documentMetadataModel")
    TestModel documentMetadataModel;
    
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
