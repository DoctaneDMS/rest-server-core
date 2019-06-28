package com.softwareplumbers.dms.rest.server.tika;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.microsoft.ooxml.OOXMLParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.microsoft.OfficeParser;
import org.apache.tika.sax.ToXMLContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class TestTikaOfficeParser {

	@Test
	public void testParseDocument() throws IOException, TikaException, SAXException {
		ContentHandler handler = new ToXMLContentHandler();
		 
	    Parser parser = new OOXMLParser();
	    Metadata metadata = new Metadata();
	    try (InputStream stream = TestTikaOfficeParser.class.getResourceAsStream("/testdoc.docx")) {
	        parser.parse(stream, handler, metadata, new ParseContext());
	        System.out.println(handler.toString());
	    } 
	}

	@Test
	public void testParseMessageOutlook2010() throws IOException, TikaException, SAXException {
		ContentHandler handler = new ToXMLContentHandler();
	    Parser parser = new OfficeParser();
	    Metadata metadata = new Metadata();
	    try (InputStream stream = TestTikaOfficeParser.class.getResourceAsStream("/testdoc_outlook2010.msg")) {
	        parser.parse(stream, handler, metadata, new ParseContext());
	        System.out.println(handler.toString());
	    } 
	}
}
