package com.softwareplumbers.dms.rest.server.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidReference;

public class DocumentLinkImpl implements DocumentLink {
    
    protected final Reference ref;
    protected final QualifiedName name;
    protected final RepositoryService service;
    
    public DocumentLinkImpl(RepositoryService service, QualifiedName name, Reference ref) {
        this.ref = ref;
        this.name = name;
        this.service = service;
    }
    
    private Document getDocument() {
        try {
            return service.getDocument(ref);
        } catch (InvalidReference e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public QualifiedName getName() {
        return name;
    }

    @Override
    public JsonObject getMetadata() {
        return getDocument().getMetadata();
    }

    @Override
    public String getId() {
        return ref.id;
    }

    @Override
    public MediaType getMediaType() {
        return getDocument().getMediaType();
    }

    @Override
    public void writeDocument(OutputStream target) throws IOException {
        getDocument().writeDocument(target);
    }

    @Override
    public long getLength() {
        return getDocument().getLength();
    }

    @Override
    public String getVersion() {
        return getDocument().getVersion();
    }
    
    public DocumentLinkImpl toStatic() {
        return new DocumentLinkImpl(service, name, getReference());
    }

    public DocumentLinkImpl toDynamic() {
        return new DocumentLinkImpl(service, name, new Reference(ref.id));
    }

	@Override
	public InputStream getData() throws IOException {
		return getDocument().getData();
	}
    
    /** Return a short string describing document */
    @Override
    public String toString() {
        return String.format("DocumentLink { name: %s, type: %s, length %d }", getName().join("/"), getMediaType().toString(), getLength() );
    }
}
