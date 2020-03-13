/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.sql;

import com.google.common.collect.Streams;
import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.dms.Constants;
import com.softwareplumbers.dms.Document;
import com.softwareplumbers.dms.DocumentLink;
import com.softwareplumbers.dms.Exceptions;
import com.softwareplumbers.dms.Exceptions.InvalidDocumentId;
import com.softwareplumbers.dms.Exceptions.InvalidObjectName;
import com.softwareplumbers.dms.Exceptions.InvalidWorkspace;
import com.softwareplumbers.dms.NamedRepositoryObject;
import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.RepositoryObject;
import com.softwareplumbers.dms.Workspace;
import com.softwareplumbers.dms.common.impl.DocumentImpl;
import com.softwareplumbers.dms.common.impl.DocumentLinkImpl;
import com.softwareplumbers.dms.common.impl.LocalData;
import com.softwareplumbers.dms.common.impl.WorkspaceImpl;
import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import javax.sql.DataSource;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookup;
import org.bouncycastle.util.Arrays;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 *
 * @author jonathan
 */
public class SQLAPI implements AutoCloseable {
    
    private static XLogger LOG = XLoggerFactory.getXLogger(SQLAPI.class);
    
    private static final String SAFE_CHARACTERS ="0123456789ABCDEFGHIJKLMNOPQURSTUVWYXabcdefghijklmnopqrstuvwxyz";
    private static final int MAX_SAFE_CHAR='z';
    private static final byte[] ROOT_ID = new byte[] { 0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0 };

    public static JsonObject toJson(Reader reader) {
        try (JsonReader jsonReader = Json.createReader(reader)) {
            return jsonReader.readObject();
        }
    }
    
    public static final Mapper<Id> GET_ID = results -> {
        return new Id(results.getBytes("ID"));
    };
    
    public static final Mapper<String> GET_NAME = results -> {
        return results.getString("NAME");
    };

    public static final Mapper<Document> GET_DOCUMENT = results -> {
        String mediaType = results.getString("MEDIA_TYPE");
        Id id = new Id(results.getBytes("ID"));
        Id version = new Id(results.getBytes("VERSION"));
        long length = results.getLong("LENGTH");
        byte[] hash = results.getBytes("DIGEST");
        JsonObject metadata = toJson(results.getCharacterStream("METADATA"));
        return new DocumentImpl(new Reference(id.toString(),version.toString()), mediaType, length, hash, metadata, false, LocalData.NONE);
    };
    
    public static final Mapper<Reference> GET_REFERENCE = results -> {
        Id id = new Id(results.getBytes("ID"));
        Id version = new Id(results.getBytes("VERSION"));
        return new Reference(id.toString(),version.toString());        
    };
    
    public static DocumentLink getLink(ResultSet results, QualifiedName basePath) throws SQLException {
        String mediaType = results.getString("MEDIA_TYPE");
        Id id = new Id(results.getBytes("DOCUMENT_ID"));
        String version = results.getString("DOCUMENT_VERSION");
        long length = results.getLong("LENGTH");
        byte[] hash = results.getBytes("DIGEST");
        JsonObject metadata = toJson(results.getCharacterStream("METADATA"));
        QualifiedName name = basePath.addParsed(results.getString("PATH"),"/");
        return new DocumentLinkImpl(name, new Reference(id.toString(),version), mediaType, length, hash, metadata, false, LocalData.NONE);
    }
    
    public static Workspace getWorkspace(ResultSet results, QualifiedName basePath) throws SQLException {
        Id id = new Id(results.getBytes("ID"));
        JsonObject metadata = toJson(results.getCharacterStream("METADATA"));
        Workspace.State state = Workspace.State.valueOf(results.getString("STATE"));
        QualifiedName path = basePath.addParsed(results.getString("PATH"),"/");
        return new WorkspaceImpl(path, id.toString(), state, metadata, false, LocalData.NONE);
    }
    
