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
import com.softwareplumbers.dms.Exceptions.InvalidObjectName;
import com.softwareplumbers.dms.Exceptions.InvalidWorkspace;
import com.softwareplumbers.dms.Reference;
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
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
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
    
    public static Document getDocument(ResultSet results) throws SQLException {
        String mediaType = results.getString("MEDIA_TYPE");
        Id id = new Id(results.getBytes("ID"));
        String version = results.getString("VERSION");
        long length = results.getLong("LENGTH");
        byte[] hash = results.getBytes("DIGEST");
        JsonObject metadata = toJson(results.getCharacterStream("METADATA"));
        return new DocumentImpl(new Reference(id.toString(),version), mediaType, length, hash, metadata, false, LocalData.NONE);
    }
    
    public static DocumentLink getLink(ResultSet results) throws SQLException {
        String mediaType = results.getString("MEDIA_TYPE");
        Id id = new Id(results.getBytes("ID"));
        String version = results.getString("VERSION");
        long length = results.getLong("LENGTH");
        byte[] hash = results.getBytes("DIGEST");
        JsonObject metadata = toJson(results.getCharacterStream("METADATA"));
        QualifiedName name = QualifiedName.parse(results.getString("PATH"),"/");
        return new DocumentLinkImpl(name, new Reference(id.toString(),version), mediaType, length, hash, metadata, false, LocalData.NONE);
    }
    
    public static Workspace getWorkspace(ResultSet results, QualifiedName basePath) throws SQLException {
        Id id = new Id(results.getBytes("ID"));
        JsonObject metadata = toJson(results.getCharacterStream("METADATA"));
        Workspace.State state = Workspace.State.valueOf(results.getString("STATE"));
        QualifiedName path = QualifiedName.parse(results.getString("PATH"),"/");
        return new WorkspaceImpl(path, id.toString(), state, metadata, false, LocalData.NONE);
    }
    
    public static Stream<Document> getDocuments(ResultSet results) throws SQLException {
        final ResultSetIterator<Document> rsi = new ResultSetIterator<>(results, SQLAPI::getDocument);
        return Streams.stream(rsi).onClose(()->rsi.close());
    }
    
    public static Stream<DocumentLink> getLinks(ResultSet results) throws SQLException {
        final ResultSetIterator<DocumentLink> rsi = new ResultSetIterator<>(results, SQLAPI::getLink);
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
        if (depth == 0)
            return "";
        else 
            return Templates.substitute(templates.joinParentNode, "N" + depth, "N" + (depth-1), getSearchJoin(depth-1));
    }
    
    String getSearchWhere(int depth) {
        if (depth < 0)
            return "";
        else
            return Templates.substitute(templates.nameWhereClause, "N" + depth, getSearchWhere(depth-1));
    }
    
    String getNameExpression(int depth) {
        StringBuilder builder = new StringBuilder();
        builder.append("N0.NAME");
        for (int i = 1; i <= depth; i++)
            builder.append(" || '/' || N").append(i).append(".NAME");
        return builder.toString();
    }
    
    String getObjectIdSQL(int depth) {
        return String.format("SELECT NODES.ID FROM %s WHERE ID=? AND %s", getSearchJoin(depth), getSearchWhere(depth));
    }
    
    String getDocumentLinkSQL(int depth) {
        return Templates.substitute(templates.fetchDocumentLinkByPath, getNameExpression(depth), "N"+ depth, getSearchJoin(depth), getSearchWhere(depth));
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
    
    public Id createFolder(Id parentId, String name, Workspace.State state, JsonObject metadata) throws SQLException {
        LOG.entry(parentId, name, state, metadata);
        Id id = new Id();
        FluentStatement.of(operations.createNode)
            .set(1, id)
            .set(2, parentId)
            .set(3, name)
            .execute(con);
        FluentStatement.of(operations.createFolder)
            .set(1, id)
            .set(2, state.toString())
            .set(3, out -> { try (JsonWriter writer = Json.createWriter(out)) { writer.write(metadata); } })
            .execute(con);
        return LOG.exit(id);
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
    
    public Optional<Id> getObjectId(Id parentId, String name) throws SQLException {
        LOG.entry(parentId, name);
        return LOG.exit(FluentStatement.of(operations.fetchChildByName)
            .set(1, parentId)
            .set(2, name)
            .execute(con, rs->new Id(rs.getBytes(1)))
            .findFirst()
        );
    }
    
    public Optional<Id> getObjectId(Id rootId, QualifiedName name) throws SQLException {
        LOG.entry(rootId, name);
        return LOG.exit(FluentStatement.of(getObjectIdSQL(name.size()))
            .set(1, rootId)
            .set(2, name)
            .execute(con, rs->new Id(rs.getBytes(1)))
            .findFirst()
        );
    }
    
    public Optional<Id> getOrCreateFolderId(Id parentId, String name, boolean optCreate) throws SQLException {
        Optional<Id> id = getObjectId(parentId, name);
        if (optCreate && !id.isPresent()) {
            id = Optional.of(createFolder(parentId, name, Workspace.State.Open, JsonObject.EMPTY_JSON_OBJECT));
        }
        return id;
    }
    
    public Optional<Id> getOrCreateFolderId(Id rootId, QualifiedName path, boolean optCreate) throws SQLException {
        Optional<Id> result = Optional.of(rootId);
        if (optCreate) {
            for (String name : path) {
                result = getOrCreateFolderId(result.get(), name, true);
                if (!result.isPresent()) break;
            }
        } else {
            result = getObjectId(rootId, path);
        }
        return result;
    }
    
    public void createDocumentLink(Id folderId, String name, Id docId, Id version) throws SQLException {
        LOG.entry(folderId, name, docId, version);
        Id id = new Id();
        FluentStatement.of(operations.createNode)
            .set(1, id)
            .set(2, folderId)
            .set(3, name)
            .execute(con);
        FluentStatement.of(operations.createLink)
            .set(1, id)
            .set(2, docId)
            .set(3, version)
            .set(4, true)
            .execute(con);
        LOG.exit();
    }
    
    public void createDocumentLink(Id rootId, QualifiedName path, Id docId, Id version, boolean optCreate) throws InvalidWorkspace, SQLException {
        LOG.entry(rootId, path, docId, version);
        Optional<Id> id = getOrCreateFolderId(rootId, path.parent, optCreate);
        if (id.isPresent()) 
            createDocumentLink(id.get(), path.part, docId, version);
        else
            throw new InvalidWorkspace(rootId.toString(), path.parent);
    }
    
    public void updateDocumentLink(Id folderId, String name, Id docId, Id version, boolean optCreate) throws InvalidObjectName, SQLException {
        LOG.entry(folderId, name, docId, version);
        Optional<Id> objectId = getObjectId(folderId, name);
        if (objectId.isPresent()) {
            FluentStatement.of(operations.updateLink)
                .set(1, docId)
                .set(2, version)
                .set(3, objectId.get())
                .execute(con);
        } else {
            if (optCreate) {
                createDocumentLink(folderId, name, docId, version);
            } else {
                throw new InvalidObjectName(folderId.toString(), QualifiedName.of(name));                    
            }
        }
        LOG.exit();
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

    public <T> Stream<T> getDocumentLinkById(Id rootId, String id, Mapper<T> mapper) throws SQLException {
        LOG.entry(rootId, id, mapper);
        return LOG.exit(FluentStatement
            .of(operations.fetchLinkById)
            .set(1, rootId)
            .set(2, id)
            .execute(con, mapper)
        );
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
