package com.softwareplumbers.dms.rest.server.tmp;

import java.util.UUID;

import org.junit.Before;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.dms.rest.server.model.BaseRepositoryServiceTest;
import com.softwareplumbers.dms.rest.server.model.DocumentNavigatorService;
import com.softwareplumbers.dms.rest.server.model.Reference;
import com.softwareplumbers.dms.rest.server.model.RepositoryService;
import com.softwareplumbers.dms.rest.server.model.ZipFileNavigatorService;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

public class TempRepositoryServiceTest extends BaseRepositoryServiceTest {
	
	public TempRepositoryService service;
    public ZipFileNavigatorService navigator;
	
	@Before
	public void createService() {
		service = new TempRepositoryService(new ZipFileNavigatorService(), QualifiedName.of("filename"));
        navigator = new ZipFileNavigatorService();
	}

	@Override
	public RepositoryService service() {
		return service;
	}
    
    @Override
    public DocumentNavigatorService navigator() {
        return navigator;
    }

	@Override
	public Reference randomDocumentReference() {
		return new Reference(UUID.randomUUID().toString(), null);
	}

	@Override
	public String randomWorkspaceId() {
		return UUID.randomUUID().toString();
	}

    @Override
    public JsonObject randomMetadata() {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("filename", UUID.randomUUID().toString());
        return builder.build();
    }

    @Override
    public String uniqueMetadataField() {
        return "filename";
    }

}
