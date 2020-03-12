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
            String l0 = api.getDocumentLinkSQL(0);
            assertTrue(l0.contains("N0.NAME AS PATH"));
            assertTrue(l0.contains("VIEW_LINKS N0"));
            assertTrue(l0.contains("WHERE N0.PARENT_ID = ? AND N0.NAME = ?"));
            String l1 = api.getDocumentLinkSQL(1);
            System.out.println(l1);
            assertTrue(l1.contains("N0.NAME || '/' || N1.NAME AS PATH"));
            assertTrue(l1.contains("VIEW_LINKS N1 INNER JOIN NODES N0 ON N1.PARENT_ID = N0.ID"));
            assertTrue(l1.contains("WHERE N0.PARENT_ID = ? AND N1.NAME = ? AND N0.NAME = ?"));
            String l2 = api.getDocumentLinkSQL(2);
            assertTrue(l2.contains("N0.NAME || '/' || N1.NAME || '/' || N2.NAME AS PATH"));
            assertTrue(l2.contains("VIEW_LINKS N2 INNER JOIN NODES N1 ON N2.PARENT_ID = N1.ID INNER JOIN NODES N0 ON N1.PARENT_ID = N0.ID"));
            assertTrue(l2.contains("WHERE N0.PARENT_ID = ? AND N2.NAME = ? AND N1.NAME = ? AND N0.NAME = ?"));
        }
    }

    @Test
    public void testCreateAndGetDocument() throws SQLException, IOException {
        try (SQLAPI api = factory.getSQLAPI()) {
            Id id = new Id();
            Id version = new Id();
            api.createDocument(id, version, "type", 0, "test".getBytes(), JsonValue.EMPTY_JSON_OBJECT);
            api.commit();
            Optional<Document> result = api.getDocument(id, version, SQLAPI::getDocument);
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
            Id id = api.createFolder(Id.ROOT_ID, "foldername", Workspace.State.Open, JsonValue.EMPTY_JSON_OBJECT);
            api.commit();
            Optional<Workspace> result = api.getFolder(Id.ROOT_ID, "foldername", rs->SQLAPI.getWorkspace(rs, QualifiedName.ROOT));
            assertTrue(result.isPresent());
            assertEquals(id.toString(), result.get().getId());
            assertEquals(Workspace.State.Open, result.get().getState());
            assertEquals(JsonValue.EMPTY_JSON_OBJECT, result.get().getMetadata());
        }
    }

}