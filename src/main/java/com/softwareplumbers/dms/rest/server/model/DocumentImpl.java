/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.dms.InputStreamSupplier;
import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.Document;
import java.io.IOException;
import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;

/**
 *
 * @author jonathan.local
 */
public class DocumentImpl extends StreamableRepositoryObjectImpl implements Document {
    
    private final Reference reference;
    
    public DocumentImpl(Reference reference, String mediaType, InputStreamSupplier doc_src, JsonObject metadata) throws IOException {
        super(mediaType, doc_src, metadata);
        this.reference = reference;
    }
    
    public DocumentImpl(Reference reference, String mediaType, byte[] data, JsonObject metadata) {
        super(mediaType, data, metadata);
        this.reference = reference;
    }

    @Override
    public Reference getReference() {
        return reference;
    }
    
    @Override
    public String getVersion() {
        return reference.version;
    }
    
    @Override
    public String getId() {
        return reference.id;
    }
    
	/** Create a new document with updated meta-data and same data. 
	 * 
	 * @param metadata new meta-data
	 * @return A new document
	 */
	public DocumentImpl setMetadata(JsonObject metadata) {
		return new DocumentImpl(this.getReference(), this.mediaType, this.data, metadata);
	}
	
	/** Create a new document with same meta-data new data. 
	 * 
	 * @param doc_src new data for document
	 * @return A new document
	 */
	public DocumentImpl setData(InputStreamSupplier doc_src) throws IOException {
		return new DocumentImpl(this.getReference(), this.mediaType, doc_src, this.metadata);
	}
	
	/** Create a new document with the same underlying data but a new Id 
	 * 
	 * @param id
	 * @return a new document
	 */
	public DocumentImpl setId(String id) {
	    return new DocumentImpl(new Reference(id, null), this.mediaType, this.data, this.metadata);
	}
    
    public DocumentImpl setReference(Reference reference) {
        return new DocumentImpl(reference, this.mediaType, this.data, this.metadata);
    }
	        
    /** Return a short string describing document */
    @Override
    public String toString() {
        return String.format("Document { type: %s, length %d }", getMediaType().toString(), getLength() );
    }    
}
