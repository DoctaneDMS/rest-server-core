/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.sql;

import com.softwareplumbers.dms.Document;
import com.softwareplumbers.dms.Reference;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;
import javax.json.JsonValue;
import org.junit.After;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
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
        schema.createSchema();        
        schema.updateSchema();        
    }
    
    @After
    public void dropSchema() throws SQLException {
        schema.dropSchema();        
    }

    @Test
    public void testCreateDocument() throws SQLException, IOException {
        try (SQLAPI api = factory.getSQLAPI()) {
            api.createDocument("id", "version", "type", 0, "test".getBytes(), JsonValue.EMPTY_JSON_OBJECT);
            api.commit();
            Optional<Document> result = api.getDocument(new Reference("id","version"), SQLAPI::getDocument);
            assertTrue(result.isPresent());
            assertEquals("id", result.get().getId());
            assertEquals("version", result.get().getVersion());
            assertEquals(0, result.get().getLength());
            assertArrayEquals("test".getBytes(), result.get().getDigest());
            assertEquals(JsonValue.EMPTY_JSON_OBJECT, result.get().getMetadata());
        }
    }
}