    public static Mapper<Info> GET_INFO = results -> {
        Id id = new Id(results.getBytes("ID"));
        Id parent_id = new Id(results.getBytes("PARENT_ID"));
        String name = results.getString("NAME");
        RepositoryObject.Type type = RepositoryObject.Type.valueOf(results.getString("TYPE"));
        QualifiedName path = QualifiedName.parse(results.getString("PATH"),"/");
        return new Info(id, parent_id, name, path, type);
    };
    
    public static Stream<Document> getDocuments(ResultSet results) throws SQLException {
        final ResultSetIterator<Document> rsi = new ResultSetIterator<>(results, GET_DOCUMENT);
        return Streams.stream(rsi).onClose(()->rsi.close());
    }
    
    public static Stream<DocumentLink> getLinks(ResultSet results, QualifiedName basePath) throws SQLException {
        final ResultSetIterator<DocumentLink> rsi = new ResultSetIterator<>(results, rs->SQLAPI.getLink(rs, basePath));
        return Streams.stream(rsi).onClose(()->rsi.close());        
    }
    
    @Autowired
    public SQLAPI(Operations operations, Templates templates, DataSource datasource) throws SQLException {
        this.operations = operations;
        this.templates = templates;
        this.datasource = datasource;
        this.con = datasource.getConnection();
    }
    
    Operations operations;
    Templates templates;
    DataSource datasource;
    Connection con;
    
    String getSearchJoin(int depth) {
        if (depth <= 1 )
            return "";
        else 
            return Templates.substitute(templates.joinParentNode, "N" + depth, "N" + (depth-1), getSearchJoin(depth-1));
    }
    
    String getSearchWhere(int depth) {
        if (depth < 1)
            return "";
        else
            return Templates.substitute(templates.nameWhereClause, "N" + depth, getSearchWhere(depth-1));
    }
    
    String getNameExpression(int depth) {
        StringBuilder builder = new StringBuilder();
        builder.append("N1.NAME");
        for (int i = 2; i <= depth; i++)
            builder.append(" || '/' || N").append(i).append(".NAME");
        return builder.toString();
    }
    
    String getInfoSQL(int depth) {
        return Templates.substitute(templates.fetchInfoByPath, getNameExpression(depth), "N"+ depth, getSearchJoin(depth), getSearchWhere(depth));
    }
    
    String getDocumentLinkSQL(int depth) {
        return Templates.substitute(templates.fetchDocumentLinkByPath, getNameExpression(depth), "N"+ depth, getSearchJoin(depth), getSearchWhere(depth));
    }

    String getFolderSQL(int depth) {
        return Templates.substitute(templates.fetchFolderByPath, getNameExpression(depth), "N"+ depth, getSearchJoin(depth), getSearchWhere(depth));
    }

    String getDocumentLinkByIdSQL(int depth) {
        return Templates.substitute(templates.fetchDocumentLinkByPathAndId, getNameExpression(depth), getSearchJoin(depth-1), "N"+ depth, getSearchWhere(depth-1));
    }

    public Optional<QualifiedName> getPathTo(Id id) throws SQLException {
        LOG.entry(id);
        if (id.equals(Id.ROOT_ID)) 
            return LOG.exit(Optional.of(QualifiedName.ROOT));
        else
            return LOG.exit(FluentStatement
                .of(operations.fetchPathToId)
                .set(1, id)
                .execute(con, rs->QualifiedName.parse(rs.getString(1),"/"))
                .findFirst()
            );
    }
    
