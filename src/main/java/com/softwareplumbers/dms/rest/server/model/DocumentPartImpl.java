/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.dms.StreamableRepositoryObject;
import com.softwareplumbers.dms.DocumentPart;
import com.softwareplumbers.common.QualifiedName;
import javax.json.JsonObject;

/** Implementation of a simple document part
 *
 * @author jonathan.local
 */
public class DocumentPartImpl implements DocumentPart {

    private final QualifiedName name;
    private final StreamableRepositoryObject document;
    private final JsonObject metadata;
    
    public DocumentPartImpl(StreamableRepositoryObject document, QualifiedName name, JsonObject metadata) {
        this.name = name;
        this.document = document;
        this.metadata = metadata;
    }        
		        
    /** Return a short string describing document */
    @Override
    public String toString() {
        return String.format("DocumentPart { document: %s, name: %s }", document, name );
    }    

    @Override
    public QualifiedName getName() {
        return name;
    }
    
    public DocumentPartImpl setName(QualifiedName name) {
        return new DocumentPartImpl(this.document, name, this.metadata);
    }
    
    @Override
    public StreamableRepositoryObject getDocument() {
        return document;
    }
    
    @Override
    public JsonObject getMetadata() {
        return metadata;
    }
    
    public DocumentPartImpl setMetadata(JsonObject metadata) {
        return new DocumentPartImpl(this.document, this.name, metadata);
    }
    
}
