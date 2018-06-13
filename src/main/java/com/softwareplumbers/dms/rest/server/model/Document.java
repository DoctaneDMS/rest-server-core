package com.softwareplumbers.dms.rest.server.model;
import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import org.apache.commons.io.IOUtils;


/**  Represents a document returned by DMS.
 * 
 * @author Jonathan Essex
 */
public interface Document {
	
	public JsonObject getMetadata();
	public MediaType getMediaType();
	public void writeDocument(OutputStream target) throws IOException;
	public int getLength();
	
	public static class Default implements Document {
		
		private final byte[] data;
		private final MediaType mediaType;
		private final JsonObject metadata;

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
		
		private Default(MediaType mediaType, byte[] data, JsonObject metadata) {
			this.data = data;
			this.mediaType = mediaType;
			this.metadata = metadata;
		}
		
		public Default(MediaType mediaType, InputStreamSupplier doc_src, JsonObject metadata) throws IOException {
			try (InputStream stream = doc_src.get()) {
				this.data = IOUtils.toByteArray(stream);
			} 
			this.mediaType = mediaType;
			this.metadata = metadata;
		}
		
		public Default(String mediaType, InputStreamSupplier doc_src) throws IOException {
			this(MediaType.valueOf(mediaType), doc_src, JsonObject.EMPTY_JSON_OBJECT);
		}
		
		public Default setMetadata(JsonObject metadata) {
			return new Default(this.mediaType, this.data, metadata);
		}
		
		public Default setData(InputStreamSupplier doc_src) throws IOException {
			return new Default(this.mediaType, doc_src, this.metadata);
		}
	}
};