    public String generateUniqueName(Id id, String baseName) throws SQLException {
		int separator = baseName.lastIndexOf('.');
        String ext = "";
		if (separator >= 0) {
			ext = baseName.substring(separator, baseName.length());
			baseName = baseName.substring(0, separator);
		} 
          
        Optional<String> maybePrev = FluentStatement
            .of(operations.fetchLastNameLike)
            .set(1, id)
            .set(2, baseName+"%"+ext)
            .execute(con, rs->rs.getString(1))
            .findFirst();
       
        if (maybePrev.isPresent()) {
            String prev = maybePrev.get();
            int extIndex = prev.lastIndexOf(ext);
            if (extIndex < 0) extIndex = prev.length();
            String postfix = prev.substring(baseName.length(), extIndex);
            int lastChar = postfix.charAt(postfix.length()-1);
            int charIndex = SAFE_CHARACTERS.indexOf(lastChar);
            if (charIndex < 0 || lastChar == MAX_SAFE_CHAR) {
                postfix = postfix + SAFE_CHARACTERS.charAt(0);
            } else {
                postfix = postfix.substring(0, postfix.length() - 1) + SAFE_CHARACTERS.charAt(charIndex + 1);
            }
            return baseName + postfix + ext;

        } else {
            return baseName + ext;
        }
    }
    
    public void createDocument(Id id, Id version, String mediaType, long length, byte[] digest, JsonObject metadata) throws SQLException, IOException {
        LOG.entry(mediaType, length, digest, metadata);
        FluentStatement.of(operations.createDocument)
            .set(1, id)
            .set(2, version)
            .set(3, mediaType)
            .set(4, length)
            .set(5, digest)
            .set(6, out -> { try (JsonWriter writer = Json.createWriter(out)) { writer.write(metadata); } })
            .execute(con);             
        LOG.exit();        
    }

    public <T> Optional<T> getDocument(Id id, Id version, Mapper<T> mapper) throws SQLException {
        LOG.entry(id, version , mapper);
        if (version == null) {
            return FluentStatement.of(operations.fetchLatestDocument).set(1, id).execute(con, mapper).findFirst();
        } else {
            return FluentStatement.of(operations.fetchDocument).set(1, id).set(2, version).execute(con, mapper).findFirst();
        }
    }
    
    public <T> T createFolder(Id parentId, String name, Workspace.State state, JsonObject metadata, Mapper<T> mapper) throws SQLException {
        LOG.entry(parentId, name, state, metadata);
        Id id = new Id();
        FluentStatement.of(operations.createNode)
            .set(1, id)
            .set(2, parentId)
            .set(3, name)
            .set(4, RepositoryObject.Type.WORKSPACE.toString())
            .execute(con);
        FluentStatement.of(operations.createFolder)
            .set(1, id)
            .set(2, state.toString())
            .set(3, out -> { try (JsonWriter writer = Json.createWriter(out)) { writer.write(metadata); } })
            .execute(con);
        return LOG.exit(FluentStatement.of(operations.fetchFolderByName)
            .set(1, parentId)
            .set(2, name)
            .execute(con, mapper)
            .findFirst()
            .orElseThrow(()->new RuntimeException("returned no results"))
        );
    }
        
    public <T> Optional<T> getFolder(Id parentId, String name, Mapper<T> mapper) throws SQLException {
        LOG.entry(parentId, name);
        return LOG.exit(FluentStatement.of(operations.fetchFolderByName)
            .set(1,parentId)
            .set(2,name)
            .execute(con, mapper)
            .findFirst()
        );
    }
    
    public <T> Optional<T> getFolder(Id parentId, QualifiedName name, Mapper<T> mapper) throws SQLException {
        LOG.entry(parentId, name);
        return LOG.exit(FluentStatement.of(getFolderSQL(name.size()))
            .set(1,parentId)
            .set(2,name)
            .execute(con, mapper)
            .findFirst()
        );
    }
    
    public <T> Optional<T> getInfo(Id parentId, String name, Mapper<T> mapper) throws SQLException {
        LOG.entry(parentId, name);
        return LOG.exit(FluentStatement.of(operations.fetchInfoByName)
            .set(1, parentId)
            .set(2, name)
            .execute(con, mapper)
            .findFirst()
        );
    }
    
