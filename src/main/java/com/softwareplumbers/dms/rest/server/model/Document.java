package com.softwareplumbers.dms.rest.server.model;
import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;
import java.io.OutputStream;


/**  Represents metadata about a document returned by DMS.
 */
interface Document {
	public JsonObject getMetadata();
	public MediaType getMediaType();
	public void writeDocument(OutputStream target);
};
