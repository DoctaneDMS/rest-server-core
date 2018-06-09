package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.common.abstractquery.Query;
import java.util.List;

import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;


/**  Represents a DMS repository
 */
public interface RepositoryService {
	public Document getDocument(Document.Reference reference);
	public Document.Reference createDocument(MediaType mediaType, InputStreamSupplier stream, JsonObject metadata);
	public Document.Reference updateDocument(String id, MediaType mediaType, InputStreamSupplier stream, JsonObject metadata);
	public List<Document.Reference> catalogue(Query filter);
};
