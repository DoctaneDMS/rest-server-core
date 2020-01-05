/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.dms.DocumentNavigatorService;
import com.softwareplumbers.dms.StreamableDocumentPart;
import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.DocumentPart;
import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.dms.rest.server.core.MediaTypes;
import static com.softwareplumbers.dms.Constants.EMPTY_METADATA;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.core.MediaType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author jonathan.local
 */
public class TestZipFileNavigatorService {
    
    ZipFileNavigatorService nav = new ZipFileNavigatorService();

    @Test
    public void testListSimpleZipfile() throws IOException, DocumentNavigatorService.DocumentFormatException {
        DocumentImpl zipDoc = new DocumentImpl(new Reference("test"), MediaTypes.ZIP, ()->getClass().getResourceAsStream("/testzip.zip"), EMPTY_METADATA);
        List<DocumentPart> parts = nav.catalogParts(zipDoc).collect(Collectors.toList());
        assertEquals(3, parts.size());
        assertEquals("testdoc.docx", parts.get(0).getName().part);
        assertEquals("testdoc_outlook2010.msg", parts.get(1).getName().part);
        assertEquals("test1.txt", parts.get(2).getName().part);
        assertEquals(MediaTypes.MICROSOFT_WORD_XML, ((StreamableDocumentPart)parts.get(0)).getMediaType());
        assertEquals(MediaTypes.MICROSOFT_OUTLOOK, ((StreamableDocumentPart)parts.get(1)).getMediaType());
        assertEquals(MediaType.TEXT_PLAIN_TYPE, ((StreamableDocumentPart)parts.get(2)).getMediaType());
    }
    
    @Test
    public void testListZipDir() throws IOException, DocumentNavigatorService.DocumentFormatException {
        DocumentImpl zipDoc = new DocumentImpl(new Reference("test"), MediaTypes.ZIP, ()->getClass().getResourceAsStream("/testzipdir.zip"), EMPTY_METADATA);
        List<DocumentPart> parts = nav.catalogParts(zipDoc).collect(Collectors.toList());
        assertEquals(5, parts.size());
        assertEquals("test1.txt", parts.get(1).getName().part);
        assertEquals("test2.txt", parts.get(2).getName().part);
        assertEquals("testdoc.docx", parts.get(4).getName().part);
        assertEquals(MediaType.TEXT_PLAIN_TYPE, ((StreamableDocumentPart)parts.get(1)).getMediaType());
        assertEquals(MediaType.TEXT_PLAIN_TYPE, ((StreamableDocumentPart)parts.get(2)).getMediaType());
        assertEquals(MediaTypes.MICROSOFT_WORD_XML, ((StreamableDocumentPart)parts.get(4)).getMediaType());
    }
    
    @Test
    public void testFindZipPart() throws IOException, DocumentNavigatorService.DocumentFormatException, DocumentNavigatorService.PartNotFoundException {
        DocumentImpl zipDoc = new DocumentImpl(new Reference("test"), MediaTypes.ZIP, ()->getClass().getResourceAsStream("/testzipdir.zip"), EMPTY_METADATA);
        DocumentPart test1 = nav.getPartByName(zipDoc, QualifiedName.of("test","test1.txt"));
        DocumentPart test2 = nav.getPartByName(zipDoc, QualifiedName.of("test","test2.txt"));
        DocumentPart testDocx = nav.getPartByName(zipDoc, QualifiedName.of("test", "subdir", "testdoc.docx"));
        assertEquals("test1.txt", test1.getName().part);
        assertEquals("test2.txt", test2.getName().part);
        assertEquals("testdoc.docx", testDocx.getName().part);
    }
    
    @Test(expected = DocumentNavigatorService.PartNotFoundException.class)
    public void testCantFindZipPart() throws IOException, DocumentNavigatorService.DocumentFormatException, DocumentNavigatorService.PartNotFoundException {
        StreamableRepositoryObjectImpl zipDoc = new DocumentImpl(new Reference("test"), MediaTypes.ZIP, ()->getClass().getResourceAsStream("/testzipdir.zip"), EMPTY_METADATA);
        DocumentPart testDocx = nav.getPartByName(zipDoc, QualifiedName.of("booyah", "subdir", "testdoc.docx"));
    }
    
    @Test
    public void testCanNavigate() throws IOException {
        DocumentImpl zipDoc = new DocumentImpl(new Reference("test"), MediaTypes.ZIP, ()->getClass().getResourceAsStream("/testzipdir.zip"), EMPTY_METADATA);
        assertTrue("can navigate a zipfile", nav.canNavigate(zipDoc));
        DocumentImpl textDoc = new DocumentImpl(new Reference("test"), MediaType.TEXT_PLAIN_TYPE, ()->getClass().getResourceAsStream("/test1.txt"), EMPTY_METADATA);
        assertFalse("can't navigate a text file", nav.canNavigate(textDoc));
    }
    
}
