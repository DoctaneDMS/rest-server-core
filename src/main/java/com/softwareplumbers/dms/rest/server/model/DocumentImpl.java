package com.softwareplumbers.dms.rest.server.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;

import org.apache.commons.io.IOUtils;

public class DocumentImpl implements Document {
	
	private final byte[] data;
	private final MediaType mediaType;
	private final JsonObject metadata;

	/** get metadata object */
	@Override
	public JsonObject getMetadata() { return metadata; }

	@Override
	public MediaType getMediaType() { return mediaType; }

	@Override
	public int getLength() { return data.length; }

	@Override
	public void writeDocument(OutputStream target) throws IOException {
		target.write(data);
	}
	
	private DocumentImpl(MediaType mediaType, byte[] data, JsonObject metadata) {
		this.data = data;
		this.mediaType = mediaType;
		this.metadata = metadata;
	}
	
	public DocumentImpl(MediaType mediaType, InputStreamSupplier doc_src, JsonObject metadata) throws IOException {
		try (InputStream stream = doc_src.get()) {
			this.data = IOUtils.toByteArray(stream);
		} 
		this.mediaType = mediaType;
		this.metadata = metadata;
	}
	
	public DocumentImpl(String mediaType, InputStreamSupplier doc_src) throws IOException {
		this(MediaType.valueOf(mediaType), doc_src, JsonObject.EMPTY_JSON_OBJECT);
	}
	
	public DocumentImpl setMetadata(JsonObject metadata) {
		return new DocumentImpl(this.mediaType, this.data, metadata);
	}
	
	public DocumentImpl setData(InputStreamSupplier doc_src) throws IOException {
		return new DocumentImpl(this.mediaType, doc_src, this.metadata);
	}
}