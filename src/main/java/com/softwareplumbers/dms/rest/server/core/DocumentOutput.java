package com.softwareplumbers.dms.rest.server.core;

import java.io.IOException;
import java.io.OutputStream;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;

import com.softwareplumbers.dms.StreamableRepositoryObject;

/** Adapts a Document into a StreamingOutput object for use in the Jersey API 
 * 
 * @author Jonathan Essex.
 */
public class DocumentOutput implements StreamingOutput {   	
	private final StreamableRepositoryObject document;

	@Override
	public void write(OutputStream output) throws IOException, WebApplicationException {
		document.writeDocument(output);			
	}
	
	public DocumentOutput(StreamableRepositoryObject document) { this.document = document; }
}