    public <T> Optional<T> getInfo(Id rootId, QualifiedName name, Mapper<T> mapper) throws SQLException {
        LOG.entry(rootId, name);
        return LOG.exit(FluentStatement.of(getInfoSQL(name.size()))
            .set(1, rootId)
            .set(2, name)
            .execute(con, mapper)
            .findFirst()
        );
    }
    
    public Stream<Info> getChildren(Id parentId) throws SQLException {
        LOG.entry(parentId);
        return LOG.exit(FluentStatement.of(operations.fetchChildren)
            .set(1, parentId)
            .execute(con, GET_INFO)
        );        
    }
    
    public <T> Optional<T> getOrCreateFolder(Id parentId, String name, boolean optCreate, Mapper<T> mapper) throws SQLException, InvalidWorkspace {
        Optional<T> folder = getFolder(parentId, name, mapper);
        if (!folder.isPresent() && optCreate)
            folder = Optional.of(createFolder(parentId, name, Workspace.State.Open, JsonObject.EMPTY_JSON_OBJECT, mapper));
        return folder;
    }
    

    
    public <T> Optional<T> getOrCreateFolder(Id rootId, QualifiedName path, boolean optCreate, Mapper<T> mapper) throws InvalidWorkspace, SQLException {
        Optional<Id> parentId = path.parent.isEmpty() 
            ? getFolder(rootId, QualifiedName.ROOT, GET_ID) 
            : getOrCreateFolder(rootId, path.parent, optCreate, GET_ID);
        return parentId.isPresent() 
            ? getOrCreateFolder(parentId.get(), path.part, optCreate, mapper)
            : Optional.empty();
    }
    
    public <T> T copyFolder(Id sourceId, QualifiedName sourcePath, Id targetId, QualifiedName targetPath, boolean optCreate, Mapper<T> mapper) throws SQLException, InvalidObjectName, InvalidWorkspace {
        Id idSrc = getFolder(sourceId, sourcePath, GET_ID).orElseThrow(()->new InvalidObjectName(sourceId.toString(), sourcePath));
        Id folderId = targetPath.parent.isEmpty() 
            ? targetId
            : getOrCreateFolder(targetId, targetPath.parent, optCreate, GET_ID)
                .orElseThrow(()->new InvalidWorkspace(targetId.toString(), targetPath.parent));
            
        Id id = new Id();
        FluentStatement.of(operations.createNode)
            .set(1, id)
            .set(2, folderId)
            .set(3, targetPath.part)
            .set(4, RepositoryObject.Type.DOCUMENT_LINK.toString())
            .execute(con);
        FluentStatement.of(operations.copyFolder)
            .set(1, id)
            .set(2, idSrc)
            .execute(con);
        
        Iterable<Info> children = FluentStatement.of(operations.fetchChildren)
            .set(1, idSrc)
            .execute(con, GET_INFO)
            .collect(Collectors.toList());
        for (Info child : children) {
            switch(child.type) {
                case WORKSPACE:
                    copyFolder(idSrc, child.path, id, child.path, false, GET_ID);
                    break;
                case DOCUMENT_LINK:
                    copyDocumentLink(idSrc, child.path, id, child.path, false, GET_ID);
                    break;
                default:
                    throw new RuntimeException("don't know how to copy " + child.type);
            }
        }
        
        return LOG.exit(FluentStatement.of(operations.fetchFolderByName)
            .set(1, folderId)
            .set(2, targetPath.part)
            .execute(con, mapper)
            .findFirst()
            .orElseThrow(()->new RuntimeException("returned no results")));
    }
    
