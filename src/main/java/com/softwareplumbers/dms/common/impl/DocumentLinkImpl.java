/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.common.impl;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.dms.Document;
import com.softwareplumbers.dms.DocumentLink;
import com.softwareplumbers.dms.InputStreamSupplier;
import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.RepositoryService;
import static com.softwareplumbers.dms.Exceptions.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Supplier;
import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;

/**
 *
 * @author jonathan
 */
public class DocumentLinkImpl implements DocumentLink {
    
    private final QualifiedName name;
    private final Supplier<Document> lookup;
    private final Reference reference;
        
    private DocumentLinkImpl(QualifiedName name, Reference reference, Supplier<Document> lookup) {
        this.name = name;
        this.reference = reference;
        this.lookup = lookup;
    }
    
    public DocumentLinkImpl(QualifiedName name, String id, String version, MediaType type, InputStreamSupplier supplier, long length, JsonObject metadata) {
        this.name = name;
        this.reference = new Reference(id, version);
        final Document document = new DocumentImpl(id, version, type, supplier, length, metadata);
        lookup = () -> document;
    }
    
    public DocumentLinkImpl(QualifiedName name, RepositoryService service, Reference reference) {
        this.name = name;
        this.reference = reference;
        lookup = CachedSupplier.of(() ->{
            try { return service.getDocument(reference); } catch (BaseException e) { throw new BaseRuntimeException(e); }
        });
    }

    @Override
    public QualifiedName getName() {
        return name;
    }

    @Override
    public JsonObject getMetadata() {
        return lookup.get().getMetadata();
    }

    @Override
    public String getId() {
        return reference.getId();
    }

    @Override
    public String getVersion() {
        return reference.getVersion();
    }

    @Override
    public MediaType getMediaType() {
        return lookup.get().getMediaType();
    }

    @Override
    public void writeDocument(OutputStream target) throws IOException {
        lookup.get().writeDocument(target);
    }

    @Override
    public InputStream getData() throws IOException {
        return lookup.get().getData();
    }

    @Override
    public long getLength() {
        return lookup.get().getLength();
    }
    
}
