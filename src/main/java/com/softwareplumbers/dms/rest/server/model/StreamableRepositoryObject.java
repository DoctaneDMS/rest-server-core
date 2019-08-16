/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.ws.rs.core.MediaType;

/** A repository object which can be read from or written to a stream.
 *
 * @author jonathan.local
 */
public interface StreamableRepositoryObject extends RepositoryObject {

    /** Get the media type of this stream
     * @return the media type of the 
     */
	public MediaType getMediaType();

    /** Write the data associated with this document to an output stream
     * @param target the stream to write to
     * @throws java.io.IOException if stream cannot be written to
     */	
    public void writeDocument(OutputStream target) throws IOException;

    /** Get the data associated with this document as an input stream
     * @return An input stream that can be used to completely read this stream
     * @throws java.io.IOException
     */
	public InputStream getData() throws IOException;
    
	/** Get the length of the document file
     * @return the number of bytes that will be written or can be read */
	public long getLength();
    
}
