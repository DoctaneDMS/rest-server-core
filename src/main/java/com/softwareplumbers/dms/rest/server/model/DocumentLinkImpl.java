package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.dms.DocumentLink;
import com.softwareplumbers.dms.Document;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;

import com.softwareplumbers.common.QualifiedName;

public class DocumentLinkImpl implements DocumentLink {
    
    protected final QualifiedName name;
    protected final Document document;
    
    /**
     *
     * @param name
     * @param document
     */
    public DocumentLinkImpl(QualifiedName name, Document document) {
        this.document = document;
        this.name = name;
    }

    @Override
    public QualifiedName getName() {
        return name;
    }

    @Override
    public JsonObject getMetadata() {
        return document.getMetadata();
    }

    @Override
    public String getId() {
        return document.getId();
    }

    @Override
    public String getMediaType() {
        return document.getMediaType();
    }

    @Override
    public void writeDocument(OutputStream target) throws IOException {
        document.writeDocument(target);
    }

    @Override
    public long getLength() {
        return document.getLength();
    }

    @Override
    public String getVersion() {
        return document.getVersion();
    }

	@Override
	public InputStream getData() throws IOException {
		return document.getData();
	}
    
    /** Return a short string describing document */
    @Override
    public String toString() {
        return String.format("DocumentLink { name: %s, type: %s, length %d }", getName().join("/"), getMediaType().toString(), getLength() );
    }
}
