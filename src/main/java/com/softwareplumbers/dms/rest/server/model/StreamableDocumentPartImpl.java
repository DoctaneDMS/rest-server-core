/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.dms.InputStreamSupplier;
import com.softwareplumbers.dms.StreamableDocumentPart;
import com.softwareplumbers.dms.StreamableRepositoryObject;
import com.softwareplumbers.common.QualifiedName;
import java.io.IOException;
import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;

/**
 *
 * @author jonathan.local
 */
public class StreamableDocumentPartImpl extends StreamableRepositoryObjectImpl implements StreamableDocumentPart {
    
    private final QualifiedName name;
    private final StreamableRepositoryObject document;
    
    public StreamableDocumentPartImpl(StreamableRepositoryObject document, QualifiedName name, MediaType mediaType, InputStreamSupplier doc_src, JsonObject metadata) throws IOException {
        super(mediaType, doc_src, metadata);
        this.name = name;
        this.document = document;
    }
    
    public StreamableDocumentPartImpl(StreamableRepositoryObject document, QualifiedName name, MediaType mediaType, byte[] data, JsonObject metadata) {
        super(mediaType, data, metadata);
        this.name = name;
        this.document = document;
    }
    
	/** Create a new document with updated meta-data and same data. 
	 * 
	 * @param metadata new meta-data
	 * @return A new document
	 */
	public StreamableDocumentPartImpl setMetadata(JsonObject metadata) {
		return new StreamableDocumentPartImpl(this.document, this.name, this.mediaType, this.data, metadata);
	}
	
	/** Create a new document with same meta-data new data. 
	 * 
	 * @param doc_src new data for document
	 * @return A new document
	 */
	public StreamableDocumentPartImpl setData(InputStreamSupplier doc_src) throws IOException {
		return new StreamableDocumentPartImpl(this.document, this.name, this.mediaType, doc_src, this.metadata);
	}
		        
    /** Return a short string describing document */
    @Override
    public String toString() {
        return String.format("Document { type: %s, length %d }", getMediaType().toString(), getLength() );
    }    

    @Override
    public QualifiedName getName() {
        return name;
    }
    
    @Override
    public StreamableRepositoryObject getDocument() {
        return document;
    }
    
    public StreamableDocumentPartImpl setName(QualifiedName name) {
        return new StreamableDocumentPartImpl(this.document, name, this.mediaType, this.data, this.metadata);
    }
}
