/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.tmp;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.dms.Document;
import com.softwareplumbers.dms.DocumentLink;
import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.RepositoryService;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;
import com.softwareplumbers.dms.Exceptions.*;

/**
 *
 * @author jonathan
 */
class DocumentInfo implements DocumentLink {
    
    public boolean deleted;
    public Reference link;
    public String name;
    private final WorkspaceImpl parent;

    public DocumentInfo(String name, Reference link, boolean deleted, final WorkspaceImpl parent) {
        this.parent = parent;
        this.deleted = deleted;
        this.link = link;
        this.name = name;
    }

    public DocumentLink toDynamic() {
        return new DocumentInfo(this.name, new Reference(link.id), deleted, parent);
    }

    public DocumentLink toStatic() {
        return new DocumentInfo(this.name, getReference(), deleted, parent);
    }

    @Override
    public QualifiedName getName() {
        return parent.getName().add(name);
    }

    private Document linkedDocument() {
        try {
            return parent.service.getDocument(link);
        } catch (InvalidReference ex) {
            throw WorkspaceImpl.LOG.logRethrow("DocumentInfo.getMetadata", new RuntimeException(ex));
        }
    }

    @Override
    public JsonObject getMetadata() {
        return linkedDocument().getMetadata();
    }

    @Override
    public String getId() {
        return link.id;
    }

    @Override
    public String getMediaType() {
        return linkedDocument().getMediaType();
    }

    @Override
    public void writeDocument(OutputStream target) throws IOException {
        linkedDocument().writeDocument(target);
    }

    @Override
    public InputStream getData() throws IOException {
        return linkedDocument().getData();
    }

    @Override
    public long getLength() {
        return linkedDocument().getLength();
    }

    @Override
    public String getVersion() {
        return linkedDocument().getVersion();
    }

    @Override
    public Reference getReference() {
        return linkedDocument().getReference();
    }
    
}