    public <T> T copyDocumentLink(Id sourceId, QualifiedName sourcePath, Id targetId, QualifiedName targetPath, boolean optCreate, Mapper<T> mapper) throws SQLException, InvalidObjectName, InvalidWorkspace {
        Id idSrc = getDocumentLink(sourceId, sourcePath, GET_ID).orElseThrow(()->new InvalidObjectName(sourceId.toString(), sourcePath));
        Id folderId = targetPath.parent.isEmpty() 
            ? targetId
            : getOrCreateFolder(targetId, targetPath.parent, optCreate, GET_ID)
                .orElseThrow(()->new InvalidWorkspace(targetId.toString(), targetPath.parent));
        Id id = new Id();
        FluentStatement.of(operations.createNode)
            .set(1, id)
            .set(2, folderId)
            .set(3, targetPath.part)
            .set(4, RepositoryObject.Type.DOCUMENT_LINK.toString())
            .execute(con);
        FluentStatement.of(operations.copyLink)
            .set(1, id)
            .set(2, idSrc)
            .execute(con);
        return LOG.exit(FluentStatement.of(operations.fetchLinkByName)
            .set(1, folderId)
            .set(2, targetPath.part)
            .execute(con, mapper)
            .findFirst()
            .orElseThrow(()->new RuntimeException("returned no results")));
    }
    
    
    public <T> T createDocumentLink(Id folderId, String name, Id docId, Id version, Mapper<T> mapper) throws SQLException {
        LOG.entry(folderId, name, docId, version);
        Id id = new Id();
        FluentStatement.of(operations.createNode)
            .set(1, id)
            .set(2, folderId)
            .set(3, name)
            .set(4, RepositoryObject.Type.DOCUMENT_LINK.toString())
            .execute(con);
        FluentStatement.of(operations.createLink)
            .set(1, id)
            .set(2, docId)
            .set(3, version)
            .set(4, true)
            .execute(con);
        return LOG.exit(FluentStatement.of(operations.fetchLinkByName)
            .set(1, folderId)
            .set(2, name)
            .execute(con, mapper)
            .findFirst()
            .orElseThrow(()->new RuntimeException("returned no results")));
    }
    
    public <T> T createDocumentLink(Id rootId, QualifiedName path, Id docId, Id version, boolean optCreate, Mapper<T> mapper) throws InvalidWorkspace, SQLException {
        LOG.entry(rootId, path, docId, version);
        Id id = getOrCreateFolder(rootId, path.parent, optCreate, GET_ID).orElseThrow(()->new InvalidWorkspace(rootId.toString(), path.parent));
        return LOG.exit(createDocumentLink(id, path.part, docId, version, mapper));
    }
    
    public <T> T updateDocumentLink(Id folderId, String name, Id docId, Id version, boolean optCreate, Mapper<T> mapper) throws InvalidObjectName, SQLException {
        LOG.entry(folderId, name, docId, version);
        Optional<Info> info = getInfo(folderId, name, GET_INFO);
        if (info.isPresent()) {
            FluentStatement.of(operations.updateLink)
                .set(1, docId)
                .set(2, version)
                .set(3, info.get().id)
                .execute(con);
            return LOG.exit(FluentStatement.of(operations.fetchLinkByName)
                .set(1, folderId)
                .set(2, name)
                .execute(con, mapper)
                .findFirst()
                .orElseThrow(()->new RuntimeException("returned no results"))
            );
        } else {
            if (optCreate) {
                return LOG.exit(createDocumentLink(folderId, name, docId, version, mapper));
            } else {
                throw new InvalidObjectName(folderId.toString(), QualifiedName.of(name));                    
            }
        }
    }
    
