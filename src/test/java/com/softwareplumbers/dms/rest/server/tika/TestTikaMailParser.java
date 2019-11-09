/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.tika;

import java.io.IOException;
import java.io.InputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.mail.RFC822Parser;
import org.apache.tika.parser.microsoft.OfficeParser;
import org.apache.tika.sax.ToXMLContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 *
 * @author SWPNET\jonessex
 */
public class TestTikaMailParser {
    @Test
	public void testParseMessageEML() throws IOException, TikaException, SAXException {
		ContentHandler handler = new ToXMLContentHandler();
	    Parser parser = new RFC822Parser();
	    Metadata metadata = new Metadata();
	    try (InputStream stream = TestTikaMailParser.class.getResourceAsStream("/testEmail.eml")) {
	        parser.parse(stream, handler, metadata, new ParseContext());
	        System.out.println(handler.toString());
	    } 
	}
}
