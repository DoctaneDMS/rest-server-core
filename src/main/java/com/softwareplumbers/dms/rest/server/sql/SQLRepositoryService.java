/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.sql;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractpattern.parsers.Parsers;
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
import com.softwareplumbers.dms.common.impl.DocumentLinkImpl;
import com.softwareplumbers.dms.common.impl.LocalData;
import com.softwareplumbers.dms.common.impl.RepositoryObjectFactory;
import com.softwareplumbers.dms.common.impl.StreamInfo;
import com.softwareplumbers.dms.rest.server.model.MetadataMerge;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 *
 * @author jonathan
 */
public class SQLRepositoryService implements RepositoryService {
    
    private static XLogger LOG = XLoggerFactory.getXLogger(SQLRepositoryService.class);
    
    private final Path basePath;
    private final RepositoryObjectFactory factory = new RepositoryObjectFactory();
    private final SQLAPIFactory dbFactory;
      
    private String getBaseName(JsonObject metadata) {
        return metadata.getString("DocumentTitle", "Document.dat");
    }
        
    private Path toPath(Id id) {
        Path path = basePath;
        for (String elem : id.toString().split("-")) {
            path = path.resolve(elem);
        }
        return path;
    }
    
    private void fileDocument(Id version, InputStreamSupplier iss) throws IOException {
        Path path = toPath(version);
        try (InputStream is = iss.get()) {
            Files.createDirectories(path.getParent());
            Files.copy(is, path);
        }
    }
    
    private void linkDocument(Id from, Id to) throws IOException {
        Path toPath = toPath(to);
        Files.createDirectories(toPath.getParent());
        Files.createLink(toPath, toPath(from));
    }

    private void destroyDocument(Id version) throws IOException {
        Path path = toPath(version);
        Files.delete(path);
    }
    
    private void maybeDestroyDocument(Id version) {
        try {
            destroyDocument(version);
        } catch (Exception e) {
            LOG.catching(e);
        }
    }

    private InputStream getDocument(Id version) throws IOException {
        return Files.newInputStream(toPath(version));
    }
       
    @Autowired
    public SQLRepositoryService(SQLAPIFactory dbFactory, Path basePath) {
        this.dbFactory = dbFactory;
        this.basePath = basePath;
    }
    
