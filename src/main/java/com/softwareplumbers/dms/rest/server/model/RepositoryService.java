package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.common.abstractquery.Query;
import java.util.List;
import java.util.function.Supplier;

import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;

import java.io.InputStream;

/**  Represents a DMS repository
 */
public interface RepositoryService {
	public Document getDocument(Document.Reference reference);
	public Document.Reference createDocument(MediaType mediaType, Supplier<InputStream> stream, JsonObject metadata);
	public List<Document.Reference> catalogue(Query filter);
};
