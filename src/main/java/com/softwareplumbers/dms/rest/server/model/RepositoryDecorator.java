/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractquery.Query;
import com.softwareplumbers.common.pipedstream.InputStreamSupplier;
import com.softwareplumbers.dms.Document;
import com.softwareplumbers.dms.DocumentLink;
import com.softwareplumbers.dms.DocumentPart;
import com.softwareplumbers.dms.Exceptions;
import com.softwareplumbers.dms.NamedRepositoryObject;
import com.softwareplumbers.dms.Options;
import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.RepositoryObject;
import com.softwareplumbers.dms.RepositoryService;
import com.softwareplumbers.dms.Workspace;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;
import java.util.stream.Stream;
import javax.json.JsonObject;

/** RepositoryDecorator allows us to layer additional services on top of Repository.
 *
 * @author metadatanathan
 */
public class RepositoryDecorator implements RepositoryService {
    
    protected RepositoryService baseRepository;
    
    public RepositoryDecorator(RepositoryService baseRepository) {
        this.baseRepository = baseRepository;
    }
    
    public RepositoryDecorator() {
        this(null);
    }
    
    public final void setBaseRepository(RepositoryService baseRepository) {
        this.baseRepository = baseRepository;        
    }
    
    public <T extends RepositoryObject> T mapResult(T result) {
        return result;
    }

    public <T extends RepositoryObject> Stream<T> mapResult(Stream<T> result) {
        return result;
    }

    @Override
    public Reference createDocument(String mediaType, InputStreamSupplier iss, JsonObject metadata) {
        return baseRepository.createDocument(mediaType, iss, metadata);
    }

    @Override
    public Reference updateDocument(String string, String mediaType, InputStreamSupplier iss, JsonObject metadata) throws Exceptions.InvalidDocumentId {
        return baseRepository.updateDocument(string, mediaType, iss, metadata);
    }