    public <T> T updateFolder(Id folderId, String name, Workspace.State state, JsonObject metadata, boolean optCreate, Mapper<T> mapper) throws InvalidWorkspace, SQLException {
        LOG.entry(folderId, name, state, metadata, optCreate);
        Optional<Info> info = getInfo(folderId, name, GET_INFO);
        if (info.isPresent()) {
            FluentStatement.of(operations.updateFolder)
                .set(1, state.toString())
                .set(2, metadata)
                .set(3, info.get().id)
                .execute(con);
            return LOG.exit(FluentStatement.of(operations.fetchFolderByName)
                .set(1, folderId)
                .set(2, name)
                .execute(con, mapper)
                .findFirst()
                .orElseThrow(()->new RuntimeException("returned no results"))
            );
        } else {
            if (optCreate) {
                return LOG.exit(createFolder(folderId, name, state, metadata, mapper));
            } else {
                throw new InvalidWorkspace(folderId.toString(), QualifiedName.of(name));                    
            }
        }
    }
    
    public <T> Optional<T> getDocumentLink(Id rootId, QualifiedName workspacePath, Id documentId, Mapper<T> mapper) throws SQLException {
        LOG.entry(rootId, workspacePath, documentId, mapper);
        return LOG.exit(FluentStatement
            .of(getDocumentLinkByIdSQL(workspacePath.size()))
            .set(1, documentId)
            .set(2, rootId)
            .set(3, workspacePath)
            .execute(con, mapper)
            .findFirst()
        );
    }
        
    public <T> Optional<T> getDocumentLink(Id rootId, String name, Mapper<T> mapper) throws SQLException {
        LOG.entry(rootId, name, mapper);
        return LOG.exit(FluentStatement
            .of(operations.fetchLinkByName)
            .set(1, rootId)
            .set(2,name)
            .execute(con, mapper)
            .findFirst()
        );
    }
    
    public <T> Optional<T> getDocumentLink(Id rootId, QualifiedName path, Mapper<T> mapper) throws SQLException {
        LOG.entry(rootId, path, mapper);
        return LOG.exit(FluentStatement
            .of(getDocumentLinkSQL(path.size()))
            .set(1, rootId)
            .set(2, path)
            .execute(con, mapper)
            .findFirst()
        );
    }

    public <T> Stream<T> getDocumentLinkById(Id rootId, String id, Mapper<T> mapper) throws SQLException {
        LOG.entry(rootId, id, mapper);
        return LOG.exit(FluentStatement
            .of(operations.fetchLinkById)
            .set(1, rootId)
            .set(2, id)
            .execute(con, mapper)
        );
    }
    
    public void deleteObject(Id rootId, QualifiedName path) throws SQLException, InvalidObjectName {
        LOG.entry(rootId, path);
        Id objectId = getInfo(rootId, path, GET_ID).orElseThrow(()->new InvalidObjectName(rootId.toString(), path));
        FluentStatement.of(operations.deleteObject).set(1, objectId).execute(con);
    }
    
    public void deleteDocumentById(Id rootId, QualifiedName workspacePath, Id documentId) throws SQLException, InvalidWorkspace, InvalidDocumentId {
        LOG.entry(rootId, workspacePath, documentId);
        Id parentId = getInfo(rootId, workspacePath, GET_ID).orElseThrow(()->new InvalidWorkspace(rootId.toString(), workspacePath));
        int count = FluentStatement.of(operations.deleteDocumentById).set(1, parentId).set(2, documentId).execute(con);
        if (count == 0) throw new Exceptions.InvalidDocumentId(documentId.toString());
    }
    
    public SQLAPI(DataSource ds) throws SQLException {
        con = ds.getConnection();
        con.setAutoCommit(false);
    }
    
    @Override
    public void close() throws SQLException {
        con.rollback();
        con.close();
    }
    
    public <T extends Throwable> void rollbackOrThrow(Supplier<T> supplier) throws T {
        try {
            con.rollback();
        } catch (SQLException e) {
            LOG.catching(e);
            throw(supplier.get());
        }
    }
    
    public <T extends Throwable> void commitOrThrow(Supplier<T> supplier) throws T {
        try {
            con.commit();
        } catch (SQLException e) {
            LOG.catching(e);
            throw(supplier.get());
        }
    }
    
    public void commit() throws SQLException {
        con.commit();
    }
}
