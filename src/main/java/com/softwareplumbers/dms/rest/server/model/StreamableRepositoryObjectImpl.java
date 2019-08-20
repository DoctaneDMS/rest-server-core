package com.softwareplumbers.dms.rest.server.model;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;

import org.apache.commons.io.IOUtils;

import static com.softwareplumbers.dms.rest.server.model.Constants.*;

/** Generic implementation of Document interface.
 * 
 * Document is immutable once created. Setter methods create a copy of object
 * without gratuitously copying data.
 * 
 * @author Jonathan Essex
 */
public abstract class StreamableRepositoryObjectImpl implements StreamableRepositoryObject {
		
	protected final byte[] data;
	protected final MediaType mediaType;
	protected final JsonObject metadata;

	/** get metadata object
     * @return  A JSON representation of object metadata */
	@Override
	public JsonObject getMetadata() { return metadata; }

	/** get media type of file */
	@Override
	public MediaType getMediaType() { return mediaType; }

	/** get length of file */
	@Override
	public long getLength() { return data.length; }
		
	/** Write document to an output stream */
	@Override
	public void writeDocument(OutputStream target) throws IOException {
		target.write(data);
	}
	
	/** Get document as an input stream */
	@Override
	public InputStream getData() throws IOException {
		return new ByteArrayInputStream(data);
	}
	
	/** Private constructor creates a document from a byte array and meta-data object
	 * 
	 * @param mediaType mime type of file
	 * @param data byte array for underlying file
	 * @param metadata associated meta-data in JSON format
	 */
	protected StreamableRepositoryObjectImpl(MediaType mediaType, byte[] data, JsonObject metadata) {
		this.data = data;
		this.mediaType = mediaType;
		this.metadata = metadata;
	}
	
	/** Private constructor creates a document from a byte array and meta-data object
	 * 
	 * @param mediaType mime type of file
	 * @param doc_src Supplies an input stream containing the file data
	 * @param metadata associated meta-data in JSON format
	 */
	public StreamableRepositoryObjectImpl(MediaType mediaType, InputStreamSupplier doc_src, JsonObject metadata) throws IOException {
		try (InputStream stream = doc_src.get()) {
			this.data = IOUtils.toByteArray(stream);
		} 
		this.mediaType = mediaType;
		this.metadata = metadata;
	}
	
	/** constructor creates a document from a byte array with empty meta-data
	 * 
	 * @param mediaType mime type of file
	 * @param doc_src Supplies an input stream containing the file data
	 */
	public StreamableRepositoryObjectImpl(String mediaType, InputStreamSupplier doc_src) throws IOException {
		this(MediaType.valueOf(mediaType), doc_src, EMPTY_METADATA);
	}
	

}