    @Override
    public DocumentLink getDocumentLink(String rootId, QualifiedName workspaceName, String id, Options.Get... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName, Exceptions.InvalidDocumentId {
        return mapResult(baseRepository.getDocumentLink(rootId, workspaceName, id, options));
    }

    @Override
    public DocumentLink createDocumentLink(String rootId, QualifiedName objectName, String mediaType, InputStreamSupplier iss, JsonObject metadata, Options.Create... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName, Exceptions.InvalidWorkspaceState {
        return mapResult(baseRepository.createDocumentLink(rootId, objectName, mediaType, iss, metadata, options));
    }

    @Override
    public DocumentLink createDocumentLinkAndName(String rootId, QualifiedName objectName, String mediaType, InputStreamSupplier iss, JsonObject metadata, Options.Create... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidWorkspaceState {
        return mapResult(baseRepository.createDocumentLinkAndName(rootId, objectName, mediaType, iss, metadata, options));
    }

    @Override
    public DocumentLink createDocumentLinkAndName(String rootId, QualifiedName objectName, Reference rfrnc, Options.Create... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidWorkspaceState, Exceptions.InvalidReference {
        return mapResult(baseRepository.createDocumentLinkAndName(rootId, objectName, rfrnc, options));
    }

    @Override
    public DocumentLink createDocumentLink(String rootId, QualifiedName objectName, Reference rfrnc, Options.Create... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidReference, Exceptions.InvalidObjectName, Exceptions.InvalidWorkspaceState {
        return mapResult(baseRepository.createDocumentLink(rootId, objectName, rfrnc, options));
    }

    @Override
    public DocumentLink updateDocumentLink(String rootId, QualifiedName objectName, String mediaType, InputStreamSupplier iss, JsonObject metadata, Options.Update... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName, Exceptions.InvalidWorkspaceState {
        return mapResult(baseRepository.updateDocumentLink(rootId, objectName, mediaType, iss, metadata, options));
    }

    @Override
    public DocumentLink updateDocumentLink(String rootId, QualifiedName objectName, Reference rfrnc, Options.Update... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName, Exceptions.InvalidWorkspaceState, Exceptions.InvalidReference {
        return mapResult(baseRepository.updateDocumentLink(rootId, objectName, rfrnc, options));
    }

    @Override
    public NamedRepositoryObject copyObject(String rootId, QualifiedName objectName, String targetId, QualifiedName targetName, boolean bln) throws Exceptions.InvalidWorkspaceState, Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName {
        return mapResult(baseRepository.copyObject(rootId, objectName, targetId, targetName, bln));
    }

    @Override
    public DocumentLink copyDocumentLink(String rootId, QualifiedName objectName, String targetId, QualifiedName targetName, boolean bln) throws Exceptions.InvalidWorkspaceState, Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName {
        return mapResult(baseRepository.copyDocumentLink(rootId, objectName, targetId, targetName, bln));
    }

    @Override
    public Workspace copyWorkspace(String rootId, QualifiedName objectName, String targetId, QualifiedName targetName, boolean bln) throws Exceptions.InvalidWorkspaceState, Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName {
        return mapResult(baseRepository.copyWorkspace(rootId, objectName, targetId, targetName, bln));
    }

    @Override
    public Workspace createWorkspaceByName(String rootId, QualifiedName objectName, Workspace.State state, JsonObject metadata, Options.Create... options) throws Exceptions.InvalidWorkspaceState, Exceptions.InvalidWorkspace {
        return mapResult(baseRepository.createWorkspaceByName(rootId, objectName, state, metadata, options));
    }

    @Override
    public Workspace createWorkspaceAndName(String rootId, QualifiedName objectName, Workspace.State state, JsonObject metadata, Options.Create... options) throws Exceptions.InvalidWorkspaceState, Exceptions.InvalidWorkspace {
        return mapResult(baseRepository.createWorkspaceAndName(rootId, objectName, state, metadata, options));        
    }

    @Override
    public Workspace updateWorkspaceByName(String rootId, QualifiedName objectName, Workspace.State state, JsonObject metadata, Options.Update... options) throws Exceptions.InvalidWorkspace {
        return mapResult(baseRepository.updateWorkspaceByName(rootId, objectName, state, metadata, options));
    }

    @Override
    public void deleteDocument(String rootId, QualifiedName workspaceName, String documentId) throws Exceptions.InvalidWorkspace, Exceptions.InvalidDocumentId, Exceptions.InvalidWorkspaceState {
        baseRepository.deleteDocument(rootId, workspaceName, documentId);        
    }

    @Override
    public void deleteObjectByName(String rootId, QualifiedName objectName) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName, Exceptions.InvalidWorkspaceState {
        baseRepository.deleteObjectByName(rootId, objectName);
    }

    @Override
    public InputStream getData(String rootId, QualifiedName objectName, Options.Get... options) throws Exceptions.InvalidObjectName, IOException {
        return baseRepository.getData(rootId, objectName, options);
    }

    @Override
    public void writeData(String rootId, QualifiedName objectName, OutputStream out, Options.Get... options) throws Exceptions.InvalidObjectName, IOException {
        baseRepository.writeData(rootId, objectName, out, options);
    }

    @Override
    public Document getDocument(Reference rfrnc) throws Exceptions.InvalidReference {
        return mapResult(baseRepository.getDocument(rfrnc));
    }

    @Override
    public DocumentPart getPart(Reference rfrnc, QualifiedName qn) throws Exceptions.InvalidReference, Exceptions.InvalidObjectName {
        return mapResult(baseRepository.getPart(rfrnc, qn));
    }

    @Override
    public InputStream getData(Reference rfrnc, Optional<QualifiedName> partName) throws Exceptions.InvalidReference, Exceptions.InvalidObjectName, IOException {
        return baseRepository.getData(rfrnc, partName);
    }

    @Override
    public void writeData(Reference rfrnc, Optional<QualifiedName> optnl, OutputStream out) throws Exceptions.InvalidReference, Exceptions.InvalidObjectName, IOException {
        baseRepository.writeData(rfrnc, optnl, out);
    }

    @Override
    public Stream<DocumentPart> catalogueParts(Reference rfrnc, QualifiedName qn) throws Exceptions.InvalidReference, Exceptions.InvalidObjectName {
        return mapResult(baseRepository.catalogueParts(rfrnc, qn));
    }

    @Override
    public DocumentLink getDocumentLink(String rootId, QualifiedName objectName, Options.Get... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName {
        return mapResult(baseRepository.getDocumentLink(rootId, objectName, options));
    }

    @Override
    public Stream<Document> catalogue(Query query, boolean bln) {
        return mapResult(baseRepository.catalogue(query, bln));
    }

    @Override
    public Stream<NamedRepositoryObject> catalogueById(String id, Query query, Options.Search... searchs)  throws Exceptions.InvalidWorkspace {
        return mapResult(baseRepository.catalogueById(id, query, searchs));
    }

    @Override
    public Stream<NamedRepositoryObject> catalogueByName(String rootId, QualifiedName objectName, Query query, Options.Search... searchs) throws Exceptions.InvalidWorkspace {
        return mapResult(baseRepository.catalogueByName(rootId, objectName, query, searchs));
    }

    @Override
    public Stream<Document> catalogueHistory(Reference rfrnc, Query query) throws Exceptions.InvalidReference {
        return mapResult(baseRepository.catalogueHistory(rfrnc, query));
    }

    @Override
    public NamedRepositoryObject getObjectByName(String rootId, QualifiedName objectName, Options.Get... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName {
        return mapResult(baseRepository.getObjectByName(rootId, objectName, options));
    }

    @Override
    public Workspace getWorkspaceByName(String string, QualifiedName qn) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName {
        return mapResult(baseRepository.getWorkspaceByName(string, qn));
    }

    @Override
    public Stream<DocumentLink> listWorkspaces(String rootId, QualifiedName objectName, Query query, Options.Search... options) throws Exceptions.InvalidDocumentId {
        return mapResult(baseRepository.listWorkspaces(rootId, objectName, query, options));
    }
    
    public <T> Optional<T> getImplementation(Class<T> clazz) {
        return clazz.isAssignableFrom(getClass()) ? Optional.of((T)this) : baseRepository.getImplementation(clazz);
    }
    
}
