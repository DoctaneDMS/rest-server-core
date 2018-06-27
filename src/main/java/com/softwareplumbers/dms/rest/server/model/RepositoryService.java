package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.common.abstractquery.Cube;
import java.util.stream.Stream;

import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;


/**  Represents a DMS repository
 */
public interface RepositoryService {
	public Document getDocument(Reference reference);
	public Reference createDocument(MediaType mediaType, InputStreamSupplier stream, JsonObject metadata);
	public Reference updateDocument(String id, MediaType mediaType, InputStreamSupplier stream, JsonObject metadata);
	public Stream<Info> catalogue(Cube filter);
};
