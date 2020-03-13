/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.sql;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.dms.Constants;
import com.softwareplumbers.dms.Document;
import com.softwareplumbers.dms.DocumentLink;
import com.softwareplumbers.dms.Exceptions.InvalidObjectName;
import com.softwareplumbers.dms.Exceptions.InvalidWorkspace;
import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.Workspace;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;
import javax.json.JsonValue;
import org.junit.After;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 *
 * @author jonathan
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { LocalConfig.class })
public class TestSQLAPI {
    
    @Autowired
    SQLAPIFactory factory;
    
    @Autowired
    Schema schema;
    
    @Before
    public void createSchema() throws SQLException {
        schema.dropSchema();        
        schema.createSchema();        
        schema.updateSchema();        
    }
        
    @Test 
    public void testGetDocumentLinkSQL() throws SQLException {
        try (SQLAPI api = factory.getSQLAPI()) {
            String l0 = api.getDocumentLinkSQL(1);
            assertTrue(l0.contains("N1.NAME AS PATH"));
            assertTrue(l0.contains("VIEW_LINKS N1"));
            assertTrue(l0.contains("WHERE N1.PARENT_ID = ? AND N1.NAME = ?"));
            String l1 = api.getDocumentLinkSQL(2);
            assertTrue(l1.contains("N1.NAME || '/' || N2.NAME AS PATH"));
            assertTrue(l1.contains("VIEW_LINKS N2 INNER JOIN NODES N1 ON N2.PARENT_ID = N1.ID"));
            assertTrue(l1.contains("WHERE N1.PARENT_ID = ? AND N2.NAME = ? AND N1.NAME = ?"));
            String l2 = api.getDocumentLinkSQL(3);
            assertTrue(l2.contains("N1.NAME || '/' || N2.NAME || '/' || N3.NAME AS PATH"));
            assertTrue(l2.contains("VIEW_LINKS N3 INNER JOIN NODES N2 ON N3.PARENT_ID = N2.ID INNER JOIN NODES N1 ON N2.PARENT_ID = N1.ID"));
            assertTrue(l2.contains("WHERE N1.PARENT_ID = ? AND N3.NAME = ? AND N2.NAME = ? AND N1.NAME = ?"));
        }
    }
    
    @Test 
    public void testGetFolderSQL() throws SQLException {
        try (SQLAPI api = factory.getSQLAPI()) {
            String l0 = api.getFolderSQL(1);
            assertTrue(l0.contains("N1.NAME AS PATH"));
            assertTrue(l0.contains("VIEW_FOLDERS N1"));
            assertTrue(l0.contains("WHERE N1.PARENT_ID = ? AND N1.NAME = ?"));
            String l1 = api.getFolderSQL(2);
            assertTrue(l1.contains("N1.NAME || '/' || N2.NAME AS PATH"));
            assertTrue(l1.contains("VIEW_FOLDERS N2 INNER JOIN NODES N1 ON N2.PARENT_ID = N1.ID"));
            assertTrue(l1.contains("WHERE N1.PARENT_ID = ? AND N2.NAME = ? AND N1.NAME = ?"));
            String l2 = api.getFolderSQL(3);
            assertTrue(l2.contains("N1.NAME || '/' || N2.NAME || '/' || N3.NAME AS PATH"));
            assertTrue(l2.contains("VIEW_FOLDERS N3 INNER JOIN NODES N2 ON N3.PARENT_ID = N2.ID INNER JOIN NODES N1 ON N2.PARENT_ID = N1.ID"));
            assertTrue(l2.contains("WHERE N1.PARENT_ID = ? AND N3.NAME = ? AND N2.NAME = ? AND N1.NAME = ?"));
        }
    }
    
    @Test
    public void testGetInfoSQL() throws SQLException {
        try (SQLAPI api = factory.getSQLAPI()) {
            String l0 = api.getInfoSQL(1);
            assertTrue(l0.contains("N1.NAME AS PATH"));
            assertTrue(l0.contains("NODES N1"));
            assertTrue(l0.contains("WHERE N1.PARENT_ID = ? AND N1.NAME = ?"));
        }
    }

