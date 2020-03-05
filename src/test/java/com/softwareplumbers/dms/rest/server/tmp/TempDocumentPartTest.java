package com.softwareplumbers.dms.rest.server.tmp;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.dms.RepositoryService;
import com.softwareplumbers.dms.common.test.DocumentPartTest;
import com.softwareplumbers.dms.common.test.TestModel;
import com.softwareplumbers.dms.rest.server.model.PartHandlerService;
import com.softwareplumbers.dms.rest.server.model.ZipFileHandler;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 *
 * @author jonathan
 */

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { TmpConfig.class })
public class TempDocumentPartTest extends DocumentPartTest {
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
    public TestModel documentMetadataModel() {
        return documentMetadataModel;
    }
    
    @Override
    public TestModel workspaceMetadataModel() {
        return workspaceMetadataModel;
    }    
}
