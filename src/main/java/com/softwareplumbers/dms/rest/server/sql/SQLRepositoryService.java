/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.sql;

import com.google.common.collect.Streams;
import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractquery.Query;
import com.softwareplumbers.common.pipedstream.InputStreamSupplier;
import com.softwareplumbers.dms.Constants;
import com.softwareplumbers.dms.Document;
import com.softwareplumbers.dms.DocumentLink;
import com.softwareplumbers.dms.DocumentPart;
import com.softwareplumbers.dms.Exceptions;
import com.softwareplumbers.dms.NamedRepositoryObject;
import com.softwareplumbers.dms.Options;
import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.RepositoryService;
import com.softwareplumbers.dms.Workspace;
import com.softwareplumbers.dms.common.impl.DocumentImpl;
import com.softwareplumbers.dms.common.impl.DocumentLinkImpl;
import com.softwareplumbers.dms.common.impl.LocalData;
import com.softwareplumbers.dms.common.impl.RepositoryObjectFactory;
import com.softwareplumbers.dms.common.impl.StreamInfo;
import com.softwareplumbers.dms.rest.server.model.MetadataMerge;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

/**
 *
 * @author jonathan
 */
public class SQLRepositoryService implements RepositoryService {
    
    private static XLogger LOG = XLoggerFactory.getXLogger(SQLRepositoryService.class);
    
    private Path basePath;
    private RepositoryObjectFactory factory;
    
    private SQLAPI getConnection() {
        return null;
    }
    
    private String getBaseName(JsonObject metadata) {
        return metadata.getString("DocumentTitle", "Document.dat");
    }
    
    private Path toPath(String id) {
        Path path = basePath;
        for (String elem : id.toString().split("-")) {
            basePath = basePath.resolve(elem);
        }
        return path;
    }
    
    private void fileDocument(String version, InputStreamSupplier iss) throws IOException {
        Path path = toPath(version);
        try (InputStream is = iss.get()) {
            Files.copy(is, path);
        }
    }
    
    private void linkDocument(String from, String to) throws IOException {
        Files.createLink(toPath(to), toPath(from));
    }

    private void destroyDocument(String version) throws IOException {
        Path path = toPath(version);
        Files.delete(path);
    }
    
    private void maybeDestroyDocument(String version) {
        try {
            destroyDocument(version);
        } catch (Exception e) {
            LOG.catching(e);
        }
    }

    private InputStream getDocument(String version) throws IOException {
        return Files.newInputStream(toPath(version));
    }
    



    

        
    @Override
    public Reference createDocument(String mediaType, InputStreamSupplier iss, JsonObject metadata) {
        LOG.entry(mediaType, iss, metadata);
        String version = UUID.randomUUID().toString();
        String id = UUID.randomUUID().toString();
        try (
            SQLAPI db = getConnection(); 
        ) {
            StreamInfo info = StreamInfo.of(iss);
            fileDocument(version, info.supplier);
            db.createDocument(id, version, mediaType, info.length, info.digest, metadata);
            db.commit();
            return new Reference(id, version);
        } catch (SQLException e) {
            maybeDestroyDocument(version);
            throw LOG.throwing(new RuntimeException(e));
        } catch (IOException e) {
            throw LOG.throwing(new RuntimeException(e));           
        }
    }

    @Override
    public Reference updateDocument(String id, String mediaType, InputStreamSupplier iss, JsonObject metadata) throws Exceptions.InvalidDocumentId {
        LOG.entry(id, mediaType, iss, metadata);
        String version = UUID.randomUUID().toString();
        try (
            SQLAPI db = getConnection(); 
        ) {
            Optional<Document> existing = db.getDocument(new Reference(id, null), SQLAPI::getDocument);
            if (existing.isPresent()) {
                Document existingDoc = existing.get();
                mediaType = mediaType == Constants.NO_TYPE ? existingDoc.getMediaType() : mediaType;
                long length = existingDoc.getLength();
                byte[] digest = existingDoc.getDigest();
                String replacing = existingDoc.getVersion();
                metadata = MetadataMerge.merge(existingDoc.getMetadata(), metadata);
                if (iss != null) {
                    StreamInfo info = StreamInfo.of(iss);
                    length = info.length;
                    digest = info.digest;
                    fileDocument(version, info.supplier);
                } else {
                    linkDocument(replacing, version);
                }

                db.createDocument(id, version, mediaType, length, digest, metadata);
                db.commit();
            }
        } catch (SQLException e) {
            maybeDestroyDocument(version);
            throw LOG.throwing(new RuntimeException(e));
        } catch (IOException e) {
            throw LOG.throwing(new RuntimeException(e));           
        }
        return LOG.exit(new Reference(id, version));
    }
    

