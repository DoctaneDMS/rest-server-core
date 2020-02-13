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
import com.softwareplumbers.dms.NamedRepositoryObject;
import com.softwareplumbers.dms.RepositoryBrowser;
import com.softwareplumbers.dms.RepositoryObject;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

/**
 *
 * @author jonathan
 */
class DocumentInfo implements DocumentLink {
    
    private static XLogger LOG = XLoggerFactory.getXLogger(DocumentInfo.class);
    
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
            throw LOG.throwing(new RuntimeException(ex));
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
    public void writeDocument(RepositoryBrowser service, OutputStream target) throws IOException {
        linkedDocument().writeDocument(service, target);
    }

    @Override
    public InputStream getData(RepositoryBrowser service) throws IOException {
        return linkedDocument().getData(service);
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

    @Override
    public Optional<RepositoryObject> getParent(RepositoryBrowser rb) { 
        return Optional.of(parent);
    }

    @Override
    public boolean isNavigable() {
        return false;
    }

    @Override
    public Stream<NamedRepositoryObject> getChildren(RepositoryBrowser rb) {
        return Collections.EMPTY_LIST.stream();
    }

    @Override
    public byte[] getDigest() {
        return linkedDocument().getDigest();
    }
    
}
