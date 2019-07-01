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
public class DocumentImpl implements Document {
		
	private final byte[] data;
	private final MediaType mediaType;
	private final JsonObject metadata;
	private final String id;
	private final String version;

	/** get metadata object */
	@Override
	public JsonObject getMetadata() { return metadata; }

	/** get media type of file */
	@Override
	public MediaType getMediaType() { return mediaType; }

	/** get length of file */
	@Override
	public long getLength() { return data.length; }
	
	/** get the document id */
	@Override
	public String getId() { return id; }
	
	/** get the document version */
    @Override
    public String getVersion() { return version; }

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
	 * @param id id for document
     * @param version version for document
	 * @param mediaType mime type of file
	 * @param data byte array for underlying file
	 * @param metadata associated meta-data in JSON format
	 */
	private DocumentImpl(String id, String version, MediaType mediaType, byte[] data, JsonObject metadata) {
		this.data = data;
		this.mediaType = mediaType;
		this.metadata = metadata;
		this.id = id;
		this.version = version;
	}
	
	/** Private constructor creates a document from a byte array and meta-data object
	 * 
     * @param reference Reference for document
	 * @param mediaType mime type of file
	 * @param doc_src Supplies an input stream containing the file data
	 * @param metadata associated meta-data in JSON format
	 */
	public DocumentImpl(Reference reference, MediaType mediaType, InputStreamSupplier doc_src, JsonObject metadata) throws IOException {
		try (InputStream stream = doc_src.get()) {
			this.data = IOUtils.toByteArray(stream);
		} 
		this.mediaType = mediaType;
		this.metadata = metadata;
		this.id = reference.id;
		this.version = reference.version;
	}
	
	/** constructor creates a document from a byte array with empty meta-data
	 * 
	 * @param reference Reference for document
	 * @param mediaType mime type of file
	 * @param doc_src Supplies an input stream containing the file data
	 */
	public DocumentImpl(Reference reference, String mediaType, InputStreamSupplier doc_src) throws IOException {
		this(reference, MediaType.valueOf(mediaType), doc_src, EMPTY_METADATA);
	}
	
	/** Create a new document with updated meta-data and same data. 
	 * 
	 * @param metadata new meta-data
	 * @return A new document
	 */
	public DocumentImpl setMetadata(JsonObject metadata) {
		return new DocumentImpl(this.id, this.version, this.mediaType, this.data, metadata);
	}
	
	/** Create a new document with same meta-data new data. 
	 * 
	 * @param doc_src new data for document
	 * @return A new document
	 */
	public DocumentImpl setData(InputStreamSupplier doc_src) throws IOException {
		return new DocumentImpl(new Reference(this.id, this.version), this.mediaType, doc_src, this.metadata);
	}
	
	/** Create a new document with the same underlying data but a new Id 
	 * 
	 * @param id
	 * @return a new document
	 */
	public DocumentImpl setId(String id) {
	    return new DocumentImpl(id, this.version, this.mediaType, this.data, this.metadata);
	}
	
	/** Create a new document with the same underlying data but a new version 
     * 
     * @param version
     * @return a new document
     */
    public DocumentImpl setVersion(String version) {
        return new DocumentImpl(this.id, version, this.mediaType, this.data, this.metadata);
    }
    
    /** Create a new document with the same underlying data but a new id and version 
     * 
     * @param reference
     * @return a new document
     */
    public DocumentImpl setReference(Reference reference) {        
        return new DocumentImpl(reference.id, reference.version, this.mediaType, this.data, this.metadata);
    }
    
    /** Return a short string describing document */
    @Override
    public String toString() {
        return String.format("Document { type: %s, length %d }", getMediaType().toString(), getLength() );
    }
}