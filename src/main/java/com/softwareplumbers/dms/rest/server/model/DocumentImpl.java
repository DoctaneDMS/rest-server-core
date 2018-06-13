package com.softwareplumbers.dms.rest.server.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;

import org.apache.commons.io.IOUtils;

/** Generic implementation of Document interface.
 * 
 * Document is immutable once created. Setter methods create a copy of object
 * without gratuitously copying data.
 * 
 * @author Jonathan Essex
 */
public class DocumentImpl implements Document {
	
	private final byte[] data;
	private final MediaType mediaType;
	private final JsonObject metadata;

	/** get metadata object */
	@Override
	public JsonObject getMetadata() { return metadata; }

	/** get media type of file */
	@Override
	public MediaType getMediaType() { return mediaType; }

	/** get length of file */
	@Override
	public int getLength() { return data.length; }

	/** Write document to an output stream */
	@Override
	public void writeDocument(OutputStream target) throws IOException {
		target.write(data);
	}
	
	/** Private constructor creates a document from a byte array and meta-data object
	 * 
	 * @param mediaType mime type of file
	 * @param data byte array for underlying file
	 * @param metadata associated meta-data in JSON format
	 */
	private DocumentImpl(MediaType mediaType, byte[] data, JsonObject metadata) {
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
	public DocumentImpl(MediaType mediaType, InputStreamSupplier doc_src, JsonObject metadata) throws IOException {
		try (InputStream stream = doc_src.get()) {
			this.data = IOUtils.toByteArray(stream);
		} 
		this.mediaType = mediaType;
		this.metadata = metadata;
	}
	
	/** Private constructor creates a document from a byte array with empty meta-data
	 * 
	 * @param mediaType mime type of file
	 * @param doc_src Supplies an input stream containing the file data
	 */
	public DocumentImpl(String mediaType, InputStreamSupplier doc_src) throws IOException {
		this(MediaType.valueOf(mediaType), doc_src, JsonObject.EMPTY_JSON_OBJECT);
	}
	
	/** Create a new document with update meta-data and same data. 
	 * 
	 * @param new meta-data
	 * @return A new document
	 */
	public DocumentImpl setMetadata(JsonObject metadata) {
		return new DocumentImpl(this.mediaType, this.data, metadata);
	}
	
	/** Create a new document with same meta-data new data. 
	 * 
	 * @param new meta-data
	 * @return A new document
	 */
	public DocumentImpl setData(InputStreamSupplier doc_src) throws IOException {
		return new DocumentImpl(this.mediaType, doc_src, this.metadata);
	}
}