    @Test
    public void testCreateAndGetDocument() throws SQLException, IOException {
        try (SQLAPI api = factory.getSQLAPI()) {
            Id id = new Id();
            Id version = new Id();
            api.createDocument(id, version, "type", 0, "test".getBytes(), JsonValue.EMPTY_JSON_OBJECT);
            api.commit();
            Optional<Document> result = api.getDocument(id, version, SQLAPI.GET_DOCUMENT);
            assertTrue(result.isPresent());
            assertEquals(id.toString(), result.get().getId());
            assertEquals(version.toString(), result.get().getVersion());
            assertEquals(0, result.get().getLength());
            assertArrayEquals("test".getBytes(), result.get().getDigest());
            assertEquals(JsonValue.EMPTY_JSON_OBJECT, result.get().getMetadata());
        }
    }
    
    @Test
    public void testCreateAndGetFolder() throws SQLException, IOException {
        try (SQLAPI api = factory.getSQLAPI()) {
            Id id = api.createFolder(Id.ROOT_ID, "foldername", Workspace.State.Open, JsonValue.EMPTY_JSON_OBJECT, SQLAPI.GET_ID);
            api.commit();
            Optional<Workspace> result = api.getFolder(Id.ROOT_ID, "foldername", rs->SQLAPI.getWorkspace(rs, QualifiedName.ROOT));
            assertTrue(result.isPresent());
            assertEquals(id.toString(), result.get().getId());
            assertEquals(Workspace.State.Open, result.get().getState());
            assertEquals(JsonValue.EMPTY_JSON_OBJECT, result.get().getMetadata());
        }
    }
    
    @Test
    public void testCreateAndGetFolderWithPath() throws SQLException, IOException {
        try (SQLAPI api = factory.getSQLAPI()) {
            Id parent_id = api.createFolder(Id.ROOT_ID, "parent", Workspace.State.Open, JsonValue.EMPTY_JSON_OBJECT, SQLAPI.GET_ID);
            Id child_id = api.createFolder(parent_id, "child", Workspace.State.Open, JsonValue.EMPTY_JSON_OBJECT, SQLAPI.GET_ID);
            Id grandchild_id = api.createFolder(child_id, "grandchild", Workspace.State.Open, JsonValue.EMPTY_JSON_OBJECT, SQLAPI.GET_ID);
            api.commit();
            Optional<Workspace> result = api.getFolder(Id.ROOT_ID, QualifiedName.of("parent","child","grandchild"), rs->SQLAPI.getWorkspace(rs, QualifiedName.ROOT));
            assertTrue(result.isPresent());
            assertEquals(grandchild_id.toString(), result.get().getId());
            assertEquals(QualifiedName.of("parent","child","grandchild"), result.get().getName());
        }
    }
    
    @Test
    public void testCopyFolderWithPath() throws SQLException, IOException, InvalidObjectName, InvalidWorkspace {
        try (SQLAPI api = factory.getSQLAPI()) {
            Id parent_id = api.createFolder(Id.ROOT_ID, "parent", Workspace.State.Open, JsonValue.EMPTY_JSON_OBJECT, SQLAPI.GET_ID);
            Id child_id = api.createFolder(parent_id, "child", Workspace.State.Open, JsonValue.EMPTY_JSON_OBJECT, SQLAPI.GET_ID);
            api.createFolder(child_id, "grandchild", Workspace.State.Open, JsonValue.EMPTY_JSON_OBJECT, SQLAPI.GET_ID);
            Workspace sibling = api.copyFolder(parent_id, QualifiedName.of("child"), parent_id, QualifiedName.of("sibling"), false, rs->SQLAPI.getWorkspace(rs, QualifiedName.of("parent")));
            assertEquals(QualifiedName.of("parent","sibling"), sibling.getName());
            Optional<Workspace> cousin = api.getFolder(Id.ROOT_ID, QualifiedName.of("parent","sibling","grandchild"), rs->SQLAPI.getWorkspace(rs, QualifiedName.ROOT));
            assertTrue(cousin.isPresent());
        }
    }
    
