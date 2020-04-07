/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.core;

import com.softwareplumbers.common.immutablelist.QualifiedName;
import com.softwareplumbers.dms.Constants;
import com.softwareplumbers.dms.rest.server.core.RepositoryPath.ElementType;
import com.softwareplumbers.dms.rest.server.core.RepositoryPath.IdElement;
import com.softwareplumbers.dms.rest.server.core.RepositoryPath.NamedElement;
import com.softwareplumbers.dms.rest.server.core.RepositoryPath.PartRoot;
import com.softwareplumbers.dms.rest.server.core.RepositoryPath.VersionedElement;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author jonathan.local
 */
public class RepositoryPathTest {
    
    @Test
    public void testSimplePath() {
        RepositoryPath path = RepositoryPath.valueOf("/xyz/abc/234");
        assertEquals(Constants.ROOT_ID, path.getRootId());
        assertEquals(RepositoryPath.ROOT, path.getQueryPath());
        assertEquals(RepositoryPath.ROOT, path.getPartPath());
        assertEquals(path, path.getDocumentPath());
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "xyz", null), path.get(0));
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "abc", null), path.get(1));
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "234", null), path.get(2));
        assertEquals(3, path.size());
    }

    @Test
    public void testSimplePathWithRootId() {
        RepositoryPath path = RepositoryPath.valueOf("~xyx/abc/234");
        assertEquals("xyx", path.getRootId());
        assertEquals(RepositoryPath.ROOT, path.getQueryPath());
        assertEquals(RepositoryPath.ROOT, path.getPartPath());
        assertEquals(path, path.getDocumentPath());
        assertEquals(new IdElement("xyx", null), path.get(0));
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "abc", null), path.get(1));
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "234", null), path.get(2));
        assertEquals(3, path.size());
    }

    @Test
    public void testQueryPath1() {
        RepositoryPath path = RepositoryPath.valueOf("xyx/ab*c/234");
        assertEquals(Constants.ROOT_ID, path.getRootId());
        assertEquals(RepositoryPath.valueOf("ab*c/234"), path.getQueryPath());
        assertEquals(RepositoryPath.valueOf("xyx"), path.getSimplePath());
        assertEquals(RepositoryPath.ROOT, path.getPartPath());
        assertEquals(path, path.getDocumentPath());
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "xyx", null), path.get(0));
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "ab*c", null), path.get(1));
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "234", null), path.get(2));
        assertEquals(3, path.size());
    }

    @Test
    public void testQueryPath2() {
        RepositoryPath path = RepositoryPath.valueOf("xyx/ab?c/*");
        assertEquals(Constants.ROOT_ID, path.getRootId());
        assertEquals(RepositoryPath.valueOf("ab?c/*"), path.getQueryPath());
        assertEquals(RepositoryPath.valueOf("xyx"), path.getSimplePath());
        assertEquals(RepositoryPath.ROOT, path.getPartPath());
        assertEquals(path, path.getDocumentPath());
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "xyx", null), path.get(0));
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "ab?c", null), path.get(1));
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "*", null), path.get(2));
        assertEquals(3, path.size());
    }

    @Test
    public void testQueryPathRootId() {
        RepositoryPath path = RepositoryPath.valueOf("~xyx/ab?c/*");
        assertEquals("xyx", path.getRootId());
        assertEquals(RepositoryPath.valueOf("ab?c/*"), path.getQueryPath());
        assertEquals(RepositoryPath.valueOf("~xyx"), path.getSimplePath());
        assertEquals(RepositoryPath.ROOT, path.getPartPath());
        assertEquals(path, path.getDocumentPath());
        assertEquals(new IdElement("xyx", null), path.get(0));
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "ab?c", null), path.get(1));
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "*", null), path.get(2));
        assertEquals(3, path.size());
    }
    
    @Test
    public void testSimplePathWithParts() {
        RepositoryPath  path = RepositoryPath.valueOf("/abc/xyz/~/234/ghi");
        assertEquals(Constants.ROOT_ID, path.getRootId());
        assertEquals(RepositoryPath.ROOT, path.getQueryPath());
        assertEquals(RepositoryPath.valueOf("abc/xyz"), path.getDocumentPath());
        assertEquals(RepositoryPath.valueOf("~/234/ghi"), path.getPartPath());
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "abc", null), path.get(0));
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "xyz", null), path.get(1));
        assertEquals(new PartRoot(), path.get(2));
        assertEquals(new NamedElement(ElementType.PART_PATH, "234"), path.get(3));
        assertEquals(new NamedElement(ElementType.PART_PATH, "ghi"), path.get(4));
        assertEquals(5, path.size());     
    }

    @Test
    public void testSimplePathWithRootPart() {
        RepositoryPath  path = RepositoryPath.valueOf("/abc/xyz/~");
        assertEquals(Constants.ROOT_ID, path.getRootId());
        assertEquals(RepositoryPath.ROOT, path.getQueryPath());
        assertEquals(RepositoryPath.valueOf("abc/xyz"), path.getDocumentPath());
        assertEquals(RepositoryPath.valueOf("~"), path.getPartPath());
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "abc", null), path.get(0));
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "xyz", null), path.get(1));
        assertEquals(new PartRoot(), path.get(2));
        assertEquals(3, path.size());     
    }
    
    @Test
    public void testSimplePathWithDocumentId() {
        RepositoryPath  path = RepositoryPath.valueOf("/abc/~sdfg");
        assertEquals(Constants.ROOT_ID, path.getRootId());
        assertEquals(RepositoryPath.ROOT, path.getQueryPath());
        assertEquals(RepositoryPath.valueOf("abc/~sdfg"), path.getDocumentPath());
        assertEquals(RepositoryPath.ROOT, path.getPartPath());
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "abc", null), path.get(0));
        assertEquals(new IdElement("sdfg", null), path.get(1));
        assertEquals(2, path.size());     
    }
    
    @Test
    public void testQueryPathWithDocumentId() {
        RepositoryPath  path = RepositoryPath.valueOf("/abc/*/~sdfg");
        assertEquals(Constants.ROOT_ID, path.getRootId());
        assertEquals(RepositoryPath.valueOf("*/~sdfg"), path.getQueryPath());
        assertEquals(RepositoryPath.valueOf("abc"), path.getSimplePath());
        assertEquals(RepositoryPath.ROOT, path.getPartPath());
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "abc", null), path.get(0));
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "*", null), path.get(1));
        assertEquals(new IdElement("sdfg", null), path.get(2));
        assertEquals(3, path.size());     
    }

    @Test
    public void testDocumentIdWithParts() {
        RepositoryPath  path = RepositoryPath.valueOf("/abc/~sdfg/~/234/ghi");
        assertEquals(Constants.ROOT_ID, path.getRootId());
        assertEquals(RepositoryPath.ROOT, path.getQueryPath());
        assertEquals(RepositoryPath.valueOf("abc/~sdfg"), path.getDocumentPath());
        assertEquals(RepositoryPath.valueOf("~/234/ghi"), path.getPartPath());
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "abc", null), path.get(0));
        assertEquals(new IdElement("sdfg", null), path.get(1));
        assertEquals(new PartRoot(), path.get(2));
        assertEquals(new NamedElement(ElementType.PART_PATH, "234"), path.get(3));
        assertEquals(new NamedElement(ElementType.PART_PATH, "ghi"), path.get(4));
        assertEquals(5, path.size());     
    }

    @Test
    public void testWorkpaceIdDocumentIdWithParts() {
        RepositoryPath  path = RepositoryPath.valueOf("~123/abc/def/s*/x*/~sdfg/~/234/ghi");
        assertEquals("123", path.getRootId());
        assertEquals(RepositoryPath.valueOf("s*/x*/~sdfg/~/234/ghi"), path.getQueryPath());
        assertEquals(RepositoryPath.valueOf("~123/abc/def"), path.getSimplePath());
        assertEquals(RepositoryPath.valueOf("~123/abc/def/s*/x*/~sdfg"), path.getDocumentPath());
        assertEquals(RepositoryPath.valueOf("~/234/ghi"), path.getPartPath());
        assertEquals(new IdElement("123", null), path.get(0));        
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "abc", null), path.get(1));
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "def", null), path.get(2));
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "s*", null), path.get(3));
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "x*", null), path.get(4));
        assertEquals(new IdElement("sdfg", null), path.get(5));
        assertEquals(new PartRoot(), path.get(6));
        assertEquals(new NamedElement(ElementType.PART_PATH, "234"), path.get(7));
        assertEquals(new NamedElement(ElementType.PART_PATH, "ghi"), path.get(8));
        assertEquals(9, path.size());        
    }
    
    @Test
    public void testWorkpaceIdDocumentIdWithQueryParts() {
        RepositoryPath  path = RepositoryPath.valueOf("~123/abc/def/s*/x*/~sdfg/~/234/ghi?");
        assertEquals("123", path.getRootId());
        assertEquals(RepositoryPath.valueOf("s*/x*/~sdfg/~/234/ghi?"), path.getQueryPath());
        assertEquals(RepositoryPath.valueOf("~123/abc/def"), path.getSimplePath());
        assertEquals(RepositoryPath.valueOf("~123/abc/def/s*/x*/~sdfg"), path.getDocumentPath());
        assertEquals(RepositoryPath.valueOf("~/234/ghi?"), path.getPartPath());
        
        assertEquals(new IdElement("123", null), path.get(0));
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "abc", null), path.get(1));
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "def", null), path.get(2));
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "s*", null), path.get(3));
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "x*", null), path.get(4));
        assertEquals(new IdElement("sdfg", null), path.get(5));
        assertEquals(new PartRoot(), path.get(6));
        assertEquals(new NamedElement(ElementType.PART_PATH, "234"), path.get(7));
        assertEquals(new NamedElement(ElementType.PART_PATH, "ghi?"), path.get(8));
        assertEquals(9, path.size());        
    }
    
    @Test
    public void testWorkpaceIdDocumentIdWithQueryPartsAndVersions() {
        RepositoryPath  path = RepositoryPath.valueOf("~123@789/abc/def@123/s*/x*/~sdfg@111/~/234/ghi?");
        assertEquals("123", path.getRootId());
        assertEquals(RepositoryPath.valueOf("s*/x*/~sdfg@111/~/234/ghi?"), path.getQueryPath());
        assertEquals(RepositoryPath.valueOf("~123@789/abc/def@123"), path.getSimplePath());
        assertEquals(RepositoryPath.valueOf("~123@789/abc/def@123/s*/x*/~sdfg@111"), path.getDocumentPath());
        assertEquals(RepositoryPath.valueOf("~/234/ghi?"), path.getPartPath());
        assertEquals(RepositoryPath.valueOf("abc/def@123/s*/x*/~sdfg@111/~/234/ghi?"), path.getAfterVersion());
        assertEquals(RepositoryPath.valueOf("~123@789"), path.getVersioned());
        
        assertEquals(new IdElement("123", "789"), path.get(0));
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "abc", null), path.get(1));
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "def", "123"), path.get(2));
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "s*", null), path.get(3));
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "x*", null), path.get(4));
        assertEquals(new IdElement("sdfg", "111"), path.get(5));
        assertEquals(new PartRoot(), path.get(6));
        assertEquals(new NamedElement(ElementType.PART_PATH, "234"), path.get(7));
        assertEquals(new NamedElement(ElementType.PART_PATH, "ghi?"), path.get(8));
        assertEquals(9, path.size());        
    }
    
    @Test
    public void testWorkpaceIdDocumentIdWithQueryPartsAndEscapedVersions() {
        RepositoryPath  path = RepositoryPath.valueOf("~123\\@789/abc/def\\@123/s*/x*/~sdfg\\@111/~/234/ghi?");
        assertEquals("123@789", path.getRootId());
        assertEquals(RepositoryPath.valueOf("s*/x*/~sdfg\\@111/~/234/ghi?"), path.getQueryPath());
        assertEquals(RepositoryPath.valueOf("~123\\@789/abc/def\\@123"), path.getSimplePath());
        assertEquals(RepositoryPath.valueOf("~123\\@789/abc/def\\@123/s*/x*/~sdfg\\@111"), path.getDocumentPath());
        assertEquals(RepositoryPath.valueOf("~/234/ghi?"), path.getPartPath());
        
        assertEquals(new IdElement("123@789", null), path.get(0));
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "abc", null), path.get(1));
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "def@123", null), path.get(2));
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "s*", null), path.get(3));
        assertEquals(new VersionedElement(ElementType.DOCUMENT_PATH, "x*", null), path.get(4));
        assertEquals(new IdElement("sdfg@111", null), path.get(5));
        assertEquals(new PartRoot(), path.get(6));
        assertEquals(new NamedElement(ElementType.PART_PATH, "234"), path.get(7));
        assertEquals(new NamedElement(ElementType.PART_PATH, "ghi?"), path.get(8));
        assertEquals(9, path.size());        
    }
}
