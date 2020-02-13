package com.softwareplumbers.dms.rest.server.tmp;

import java.util.UUID;

import org.junit.Before;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.dms.rest.server.model.BaseRepositoryServiceTest;
import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.RepositoryService;
import com.softwareplumbers.dms.rest.server.model.PartHandlerService;
import com.softwareplumbers.dms.rest.server.model.ZipFileHandler;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

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

	@Override
	public Reference randomDocumentReference() {
		return new Reference(UUID.randomUUID().toString(), null);
	}

	@Override
	public String randomWorkspaceId() {
		return UUID.randomUUID().toString();
	}

    @Override
    public JsonObject randomDocumentMetadata() {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("filename", UUID.randomUUID().toString());
        return builder.build();
    }

    @Override
    public JsonObject randomWorkspaceMetadata() {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("transaction", UUID.randomUUID().toString());
        return builder.build();
    }

    @Override
    public String uniqueMetadataField() {
        return "filename";
    }

}
