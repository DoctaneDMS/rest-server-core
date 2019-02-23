package com.softwareplumbers.dms.rest.server.model;
import javax.ws.rs.core.MediaType;

import java.io.IOException;
import java.io.OutputStream;


/**  Represents a document returned by DMS.
 * 
 * @author Jonathan Essex
 */
public interface Document extends RepositoryObject {
	
	/** Get the media type of this document file */
	public MediaType getMediaType();
	/** Get the metadata associated with this document */
	public void writeDocument(OutputStream target) throws IOException;
	/** Get the length of the document file */
	public long getLength();
	/** Default implementation returns Type.DOCUMENT */	
	default Type getType() { return Type.DOCUMENT; }
};
