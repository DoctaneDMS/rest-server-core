package com.softwareplumbers.dms.rest.server.model;
import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import org.apache.commons.io.IOUtils;
import java.util.function.Supplier;


/**  Represents metadata about a document returned by DMS.
 */
public interface Document {
	
	public static class Reference implements Comparable<Reference> {
		public final String id;
		public final Integer version;
		public Reference(String id, Integer version) { this.id = id; this.version = version; }
		public Reference(String id) { this.id = id; this.version = null; }
		
		public boolean equals(Reference other) { 
			if (id.equals(other.id)) return true;
			if (version == other.version) return true;
			if (version != null && other.version != null) return version.equals(other.version);
			return false;
		}
		
		public boolean equals(Object other) {
			return other instanceof Reference && equals((Reference)other);
		}
		
		public int hashCode() {
			return id.hashCode() ^ (version == null ? 0 : version.intValue());
		}

		public int compareTo(Reference other) {
			int result = id.compareTo(other.id);
			if (result != 0) return result;
			if (version == other.version) return 0; 
			if (version == null) return 1;
			if (other.version == null) return -1;
			if (version < other.version) return -1;
			if (version > other.version) return 1;
			return 0;
		}
	}
	
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
