/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.common.abstractquery.Query;
import com.softwareplumbers.common.immutablelist.QualifiedName;
import com.softwareplumbers.common.pipedstream.InputStreamSupplier;
import com.softwareplumbers.dms.Document;
import com.softwareplumbers.dms.DocumentLink;
import com.softwareplumbers.dms.DocumentPart;
import com.softwareplumbers.dms.Exceptions;
import com.softwareplumbers.dms.NamedRepositoryObject;
import com.softwareplumbers.dms.Options;
import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.RepositoryObject;
import com.softwareplumbers.dms.RepositoryPath;
import com.softwareplumbers.dms.RepositoryService;
import com.softwareplumbers.dms.VersionedRepositoryObject;
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
    public DocumentLink createDocumentLink(RepositoryPath objectName, String mediaType, InputStreamSupplier iss, JsonObject metadata, Options.Create... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName, Exceptions.InvalidWorkspaceState {
        return mapResult(baseRepository.createDocumentLink(objectName, mediaType, iss, metadata, options));
    }

    @Override
    public DocumentLink createDocumentLinkAndName(RepositoryPath objectName, String mediaType, InputStreamSupplier iss, JsonObject metadata, Options.Create... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidWorkspaceState {
        return mapResult(baseRepository.createDocumentLinkAndName(objectName, mediaType, iss, metadata, options));
    }

    @Override
    public DocumentLink createDocumentLinkAndName(RepositoryPath objectName, Reference rfrnc, Options.Create... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidWorkspaceState, Exceptions.InvalidReference {
        return mapResult(baseRepository.createDocumentLinkAndName(objectName, rfrnc, options));
    }

    @Override
    public DocumentLink createDocumentLink(RepositoryPath objectName, Reference rfrnc, Options.Create... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidReference, Exceptions.InvalidObjectName, Exceptions.InvalidWorkspaceState {
        return mapResult(baseRepository.createDocumentLink(objectName, rfrnc, options));
    }

    @Override
    public DocumentLink updateDocumentLink(RepositoryPath objectName, String mediaType, InputStreamSupplier iss, JsonObject metadata, Options.Update... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName, Exceptions.InvalidWorkspaceState {
        return mapResult(baseRepository.updateDocumentLink(objectName, mediaType, iss, metadata, options));
    }

    @Override
    public DocumentLink updateDocumentLink(RepositoryPath objectName, Reference rfrnc, Options.Update... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName, Exceptions.InvalidWorkspaceState, Exceptions.InvalidReference {
        return mapResult(baseRepository.updateDocumentLink(objectName, rfrnc, options));
    }

    @Override
    public NamedRepositoryObject copyObject(RepositoryPath objectName, RepositoryPath targetName, boolean bln) throws Exceptions.InvalidWorkspaceState, Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName {
        return mapResult(baseRepository.copyObject(objectName, targetName, bln));
    }

    @Override
    public DocumentLink copyDocumentLink(RepositoryPath objectName, RepositoryPath targetName, boolean bln) throws Exceptions.InvalidWorkspaceState, Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName {
        return mapResult(baseRepository.copyDocumentLink(objectName, targetName, bln));
    }

    @Override
    public Workspace copyWorkspace(RepositoryPath objectName, RepositoryPath targetName, boolean bln) throws Exceptions.InvalidWorkspaceState, Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName {
        return mapResult(baseRepository.copyWorkspace(objectName, targetName, bln));
    }

    @Override
    public Workspace createWorkspaceByName(RepositoryPath objectName, Workspace.State state, JsonObject metadata, Options.Create... options) throws Exceptions.InvalidWorkspaceState, Exceptions.InvalidWorkspace {
        return mapResult(baseRepository.createWorkspaceByName(objectName, state, metadata, options));
    }

    @Override
    public Workspace createWorkspaceAndName(RepositoryPath objectName, Workspace.State state, JsonObject metadata, Options.Create... options) throws Exceptions.InvalidWorkspaceState, Exceptions.InvalidWorkspace {
        return mapResult(baseRepository.createWorkspaceAndName(objectName, state, metadata, options));        
    }

    @Override
    public Workspace updateWorkspaceByName(RepositoryPath objectName, Workspace.State state, JsonObject metadata, Options.Update... options) throws Exceptions.InvalidWorkspace {
        return mapResult(baseRepository.updateWorkspaceByName(objectName, state, metadata, options));
    }

    @Override
    public void deleteDocument(RepositoryPath workspaceName, String documentId) throws Exceptions.InvalidWorkspace, Exceptions.InvalidDocumentId, Exceptions.InvalidWorkspaceState {
        baseRepository.deleteDocument(workspaceName, documentId);        
    }

    @Override
    public void deleteObjectByName(RepositoryPath objectName) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName, Exceptions.InvalidWorkspaceState {
        baseRepository.deleteObjectByName(objectName);
    }

    @Override
    public InputStream getData(RepositoryPath objectName, Options.Get... options) throws Exceptions.InvalidObjectName, IOException {
        return baseRepository.getData(objectName, options);
    }

    @Override
    public void writeData(RepositoryPath objectName, OutputStream out, Options.Get... options) throws Exceptions.InvalidObjectName, IOException {
        baseRepository.writeData(objectName, out, options);
    }

    @Override
    public Document getDocument(Reference rfrnc) throws Exceptions.InvalidReference {
        return mapResult(baseRepository.getDocument(rfrnc));
    }

    @Override
    public RepositoryObject getPart(Reference rfrnc, RepositoryPath qn) throws Exceptions.InvalidReference, Exceptions.InvalidObjectName {
        return mapResult(baseRepository.getPart(rfrnc, qn));
    }

    @Override
    public Stream<DocumentPart> catalogueParts(Reference rfrnc, RepositoryPath qn) throws Exceptions.InvalidReference, Exceptions.InvalidObjectName {
        return mapResult(baseRepository.catalogueParts(rfrnc, qn));
    }

    @Override
    public DocumentLink getDocumentLink(RepositoryPath objectName, Options.Get... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName {
        return mapResult(baseRepository.getDocumentLink(objectName, options));
    }

    @Override
    public Stream<Document> catalogue(Query query, boolean bln) {
        return mapResult(baseRepository.catalogue(query, bln));
    }

    @Override
    public Stream<NamedRepositoryObject> catalogueByName(RepositoryPath objectName, Query query, Options.Search... searchs) throws Exceptions.InvalidWorkspace {
        return mapResult(baseRepository.catalogueByName(objectName, query, searchs));
    }

    @Override
    public Stream<Document> catalogueHistory(Reference rfrnc, Query query) throws Exceptions.InvalidReference {
        return mapResult(baseRepository.catalogueHistory(rfrnc, query));
    }

    @Override
    public NamedRepositoryObject getObjectByName(RepositoryPath objectName, Options.Get... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName {
        return mapResult(baseRepository.getObjectByName(objectName, options));
    }

    @Override
    public Workspace getWorkspaceByName(RepositoryPath qn) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName {
        return mapResult(baseRepository.getWorkspaceByName(qn));
    }
    
    public <T> Optional<T> getImplementation(Class<T> clazz) {
        return clazz.isAssignableFrom(getClass()) ? Optional.of((T)this) : baseRepository.getImplementation(clazz);
    }

    @Override
    public DocumentLink publishDocumentLink(RepositoryPath rp, String versionName, JsonObject metadata) throws Exceptions.InvalidObjectName, Exceptions.InvalidVersionName {
        return mapResult(baseRepository.publishDocumentLink(rp, versionName, metadata));
    }

    @Override
    public Workspace publishWorkspace(RepositoryPath rp, String versionName, JsonObject metadata) throws Exceptions.InvalidWorkspace, Exceptions.InvalidVersionName {
        return mapResult(baseRepository.publishWorkspace(rp, versionName, metadata));
    }

    @Override
    public InputStream getData(Reference rfrnc, RepositoryPath path) throws Exceptions.InvalidReference, Exceptions.InvalidObjectName, IOException {
        return baseRepository.getData(rfrnc, path);
    }

    @Override
    public void writeData(Reference rfrnc, RepositoryPath path, OutputStream out) throws Exceptions.InvalidReference, Exceptions.InvalidObjectName, IOException {
        baseRepository.writeData(rfrnc, path, out);
    }
    
}
