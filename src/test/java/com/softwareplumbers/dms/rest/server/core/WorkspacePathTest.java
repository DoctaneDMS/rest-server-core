/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.core;

import com.softwareplumbers.common.QualifiedName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author jonathan.local
 */
public class WorkspacePathTest {
    
    @Test
    public void testSimplePath() {
        WorkspacePath path = WorkspacePath.valueOf("/xyz/abc/234");
        assertEquals(QualifiedName.ROOT, path.queryPath);
        assertNull(path.documentId);
        assertNull(path.rootId);
        assertFalse(path.partPath.isPresent());
        boolean result = QualifiedName.of("xyz","abc","234").equals(path.staticPath);
        assertEquals(QualifiedName.of("xyz","abc","234"), path.staticPath);        
    }

    @Test
    public void testSimplePathWithRootId() {
        WorkspacePath path = WorkspacePath.valueOf("~xyx/abc/234");
        assertFalse(path.partPath.isPresent());
        assertEquals(QualifiedName.ROOT, path.queryPath);
        assertNull(path.documentId);
        assertEquals("xyx", path.rootId);
        assertEquals(QualifiedName.of("abc","234"), path.staticPath);        
    }

    @Test
    public void testQueryPath1() {
        WorkspacePath path = WorkspacePath.valueOf("xyx/ab*c/234");
        assertFalse(path.partPath.isPresent());
        assertEquals(QualifiedName.of("ab*c","234"), path.queryPath);
        assertNull(path.documentId);
        assertNull(path.rootId);
        assertEquals(QualifiedName.of("xyx"), path.staticPath);        
    }

    @Test
    public void testQueryPath2() {
        WorkspacePath path = WorkspacePath.valueOf("xyx/ab?c/*");
        assertFalse(path.partPath.isPresent());
        assertEquals(QualifiedName.of("ab?c","*"), path.queryPath);
        assertNull(path.documentId);
        assertNull(path.rootId);
        assertEquals(QualifiedName.of("xyx"), path.staticPath);        
    }

    @Test
    public void testQueryPathRootId() {
        WorkspacePath path = WorkspacePath.valueOf("~xyx/ab?c/*");
        assertFalse(path.partPath.isPresent());
        assertEquals(QualifiedName.of("ab?c","*"), path.queryPath);
        assertNull(path.documentId);
        assertEquals("xyx", path.rootId);
        assertEquals(QualifiedName.ROOT, path.staticPath);        
    }
    
    @Test
    public void testSimplePathWithParts() {
        WorkspacePath  path = WorkspacePath.valueOf("/abc/xyz/~/234/ghi");
        assertEquals(QualifiedName.of("234","ghi"), path.partPath.get());
        assertFalse(path.queryPart);
        assertEquals(QualifiedName.ROOT, path.queryPath);
        assertNull(path.documentId);
        assertNull(path.rootId);
        assertEquals(QualifiedName.of("abc","xyz"), path.staticPath);        
    }

    @Test
    public void testSimplePathWithRootPart() {
        WorkspacePath  path = WorkspacePath.valueOf("/abc/xyz/~");
        assertEquals(QualifiedName.ROOT, path.partPath.get());
        assertFalse(path.queryPart);
        assertEquals(QualifiedName.ROOT, path.queryPath);
        assertNull(path.documentId);
        assertNull(path.rootId);
        assertEquals(QualifiedName.of("abc","xyz"), path.staticPath);        
    }
    
    @Test
    public void testSimplePathWithDocumentId() {
        WorkspacePath  path = WorkspacePath.valueOf("/abc/~sdfg");
        assertFalse(path.partPath.isPresent());
        assertEquals(QualifiedName.ROOT, path.queryPath);
        assertEquals("sdfg", path.documentId);
        assertNull(path.rootId);
        assertEquals(QualifiedName.of("abc"), path.staticPath);        
    }
    
    @Test
    public void testQueryPathWithDocumentId() {
        WorkspacePath  path = WorkspacePath.valueOf("/abc/*/~sdfg");
        assertFalse(path.partPath.isPresent());
        assertEquals(QualifiedName.of("*"), path.queryPath);
        assertEquals("sdfg", path.documentId);
        assertNull(path.rootId);
        assertEquals(QualifiedName.of("abc"), path.staticPath);        
    }

    @Test
    public void testDocumentIdWithParts() {
        WorkspacePath  path = WorkspacePath.valueOf("/abc/~sdfg/~/234/ghi");
        assertEquals(QualifiedName.of("234","ghi"), path.partPath.get());
        assertFalse(path.queryPart);        
        assertEquals(QualifiedName.ROOT, path.queryPath);
        assertEquals("sdfg", path.documentId);
        assertNull(path.rootId);
        assertEquals(QualifiedName.of("abc"), path.staticPath);        
    }

    @Test
    public void testWorkpaceIdDocumentIdWithParts() {
        WorkspacePath  path = WorkspacePath.valueOf("~123/abc/def/s*/x*/~sdfg/234/ghi");
        assertEquals(QualifiedName.of("234","ghi"), path.partPath.get());
        assertFalse(path.queryPart);
        assertEquals(QualifiedName.of("s*","x*"), path.queryPath);
        assertEquals("sdfg", path.documentId);
        assertEquals("123", path.rootId);
        assertEquals(QualifiedName.of("abc","def"), path.staticPath);        
    }
    
    @Test
    public void testWorkpaceIdDocumentIdWithQueryParts() {
        WorkspacePath  path = WorkspacePath.valueOf("~123/abc/def/s*/x*/~sdfg/234/ghi?");
        assertEquals(QualifiedName.of("234","ghi?"), path.partPath);
        assertTrue(path.queryPart);
        assertEquals(QualifiedName.of("s*","x*"), path.queryPath);
        assertEquals("sdfg", path.documentId);
        assertEquals("123", path.rootId);
        assertEquals(QualifiedName.of("abc","def"), path.staticPath);        
    }
}
