/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.dms.StreamableDocumentPart;
import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.DocumentPart;
import com.softwareplumbers.common.immutablelist.QualifiedName;
import com.softwareplumbers.dms.rest.server.core.MediaTypes;
import static com.softwareplumbers.dms.Constants.EMPTY_METADATA;
import com.softwareplumbers.dms.Document;
import com.softwareplumbers.dms.Exceptions.InvalidObjectName;
import com.softwareplumbers.dms.common.impl.RepositoryObjectFactory;
import java.io.IOException;
import java.util.Optional;
import javax.ws.rs.core.MediaType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author jonathan.local
 */
public class TestZipFileNavigatorService {
    
    ZipFileHandler nav = new ZipFileHandler();
    RepositoryObjectFactory factory = RepositoryObjectFactory.getInstance();

    @Test
    public void testListSimpleZipfile() throws IOException, InvalidObjectName {
        Document zipDoc = factory.buildDocument(new Reference("test"), MediaTypes.ZIP.toString(), ()->getClass().getResourceAsStream("/testzip.zip"), EMPTY_METADATA, true);
        DocumentPart parts = nav.build(null, zipDoc);
        assertEquals(3, parts.getChildren(null).count());
        StreamableDocumentPart part1 = (StreamableDocumentPart)parts.getChild(null, QualifiedName.of("testdoc.docx"));
        StreamableDocumentPart part2 = (StreamableDocumentPart)parts.getChild(null, QualifiedName.of("testdoc_outlook2010.msg"));
        StreamableDocumentPart part3 = (StreamableDocumentPart)parts.getChild(null, QualifiedName.of("test1.txt"));
        assertEquals("testdoc.docx", part1.getName().part);
        assertEquals("testdoc_outlook2010.msg", part2.getName().part);
        assertEquals("test1.txt", part3.getName().part);
        assertEquals(MediaTypes.MICROSOFT_WORD_XML, MediaType.valueOf(part1.getMediaType()));
        assertEquals(MediaTypes.MICROSOFT_OUTLOOK, MediaType.valueOf(part2.getMediaType()));
        assertEquals("text/plain", part3.getMediaType());
        assertNotNull(part1.getMetadata().get("LastModifiedTime"));
    }
    
    @Test
    public void testListZipDir() throws IOException, InvalidObjectName  {
        Document zipDoc = factory.buildDocument(new Reference("test"), MediaTypes.ZIP.toString(), ()->getClass().getResourceAsStream("/testzipdir.zip"), EMPTY_METADATA, true);
        DocumentPart parts = nav.build(null, zipDoc);
        assertEquals(1, parts.getChildren(null).count());
        StreamableDocumentPart part1 = (StreamableDocumentPart)parts.getChild(null, QualifiedName.of("test","test1.txt"));
        StreamableDocumentPart part2 = (StreamableDocumentPart)parts.getChild(null, QualifiedName.of("test","test2.txt"));        
        StreamableDocumentPart part3 = (StreamableDocumentPart)parts.getChild(null, QualifiedName.of("test","subdir","testdoc.docx"));
        assertEquals("test1.txt", part1.getName().part);
        assertEquals("test2.txt", part2.getName().part);
        assertEquals("testdoc.docx", part3.getName().part);
        assertEquals("text/plain", part1.getMediaType());
        assertEquals("text/plain", part2.getMediaType());
        assertEquals(MediaTypes.MICROSOFT_WORD_XML.toString(), part3.getMediaType());
    }
    
    
    @Test
    public void testCanNavigate() throws IOException {
        Document zipDoc = factory.buildDocument(new Reference("test"), MediaTypes.ZIP.toString(), ()->getClass().getResourceAsStream("/testzipdir.zip"), EMPTY_METADATA, true);
        assertTrue("can navigate a zipfile", nav.canHandle(zipDoc));
        Document textDoc = factory.buildDocument(new Reference("test"), "text/plain", ()->getClass().getResourceAsStream("/test1.txt"), EMPTY_METADATA, true);
        assertFalse("can't navigate a text file", nav.canHandle(textDoc));
    }
    
}
