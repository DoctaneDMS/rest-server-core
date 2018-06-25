package com.softwareplumbers.dms.rest.server.model;
import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;

import java.io.IOException;
import java.io.OutputStream;


/**  Represents a document returned by DMS.
 * 
 * @author Jonathan Essex
 */
public interface Document {
	
	/** Get the metadata associated with this document */
	public JsonObject getMetadata();
	/** Get the media type of this document file */
	public MediaType getMediaType();
	/** Get the metadata associated with this document */
	public void writeDocument(OutputStream target) throws IOException;
	/** Get the length of the document file */
	public long getLength();
};
