package com.softwareplumbers.dms.rest.server.core;

import java.io.IOException;
import java.io.OutputStream;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;

import com.softwareplumbers.dms.rest.server.model.Document;

/** Adapts a Document into a StreamingOutput object for use in the Jersey API 
 * 
 * @author Jonathan Essex.
 */
public class DocumentOutput implements StreamingOutput {   	
	private final Document document;

	@Override
	public void write(OutputStream output) throws IOException, WebApplicationException {
		document.writeDocument(output);			
	}
	
	public DocumentOutput(Document document) { this.document = document; }
}