    @Override
    public DocumentLink getDocumentLink(String rootId, QualifiedName workspacePath, String documentId, Options.Get... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName, Exceptions.InvalidDocumentId {
        LOG.entry(rootId, workspacePath, documentId, Options.loggable(options));
        try (
            SQLAPI db = getConnection(); 
        ) {
            Optional<DocumentLink> current = db.getDocumentLink(rootId, workspacePath, documentId, SQLAPI::getLink);
            if (current.isPresent()) {
                return LOG.exit(current.get());
            } else {
                // TODO: check if workspace path is valid and return appropriate exception
                throw LOG.throwing(new Exceptions.InvalidDocumentId(documentId));
            }
        } catch (SQLException e) {
            throw LOG.throwing(new RuntimeException(e));
        } 
    }        

    @Override
    public DocumentLink createDocumentLink(String rootId, QualifiedName path, String mediaType, InputStreamSupplier iss, JsonObject metadata, Options.Create... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName, Exceptions.InvalidWorkspaceState {
        LOG.entry(rootId, path, mediaType, iss, metadata, Options.loggable(options));
        String version = UUID.randomUUID().toString();
        String id = UUID.randomUUID().toString();
        Reference reference = new Reference(id, version);

        try (
            SQLAPI db = getConnection(); 
        ) {
            StreamInfo info = StreamInfo.of(iss);
            db.createDocument(id, version, mediaType, info.length, info.digest, metadata);
            db.createDocumentLink(rootId, path, reference, Options.CREATE_MISSING_PARENT.isIn(options));
            db.commit();
            QualifiedName fullPath = db.getPathTo(rootId).get().addAll(path);
            return new DocumentLinkImpl(fullPath, reference, mediaType, info.length, info.digest, metadata, false, LocalData.NONE);
        } catch (SQLException e) {
            maybeDestroyDocument(version);
            throw LOG.throwing(new RuntimeException(e));
        } catch (IOException e) {
            throw LOG.throwing(new RuntimeException(e));
        }    
    }

    @Override
    public DocumentLink createDocumentLinkAndName(String rootId, QualifiedName workspaceName, String mediaType, InputStreamSupplier iss, JsonObject metadata, Options.Create... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidWorkspaceState {
        
        String version = UUID.randomUUID().toString();
        String id = UUID.randomUUID().toString();
        String baseName = getBaseName(metadata);
        Reference reference = new Reference(id, version);

        try (
            SQLAPI db = getConnection(); 
        ) {    
            String folderId = db.getOrCreateFolderId(rootId, workspaceName, Options.CREATE_MISSING_PARENT.isIn(options)).orElseThrow(()->new Exceptions.InvalidWorkspace(rootId, workspaceName));
            String name = db.generateUniqueName(rootId, baseName);
            StreamInfo info = StreamInfo.of(iss);
            db.createDocument(id, version, mediaType, info.length, info.digest, metadata);
            db.createDocumentLink(folderId, name, reference);
            db.commit();               
            QualifiedName fullPath = db.getPathTo(rootId).get().addAll(workspaceName).add(name);
            return new DocumentLinkImpl(fullPath, reference, mediaType, info.length, info.digest, metadata, false, LocalData.NONE);
        } catch (SQLException e) {
            maybeDestroyDocument(version);
            throw LOG.throwing(new RuntimeException(e));
        } catch (IOException e) {
            throw LOG.throwing(new RuntimeException(e));            
        }
    }

    @Override
    public DocumentLink createDocumentLinkAndName(String rootId, QualifiedName workspaceName, Reference reference, Options.Create... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidWorkspaceState, Exceptions.InvalidReference {
        try (
            SQLAPI db = getConnection(); 
        ) {    
            String folderId = db.getOrCreateFolderId(rootId, workspaceName, Options.CREATE_MISSING_PARENT.isIn(options)).orElseThrow(()->new Exceptions.InvalidWorkspace(rootId, workspaceName));
            Document document = db.getDocument(reference, SQLAPI::getDocument).orElseThrow(()->new Exceptions.InvalidReference(reference));
            String name = db.generateUniqueName(rootId, getBaseName(document.getMetadata()));
            db.createDocumentLink(folderId, name, reference);
            db.commit();               
            QualifiedName fullPath = db.getPathTo(rootId).get().addAll(workspaceName).add(name);
            return new DocumentLinkImpl(fullPath, reference, document.getMediaType(), document.getLength(), document.getDigest(), document.getMetadata(), false, LocalData.NONE);
        } catch (SQLException e) {
            throw LOG.throwing(new RuntimeException(e));
        }
    }

    @Override
    public DocumentLink createDocumentLink(String rootId, QualifiedName path, Reference reference, Options.Create... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidReference, Exceptions.InvalidObjectName, Exceptions.InvalidWorkspaceState {
        try (
            SQLAPI db = getConnection(); 
        ) {    
            String folderId = db.getOrCreateFolderId(rootId, path.parent, Options.CREATE_MISSING_PARENT.isIn(options)).orElseThrow(()->new Exceptions.InvalidWorkspace(rootId, path.parent));
            Document document = db.getDocument(reference, SQLAPI::getDocument).orElseThrow(()->new Exceptions.InvalidReference(reference));
            db.createDocumentLink(folderId, path.part, reference);
            db.commit();               
            QualifiedName fullPath = db.getPathTo(rootId).get().addAll(path);
            return new DocumentLinkImpl(fullPath, reference, document.getMediaType(), document.getLength(), document.getDigest(), document.getMetadata(), false, LocalData.NONE);
        } catch (SQLException e) {
            throw LOG.throwing(new RuntimeException(e));
        }
    }

    @Override
    public DocumentLink updateDocumentLink(String rootId, QualifiedName path, String mediaType, InputStreamSupplier iss, JsonObject metadata, Options.Update... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName, Exceptions.InvalidWorkspaceState {
        
        boolean createMissing = Options.CREATE_MISSING_ITEM.isIn(options);
        String version = UUID.randomUUID().toString();

        try (
            SQLAPI db = getConnection(); 
        ) {    
            String folderId = db.getOrCreateFolderId(rootId, path.parent, Options.CREATE_MISSING_PARENT.isIn(options)).orElseThrow(()->new Exceptions.InvalidWorkspace(rootId, path.parent));
            Optional<DocumentLink> existing = db.getDocumentLink(folderId, path.part, SQLAPI::getLink);
            if (existing.isPresent()) {
                DocumentLink existingDoc = existing.get();
                mediaType = mediaType == Constants.NO_TYPE ? existingDoc.getMediaType() : mediaType;
                long length = existingDoc.getLength();
                byte[] digest = existingDoc.getDigest();
                Reference replacing = existingDoc.getReference();
                metadata = MetadataMerge.merge(existingDoc.getMetadata(), metadata);
                if (iss != null) {
                    StreamInfo info = StreamInfo.of(iss);
                    length = info.length;
                    digest = info.digest;
                    fileDocument(version, info.supplier);
                } else {
                    linkDocument(replacing.id, version);
                }                
                
                db.createDocument(replacing.id, version, mediaType, length, digest, metadata);
                Reference newReference = new Reference(replacing.id, version);
                db.updateDocumentLink(folderId, path.part, newReference, createMissing);
                db.commit();           
                QualifiedName fullPath = db.getPathTo(rootId).get().addAll(path);
                return new DocumentLinkImpl(fullPath, newReference, mediaType, length, digest, metadata, false, LocalData.NONE);
            } else {
                throw new Exceptions.InvalidObjectName(rootId, path);
            }
        } catch (SQLException e) {
            throw LOG.throwing(new RuntimeException(e));
        } catch (IOException e) {
            throw LOG.throwing(new RuntimeException(e));            
        }
    }

    @Override
    public DocumentLink updateDocumentLink(String rootId, QualifiedName path, Reference reference, Options.Update... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName, Exceptions.InvalidWorkspaceState, Exceptions.InvalidReference {
        try (
            SQLAPI db = getConnection(); 
        ) {    
            String folderId = db.getOrCreateFolderId(rootId, path.parent, Options.CREATE_MISSING_PARENT.isIn(options)).orElseThrow(()->new Exceptions.InvalidWorkspace(rootId, path.parent));
            Document document = db.getDocument(reference, SQLAPI::getDocument).orElseThrow(()->new Exceptions.InvalidReference(reference));
            db.updateDocumentLink(folderId, path.part, reference, Options.CREATE_MISSING_ITEM.isIn(options));
            db.commit();               
            QualifiedName fullPath = db.getPathTo(rootId).get().addAll(path);
            return new DocumentLinkImpl(fullPath, reference, document.getMediaType(), document.getLength(), document.getDigest(), document.getMetadata(), false, LocalData.NONE);
        } catch (SQLException e) {
            throw LOG.throwing(new RuntimeException(e));
        }
    }

    @Override
    public NamedRepositoryObject copyObject(String string, QualifiedName qn, String string1, QualifiedName qn1, boolean bln) throws Exceptions.InvalidWorkspaceState, Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public DocumentLink copyDocumentLink(String string, QualifiedName qn, String string1, QualifiedName qn1, boolean bln) throws Exceptions.InvalidWorkspaceState, Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Workspace copyWorkspace(String string, QualifiedName qn, String string1, QualifiedName qn1, boolean bln) throws Exceptions.InvalidWorkspaceState, Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Workspace createWorkspaceByName(String string, QualifiedName qn, Workspace.State state, JsonObject jo, Options.Create... creates) throws Exceptions.InvalidWorkspaceState, Exceptions.InvalidWorkspace {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Workspace createWorkspaceAndName(String string, QualifiedName qn, Workspace.State state, JsonObject jo, Options.Create... creates) throws Exceptions.InvalidWorkspaceState, Exceptions.InvalidWorkspace {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Workspace updateWorkspaceByName(String string, QualifiedName qn, Workspace.State state, JsonObject jo, Options.Update... updates) throws Exceptions.InvalidWorkspace {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void deleteDocument(String string, QualifiedName qn, String string1) throws Exceptions.InvalidWorkspace, Exceptions.InvalidDocumentId, Exceptions.InvalidWorkspaceState {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void deleteObjectByName(String string, QualifiedName qn) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName, Exceptions.InvalidWorkspaceState {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <T> Optional<T> getImplementation(Class<T> type) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Document getDocument(Reference rfrnc) throws Exceptions.InvalidReference {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public DocumentPart getPart(Reference rfrnc, QualifiedName qn) throws Exceptions.InvalidReference, Exceptions.InvalidObjectName {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public InputStream getData(Reference rfrnc, Optional<QualifiedName> optnl) throws Exceptions.InvalidReference, Exceptions.InvalidObjectName, IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void writeData(Reference rfrnc, Optional<QualifiedName> optnl, OutputStream out) throws Exceptions.InvalidReference, Exceptions.InvalidObjectName, IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public InputStream getData(String string, QualifiedName qn, Options.Get... gets) throws Exceptions.InvalidObjectName, IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void writeData(String string, QualifiedName qn, OutputStream out, Options.Get... gets) throws Exceptions.InvalidObjectName, IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Stream<DocumentPart> catalogueParts(Reference rfrnc, QualifiedName qn) throws Exceptions.InvalidReference, Exceptions.InvalidObjectName {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public DocumentLink getDocumentLink(String string, QualifiedName qn, Options.Get... gets) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Stream<Document> catalogue(Query query, boolean bln) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Stream<NamedRepositoryObject> catalogueById(String string, QualifiedName qn, String string1, Query query, Options.Search... searchs) throws Exceptions.InvalidWorkspace {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Stream<NamedRepositoryObject> catalogueByName(String string, QualifiedName qn, Query query, Options.Search... searchs) throws Exceptions.InvalidWorkspace {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Stream<Document> catalogueHistory(Reference rfrnc, Query query) throws Exceptions.InvalidReference {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public NamedRepositoryObject getObjectByName(String string, QualifiedName qn, Options.Get... gets) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Workspace getWorkspaceByName(String string, QualifiedName qn) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Stream<DocumentLink> listWorkspaces(String string, QualifiedName qn, Query query, Options.Search... searchs) throws Exceptions.InvalidDocumentId {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