        @Test
    public void testCopyDocumentLinkWithPath() throws SQLException, IOException, InvalidObjectName, InvalidWorkspace {
        try (SQLAPI api = factory.getSQLAPI()) {
            Id parent_id = api.createFolder(Id.ROOT_ID, "parent", Workspace.State.Open, JsonValue.EMPTY_JSON_OBJECT, SQLAPI.GET_ID);
            Id child_id = api.createFolder(parent_id, "child", Workspace.State.Open, JsonValue.EMPTY_JSON_OBJECT, SQLAPI.GET_ID);
            Id id = new Id();
            Id version = new Id();
            api.createDocument(id, version, "type", 0, "test".getBytes(), JsonValue.EMPTY_JSON_OBJECT);
            api.createDocumentLink(child_id, "grandchild", id, version, SQLAPI.GET_ID);
            DocumentLink sibling = api.copyDocumentLink(child_id, QualifiedName.of("grandchild"), parent_id, QualifiedName.of("sibling"), false, rs->SQLAPI.getLink(rs, QualifiedName.of("parent")));
            assertEquals(QualifiedName.of("parent","sibling"), sibling.getName());
            assertEquals(id.toString(), sibling.getId());
            assertEquals(version.toString(), sibling.getVersion());
        }
    }
    
    @Test
    public void testCreateAndGetDocumentLink() throws SQLException, IOException {
        try (SQLAPI api = factory.getSQLAPI()) {
            Id folder_id = api.createFolder(Id.ROOT_ID, "foldername", Workspace.State.Open, JsonValue.EMPTY_JSON_OBJECT, SQLAPI.GET_ID);
            Id id = new Id();
            Id version = new Id();
            api.createDocument(id, version, "type", 0, "test".getBytes(), JsonValue.EMPTY_JSON_OBJECT);
            api.createDocumentLink(folder_id, "docname", id, version, SQLAPI.GET_ID);
            api.commit();
            Optional<DocumentLink> result = api.getDocumentLink(folder_id, "docname", rs->SQLAPI.getLink(rs, QualifiedName.of("foldername")));
            assertTrue(result.isPresent());
            assertEquals(QualifiedName.of("foldername","docname"), result.get().getName());
            assertEquals(id.toString(), result.get().getId());
            assertEquals(version.toString(), result.get().getVersion());
            assertEquals("type", result.get().getMediaType());
            assertEquals(0, result.get().getLength());
            assertArrayEquals("test".getBytes(), result.get().getDigest());
            assertEquals(JsonValue.EMPTY_JSON_OBJECT, result.get().getMetadata());
        }
    }

    @Test
    public void testCreateAndGetDocumentLinkWithPath() throws SQLException, IOException {
        try (SQLAPI api = factory.getSQLAPI()) {
            Id folder_id = api.createFolder(Id.ROOT_ID, "foldername", Workspace.State.Open, JsonValue.EMPTY_JSON_OBJECT, SQLAPI.GET_ID);
            Id id = new Id();
            Id version = new Id();
            api.createDocument(id, version, "type", 0, "test".getBytes(), JsonValue.EMPTY_JSON_OBJECT);
            api.createDocumentLink(folder_id, "docname", id, version, SQLAPI.GET_ID);
            api.commit();
            Optional<DocumentLink> result = api.getDocumentLink(Id.ROOT_ID, QualifiedName.of("foldername","docname"), rs->SQLAPI.getLink(rs, QualifiedName.ROOT));
            assertTrue(result.isPresent());
            assertEquals(QualifiedName.of("foldername","docname"), result.get().getName());
            assertEquals(id.toString(), result.get().getId());
            assertEquals(version.toString(), result.get().getVersion());
            assertEquals("type", result.get().getMediaType());
            assertEquals(0, result.get().getLength());
            assertArrayEquals("test".getBytes(), result.get().getDigest());
            assertEquals(JsonValue.EMPTY_JSON_OBJECT, result.get().getMetadata());
        }
    }    
}