    @Override
    public Reference createDocument(String mediaType, InputStreamSupplier iss, JsonObject metadata) {
        LOG.entry(mediaType, iss, metadata);
        Id version = new Id();
        Id id = new Id();
        try (
            SQLAPI db = dbFactory.getSQLAPI();
        ) {
            StreamInfo info = StreamInfo.of(iss);
            fileDocument(version, info.supplier);
            db.createDocument(id, version, mediaType, info.length, info.digest, metadata);
            db.commit();
            return new Reference(id.toString(), version.toString());
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
        Id version = new Id();
        try (
            SQLAPI db = dbFactory.getSQLAPI(); 
        ) {
            Optional<Document> existing = db.getDocument(new Id(id), null, SQLAPI.GET_DOCUMENT);
            if (existing.isPresent()) {
                Document existingDoc = existing.get();
                mediaType = mediaType == Constants.NO_TYPE ? existingDoc.getMediaType() : mediaType;
                long length = existingDoc.getLength();
                byte[] digest = existingDoc.getDigest();
                Id replacing = new Id(existingDoc.getVersion());
                metadata = MetadataMerge.merge(existingDoc.getMetadata(), metadata);
                if (iss != null) {
                    StreamInfo info = StreamInfo.of(iss);
                    length = info.length;
                    digest = info.digest;
                    fileDocument(version, info.supplier);
                } else {
                    linkDocument(replacing, version);
                }

                db.createDocument(new Id(id), version, mediaType, length, digest, metadata);
                db.commit();
            }
        } catch (SQLException e) {
            maybeDestroyDocument(version);
            throw LOG.throwing(new RuntimeException(e));
        } catch (IOException e) {
            throw LOG.throwing(new RuntimeException(e));           
        }
        return LOG.exit(new Reference(id, version.toString()));
    }
    

    @Override
    public DocumentLink getDocumentLink(String rootId, QualifiedName workspacePath, String documentId, Options.Get... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName, Exceptions.InvalidDocumentId {
        LOG.entry(rootId, workspacePath, documentId, Options.loggable(options));
        try (
            SQLAPI db = dbFactory.getSQLAPI(); 
        ) {
            Id root = Id.of(rootId);
            QualifiedName rootPath = db.getPathTo(root).orElseThrow(()->new Exceptions.InvalidWorkspace(rootId));
            Optional<DocumentLink> current = db.getDocumentLink(root, workspacePath, Id.of(documentId), rs->SQLAPI.getLink(rs, rootPath));
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
        Id version = new Id();
        Id id = new Id();
        Reference reference = new Reference(id.toString(), version.toString());
        if (metadata == null) metadata = JsonObject.EMPTY_JSON_OBJECT;

        try (
            SQLAPI db = dbFactory.getSQLAPI(); 
        ) {
            StreamInfo info = StreamInfo.of(iss);
            db.createDocument(id, version, mediaType, info.length, info.digest, metadata);
            Id idRoot = Id.of(rootId);
            QualifiedName baseName = db.getPathTo(idRoot)
                .orElseThrow(()->new Exceptions.InvalidWorkspace(rootId))
                .addAll(path.parent);            
            DocumentLink result = db.createDocumentLink(idRoot, path, id, version, Options.CREATE_MISSING_PARENT.isIn(options), rs->SQLAPI.getLink(rs, baseName));
            db.commit();
            return result;
        } catch (SQLException e) {
            maybeDestroyDocument(version);
            throw LOG.throwing(new RuntimeException(e));
        } catch (IOException e) {
            throw LOG.throwing(new RuntimeException(e));
        }    
    }

    @Override
    public DocumentLink createDocumentLinkAndName(String rootId, QualifiedName workspaceName, String mediaType, InputStreamSupplier iss, JsonObject metadata, Options.Create... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidWorkspaceState {
        
        Id version = new Id();
        Id id = new Id();
        String baseName = getBaseName(metadata);
        Reference reference = new Reference(id.toString(), version.toString());

        try (
            SQLAPI db = dbFactory.getSQLAPI(); 
        ) {    
            Id folderId = db.getOrCreateFolder(Id.of(rootId), workspaceName, Options.CREATE_MISSING_PARENT.isIn(options), SQLAPI.GET_ID).orElseThrow(()->new Exceptions.InvalidWorkspace(rootId, workspaceName));
            String name = db.generateUniqueName(folderId, baseName);
            StreamInfo info = StreamInfo.of(iss);
            db.createDocument(id, version, mediaType, info.length, info.digest, metadata);
            Id idRoot = Id.of(rootId);
            QualifiedName fullWorkspaceName = db.getPathTo(idRoot)
                .orElseThrow(()->new Exceptions.InvalidWorkspace(rootId))
                .addAll(workspaceName);            
            DocumentLink result = db.createDocumentLink(folderId, name, id, version, rs->SQLAPI.getLink(rs, fullWorkspaceName));
            db.commit();
            return result;        
        
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
            SQLAPI db = dbFactory.getSQLAPI(); 
        ) {    
            Id root = Id.of(rootId);
            Id folderId = db.getOrCreateFolder(root, workspaceName, Options.CREATE_MISSING_PARENT.isIn(options), SQLAPI.GET_ID).orElseThrow(()->new Exceptions.InvalidWorkspace(rootId, workspaceName));
            Id docId = Id.ofDocument(reference.id);
            Id versionId = Id.ofVersion(reference.version);
            Document document = db.getDocument(docId, versionId, SQLAPI.GET_DOCUMENT).orElseThrow(()->new Exceptions.InvalidReference(reference));
            String name = db.generateUniqueName(folderId, getBaseName(document.getMetadata()));
            Id idRoot = Id.of(rootId);
            QualifiedName fullWorkspaceName = db.getPathTo(idRoot)
                .orElseThrow(()->new Exceptions.InvalidWorkspace(rootId))
                .addAll(workspaceName);            
            DocumentLink result = db.createDocumentLink(folderId, name, docId, versionId, rs->SQLAPI.getLink(rs, fullWorkspaceName));
            db.commit();               
            return result;
        } catch (SQLException e) {
            throw LOG.throwing(new RuntimeException(e));
        }
    }

    @Override
    public DocumentLink createDocumentLink(String rootId, QualifiedName path, Reference reference, Options.Create... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidReference, Exceptions.InvalidObjectName, Exceptions.InvalidWorkspaceState {
        try (
            SQLAPI db = dbFactory.getSQLAPI(); 
        ) {    
            Id root = Id.of(rootId);
            Id folderId = db.getOrCreateFolder(root, path.parent, Options.CREATE_MISSING_PARENT.isIn(options), SQLAPI.GET_ID).orElseThrow(()->new Exceptions.InvalidWorkspace(rootId, path.parent));
            Id docId = Id.ofDocument(reference.id);
            Id versionId = Id.ofVersion(reference.version);
            Document document = db.getDocument(docId, versionId, SQLAPI.GET_DOCUMENT).orElseThrow(()->new Exceptions.InvalidReference(reference));
            Id idRoot = Id.of(rootId);
            QualifiedName baseName = db.getPathTo(idRoot)
                .orElseThrow(()->new Exceptions.InvalidWorkspace(rootId))
                .addAll(path.parent);            
            DocumentLink result = db.createDocumentLink(folderId, path.part, docId, versionId,rs->SQLAPI.getLink(rs, baseName));
            db.commit();
            return result;
        } catch (SQLException e) {
            throw LOG.throwing(new RuntimeException(e));
        }
    }

    @Override
    public DocumentLink updateDocumentLink(String rootId, QualifiedName path, String mediaType, InputStreamSupplier iss, JsonObject metadata, Options.Update... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName, Exceptions.InvalidWorkspaceState {
        
        boolean createMissing = Options.CREATE_MISSING_ITEM.isIn(options);
        Id version = new Id();

        try (
            SQLAPI db = dbFactory.getSQLAPI(); 
        ) {    
            Id root = Id.of(rootId);
            Id folderId = db.getOrCreateFolder(root, path.parent, Options.CREATE_MISSING_PARENT.isIn(options), SQLAPI.GET_ID).orElseThrow(()->new Exceptions.InvalidWorkspace(rootId, path.parent));
            QualifiedName rootPath = db.getPathTo(root).orElseThrow(()->new Exceptions.InvalidWorkspace(rootId));
            Optional<DocumentLink> existing = db.getDocumentLink(folderId, QualifiedName.of(path.part), rs->SQLAPI.getLink(rs, rootPath));
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
                    linkDocument(Id.ofDocument(replacing.version), version);
                }                
                
                Id replacingId = Id.ofDocument(replacing.id);
                db.createDocument(replacingId, version, mediaType, length, digest, metadata);
                Reference newReference = new Reference(replacing.id, version.toString());
                QualifiedName fullPath = db.getPathTo(root).get().addAll(path);
                DocumentLink result = db.updateDocumentLink(folderId, path.part, replacingId, version, createMissing, rs->SQLAPI.getLink(rs, fullPath));
                db.commit();           
                return result;
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
            SQLAPI db = dbFactory.getSQLAPI(); 
        ) {    
            Id root = Id.of(rootId);
            Id docId = Id.ofDocument(reference.id);
            Id versionId = Id.ofVersion(reference.version);
            Id folderId = db.getOrCreateFolder(root, path.parent, Options.CREATE_MISSING_PARENT.isIn(options), SQLAPI.GET_ID).orElseThrow(()->new Exceptions.InvalidWorkspace(rootId, path.parent));
            Document document = db.getDocument(docId, versionId, SQLAPI.GET_DOCUMENT).orElseThrow(()->new Exceptions.InvalidReference(reference));
            QualifiedName fullPath = db.getPathTo(root).get().addAll(path);
            DocumentLink result = db.updateDocumentLink(folderId, path.part, docId, versionId, Options.CREATE_MISSING_ITEM.isIn(options), rs->SQLAPI.getLink(rs, fullPath));
            db.commit();               
            return new DocumentLinkImpl(fullPath, reference, document.getMediaType(), document.getLength(), document.getDigest(), document.getMetadata(), false, LocalData.NONE);
        } catch (SQLException e) {
            throw LOG.throwing(new RuntimeException(e));
        }
    }

    
    
    @Override
    public NamedRepositoryObject copyObject(String rootId, QualifiedName path, String targetId, QualifiedName targetPath, boolean createParent) throws Exceptions.InvalidWorkspaceState, Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName {
        LOG.entry(rootId, path, path, targetPath, createParent);
        try (
            SQLAPI db = dbFactory.getSQLAPI(); 
        ) { 
            Info info = db.getInfo(Id.of(rootId), path, SQLAPI.GET_INFO).orElseThrow(()->new Exceptions.InvalidObjectName(rootId, path));
            switch (info.type) {
                case DOCUMENT_LINK: copyDocumentLink(rootId, path, targetId, targetPath, createParent);
                case WORKSPACE: copyWorkspace(rootId, path, targetId, targetPath, createParent);
                default:
                    throw LOG.throwing(new RuntimeException("Don't know how to copy " + info.type.toString()));
            }
        } catch (SQLException e) {
            throw LOG.throwing(new RuntimeException(e));
        }
    }
    
    @Override
    public DocumentLink copyDocumentLink(String rootId, QualifiedName path, String targetId, QualifiedName targetPath, boolean createParent) throws Exceptions.InvalidWorkspaceState, Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName {
        LOG.entry(rootId, path, targetId, targetPath, createParent);
        try (
            SQLAPI db = dbFactory.getSQLAPI(); 
        ) { 
            Id idTarget = Id.of(targetId);
            QualifiedName baseName = db.getPathTo(idTarget)
                .orElseThrow(()->new Exceptions.InvalidWorkspace(targetId))
                .addAll(targetPath.parent);
            DocumentLink result = db.copyDocumentLink(Id.of(rootId), path, idTarget, targetPath, createParent, rs->SQLAPI.getLink(rs, baseName));
            db.commit();
            return LOG.exit(result);
        } catch (SQLException e) {
            throw LOG.throwing(new RuntimeException(e));
        }
    }

    @Override
    public Workspace copyWorkspace(String rootId, QualifiedName path, String targetId, QualifiedName targetPath, boolean createParent) throws Exceptions.InvalidWorkspaceState, Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName {
        LOG.entry(rootId, path, targetId, targetPath, createParent);
        try (
            SQLAPI db = dbFactory.getSQLAPI(); 
        ) { 
            Id idTarget = Id.of(targetId);
            QualifiedName baseName = db.getPathTo(idTarget)
                .orElseThrow(()->new Exceptions.InvalidWorkspace(targetId))
                .addAll(targetPath.parent);
            Workspace result = db.copyFolder(Id.of(rootId), path, idTarget, targetPath, createParent, rs->SQLAPI.getWorkspace(rs, baseName));
            db.commit();
            return LOG.exit(result);
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new Exceptions.InvalidWorkspace(targetId, targetPath);
        } catch (SQLException e) {
            throw LOG.throwing(new RuntimeException(e));
        }  
    }

    @Override
    public Workspace createWorkspaceByName(String rootId, QualifiedName path, Workspace.State state, JsonObject metadata, Options.Create... options) throws Exceptions.InvalidWorkspaceState, Exceptions.InvalidWorkspace {
        LOG.entry(rootId, path, state, metadata, Options.loggable(options));
        try (
            SQLAPI db = dbFactory.getSQLAPI(); 
        ) {
            
            Id parent_id = path.parent.isEmpty() 
                ? Id.of(rootId)
                : db.getOrCreateFolder(Id.of(rootId), path.parent, Options.CREATE_MISSING_PARENT.isIn(options), SQLAPI.GET_ID).orElseThrow(()->new Exceptions.InvalidWorkspace(rootId, path.parent));
            Workspace result = db.createFolder(parent_id, path.part, state, metadata, rs->SQLAPI.getWorkspace(rs, path.parent));
            db.commit();
            return result;
        } catch (SQLException e) {
            throw LOG.throwing(new RuntimeException(e));
        }    
    }

    @Override
    public Workspace createWorkspaceAndName(String rootId, QualifiedName workspacePath, Workspace.State state, JsonObject metadata, Options.Create... options) throws Exceptions.InvalidWorkspaceState, Exceptions.InvalidWorkspace {
        LOG.entry(rootId, workspacePath, state, metadata, Options.loggable(options));
        try (
            SQLAPI db = dbFactory.getSQLAPI(); 
        ) {    
            Id root = Id.of(rootId);
            Id folderId = workspacePath.isEmpty() 
                ? root 
                : db.getOrCreateFolder(root, workspacePath, Options.CREATE_MISSING_PARENT.isIn(options), SQLAPI.GET_ID)
                    .orElseThrow(()->new Exceptions.InvalidWorkspace(rootId, workspacePath));
            String name = db.generateUniqueName(folderId, getBaseName(metadata));
            QualifiedName fullWorkspaceName = db.getPathTo(root)
                .orElseThrow(()->new Exceptions.InvalidWorkspace(rootId))
                .addAll(workspacePath);            
            Workspace result = db.createFolder(folderId, name, state, metadata, rs->SQLAPI.getWorkspace(rs, fullWorkspaceName));
            db.commit();               
            return LOG.exit(result);
        } catch (SQLException e) {
            throw LOG.throwing(new RuntimeException(e));
        }
    }

    @Override
    public Workspace updateWorkspaceByName(String rootId, QualifiedName path, Workspace.State state, JsonObject metadata, Options.Update... options) throws Exceptions.InvalidWorkspace {
        LOG.entry(rootId, path, state, metadata, Options.loggable(options));
        boolean createMissing = Options.CREATE_MISSING_ITEM.isIn(options);

        try (
            SQLAPI db = dbFactory.getSQLAPI(); 
        ) {    
            Id root = Id.of(rootId);
 
            Optional<Workspace> existing = db.getFolder(root, path, db::getWorkspace);
             
            if (existing.isPresent()) {
                Workspace existingWorkspace = existing.get();
                state = state == null ? existingWorkspace.getState() : state;
                metadata = MetadataMerge.merge(existingWorkspace.getMetadata(), metadata);
                Workspace result = db.updateFolder(Id.of(existingWorkspace.getId()), state, metadata, rs->SQLAPI.getWorkspace(rs, existingWorkspace.getName().parent))
                    .orElseThrow(()->LOG.throwing(new Exceptions.InvalidWorkspace(rootId, path)));
                db.commit();           
                return LOG.exit(result);
            } else {
                if (createMissing && !path.isEmpty()) { // can't create a folder without a path
                    Id parentId = db.getOrCreateFolder(root, path.parent, Options.CREATE_MISSING_PARENT.isIn(options), SQLAPI.GET_ID)
                        .orElseThrow(()->LOG.throwing(new Exceptions.InvalidWorkspace(rootId, path)));
                    return db.createFolder(parentId, path.part, state, metadata, rs->SQLAPI.getWorkspace(rs, path.parent));
                } else {
                    throw LOG.throwing(new Exceptions.InvalidWorkspace(rootId, path));
                }
            }
        } catch (SQLException e) {
            throw LOG.throwing(new RuntimeException(e));
        } 
    }

    @Override
    public void deleteDocument(String rootId, QualifiedName workspacePath, String documentId) throws Exceptions.InvalidWorkspace, Exceptions.InvalidDocumentId, Exceptions.InvalidWorkspaceState {
        LOG.entry(rootId, workspacePath, documentId);
        try (
            SQLAPI db = dbFactory.getSQLAPI(); 
        ) {
            db.deleteDocumentById(Id.of(rootId), workspacePath, Id.ofDocument(documentId));
            db.commit();
            LOG.exit();
        } catch (SQLException e) {
            throw LOG.throwing(new RuntimeException(e));
        }
    }

    @Override
    public void deleteObjectByName(String rootId, QualifiedName path) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName, Exceptions.InvalidWorkspaceState {
        LOG.entry(rootId, path);
        try (
            SQLAPI db = dbFactory.getSQLAPI(); 
        ) {
            db.deleteObject(Id.of(rootId), path);
            db.commit();
            LOG.exit();
        } catch (SQLException e) {
            throw LOG.throwing(new RuntimeException(e));
        }
    }

    @Override
    public <T> Optional<T> getImplementation(Class<T> type) {
        if (type.isAssignableFrom(this.getClass())) return Optional.of((T)this);
        return Optional.empty();
    }

    @Override
    public Document getDocument(Reference reference) throws Exceptions.InvalidReference {
        LOG.entry(reference);
        try (
            SQLAPI db = dbFactory.getSQLAPI(); 
        ) {
            return LOG.exit(db.getDocument(Id.ofDocument(reference.id), Id.ofVersion(reference.version), SQLAPI.GET_DOCUMENT)
                .orElseThrow(()->new Exceptions.InvalidReference(reference))
            );
        } catch (SQLException e) {
            throw LOG.throwing(new RuntimeException(e));
        }
    }

    @Override
    public DocumentPart getPart(Reference rfrnc, QualifiedName qn) throws Exceptions.InvalidReference, Exceptions.InvalidObjectName {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public InputStream getData(Reference reference, Optional<QualifiedName> part) throws Exceptions.InvalidReference, Exceptions.InvalidObjectName, IOException {
        LOG.entry(reference, part);
        return LOG.exit(Files.newInputStream(toPath(Id.ofVersion(reference.version))));
    }

    @Override
    public void writeData(Reference reference, Optional<QualifiedName> part, OutputStream out) throws Exceptions.InvalidReference, Exceptions.InvalidObjectName, IOException {
        LOG.entry(reference, part, out);
        Files.copy(toPath(Id.ofVersion(reference.version)), out);
        LOG.exit();
    }

    @Override
    public InputStream getData(String rootId, QualifiedName path, Options.Get... options) throws Exceptions.InvalidObjectName, IOException {
        LOG.entry(rootId, path, Options.loggable(options));
        try (
            SQLAPI db = dbFactory.getSQLAPI(); 
        ) {
            DocumentLink link = db.getDocumentLink(Id.of(rootId), path, rs->SQLAPI.getLink(rs, path.parent))
                .orElseThrow(()->new Exceptions.InvalidObjectName(rootId, path));
            return LOG.exit(Files.newInputStream(toPath(Id.ofVersion(link.getVersion()))));
        } catch (SQLException e) {
            throw LOG.throwing(new RuntimeException(e));
        }
    }

    @Override
    public void writeData(String rootId, QualifiedName path, OutputStream out, Options.Get... options) throws Exceptions.InvalidObjectName, IOException {
        LOG.entry(rootId, path, out, Options.loggable(options));
        try (
            SQLAPI db = dbFactory.getSQLAPI(); 
        ) {
            DocumentLink link = db.getDocumentLink(Id.of(rootId), path, rs->SQLAPI.getLink(rs, path.parent))
                .orElseThrow(()->new Exceptions.InvalidObjectName(rootId, path));
            Files.copy(toPath(Id.ofVersion(link.getVersion())), out);
            LOG.exit();
        } catch (SQLException e) {
            throw LOG.throwing(new RuntimeException(e));
        }
    }

    @Override
    public Stream<DocumentPart> catalogueParts(Reference rfrnc, QualifiedName qn) throws Exceptions.InvalidReference, Exceptions.InvalidObjectName {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public DocumentLink getDocumentLink(String rootId, QualifiedName path, Options.Get... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName {
        LOG.entry(rootId, path, Options.loggable(options));
        try (
            SQLAPI db = dbFactory.getSQLAPI(); 
        ) {
            Id id = Id.of(rootId);
            QualifiedName baseName = db.getPathTo(id)
                .orElseThrow(()->new Exceptions.InvalidWorkspace(rootId));
            DocumentLink link = db.getDocumentLink(id, path, rs->SQLAPI.getLink(rs, baseName.addAll(path.parent)))
                .orElseThrow(()->new Exceptions.InvalidObjectName(rootId, path));
            return LOG.exit(link);
        } catch (SQLException e) {
            throw LOG.throwing(new RuntimeException(e));
        }
    }

    @Override
    public Stream<Document> catalogue(Query query, boolean searchHistory) {
        LOG.entry(query, searchHistory);
        try (
            SQLAPI db = dbFactory.getSQLAPI(); 
        ) {            
            Stream<Document> docs = db.getDocuments(query, searchHistory, SQLAPI.GET_DOCUMENT)
                .filter(link->query.containsItem(link.toJson(this,0,1)));
            return LOG.exit(docs);
        } catch (SQLException e) {
            throw LOG.throwing(new RuntimeException(e));
        }
    }

    @Override
    public Stream<NamedRepositoryObject> catalogueById(String rootId, QualifiedName workspacePath, String documentId, Query query, Options.Search... options) throws Exceptions.InvalidWorkspace {
         LOG.entry(rootId, workspacePath, documentId, query, Options.loggable(options));
        try (
            SQLAPI db = dbFactory.getSQLAPI(); 
        ) {
            Id id = Id.of(rootId);
            QualifiedName baseName = db.getPathTo(id)
                .orElseThrow(()->new Exceptions.InvalidWorkspace(rootId));
            Stream<NamedRepositoryObject> links = db.getDocumentLinks(id, workspacePath, Id.of(documentId), query, rs->SQLAPI.getLink(rs, baseName.addAll(workspacePath)))
                .filter(link->query.containsItem(link.toJson(this,0,1)))
                .map(NamedRepositoryObject.class::cast);
            return LOG.exit(links);
        } catch (SQLException e) {
            throw LOG.throwing(new RuntimeException(e));
        }
    }

    @Override
    public Stream<NamedRepositoryObject> catalogueByName(String rootId, QualifiedName path, Query query, Options.Search... options) throws Exceptions.InvalidWorkspace {
        LOG.entry(rootId, path, Options.loggable(options));
        
        if (!Options.NO_IMPLICIT_WILDCARD.isIn(options)) {
            Predicate<String> hasWildcards = element -> !Parsers.parseUnixWildcard(element).isSimple();
            if (path.isEmpty() || path.indexFromEnd(hasWildcards) < 0) {
                path = path.add("*");
            }
        }
		
        try (
            SQLAPI db = dbFactory.getSQLAPI(); 
        ) {
            Id id = Id.of(rootId);
            QualifiedName baseName = db.getPathTo(id)
                .orElseThrow(()->new Exceptions.InvalidWorkspace(rootId));
            Stream<NamedRepositoryObject> links = db.getDocumentLinks(id, path, query, rs->SQLAPI.getLink(rs, baseName))
                .filter(link->query.containsItem(link.toJson(this,1,0)))
                .map(NamedRepositoryObject.class::cast);
            Stream<NamedRepositoryObject> workspaces = db.getFolders(id, path, query, rs->SQLAPI.getWorkspace(rs, baseName))
                .filter(link->query.containsItem(link.toJson(this,1,0)))
                .map(NamedRepositoryObject.class::cast);
            return LOG.exit(Stream.concat(links, workspaces));
        } catch (SQLException e) {
            throw LOG.throwing(new RuntimeException(e));
        }
    }

    @Override
    public Stream<Document> catalogueHistory(Reference reference, Query query) throws Exceptions.InvalidReference {
        LOG.entry(reference, query);
        try (
            SQLAPI db = dbFactory.getSQLAPI(); 
        ) {            
            Stream<Document> docs = db.getDocuments(Id.ofDocument(reference.id), query, SQLAPI.GET_DOCUMENT)
                .filter(link->query.containsItem(link.toJson(this,0,1)));
            return LOG.exit(docs);
        } catch (SQLException e) {
            throw LOG.throwing(new RuntimeException(e));
        }
    }

    @Override
    public NamedRepositoryObject getObjectByName(String rootId, QualifiedName path, Options.Get... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName {
        LOG.entry(rootId, path);
        try (
            SQLAPI db = dbFactory.getSQLAPI(); 
        ) {
            Id id = Id.of(rootId);
            QualifiedName baseName = db.getPathTo(id)
                .orElseThrow(()->new Exceptions.InvalidWorkspace(rootId))
                .addAll(path.isEmpty() ? path : path.parent);
            Info info = db.getInfo(id,path,SQLAPI.GET_INFO).orElseThrow(()->new Exceptions.InvalidObjectName(rootId, path));
            switch(info.type) {
                case WORKSPACE:
                    return LOG.exit(db.getFolder(info.parent_id, QualifiedName.of(info.name), rs->SQLAPI.getWorkspace(rs, baseName))
                        .orElseThrow(()->new Exceptions.InvalidObjectName(rootId, path)));
                case DOCUMENT_LINK:
                    return LOG.exit(db.getDocumentLink(info.parent_id, QualifiedName.of(info.name), rs->SQLAPI.getLink(rs, baseName))
                        .orElseThrow(()->new Exceptions.InvalidObjectName(rootId, path)));
                default:
                    throw LOG.throwing(new RuntimeException("Don't know how to get " + info.type));
            }

        } catch (SQLException e) {
            throw LOG.throwing(new RuntimeException(e));
        }
    }

    @Override
    public Workspace getWorkspaceByName(String rootId, QualifiedName path) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName {
        LOG.entry(rootId, path);
        try (
            SQLAPI db = dbFactory.getSQLAPI(); 
        ) {
            Id id = Id.of(rootId);
            QualifiedName baseName = db.getPathTo(id)
                .orElseThrow(()->new Exceptions.InvalidWorkspace(rootId));
            Workspace workspace = db.getFolder(Id.of(rootId), path, rs->SQLAPI.getWorkspace(rs, baseName.addAll(path.parent)))
                .orElseThrow(()->new Exceptions.InvalidWorkspace(rootId, path));
            return LOG.exit(workspace);
        } catch (SQLException e) {
            throw LOG.throwing(new RuntimeException(e));
        }
    }

    @Override
    public Stream<DocumentLink> listWorkspaces(String documentId, QualifiedName pathFilter, Query filter, Options.Search... options) throws Exceptions.InvalidDocumentId {
        LOG.entry(pathFilter, documentId, filter, Options.loggable(options));
        try (
            SQLAPI db = dbFactory.getSQLAPI(); 
        ) {
            Stream<DocumentLink> links = db.getDocumentLinks(pathFilter, Id.of(documentId), filter, db::getLink)
                .filter(link->filter.containsItem(link.toJson(this,0,1)));
            return LOG.exit(links);
        } catch (SQLException e) {
            throw LOG.throwing(new RuntimeException(e));
        }
    }
    
}
