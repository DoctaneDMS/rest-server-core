package com.softwareplumbers.dms.rest.server.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.microsoft.ooxml.OOXMLParser;
import org.apache.tika.sax.ToXMLContentHandler;
import org.xml.sax.ContentHandler;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerConfigurationException;

import com.softwareplumbers.dms.rest.server.model.Document;

/** Adapts a document into XHTML output using TIKA */
public class XMLOutput implements StreamingOutput {

		private final Document document;

		@Override
		public void write(OutputStream output) throws IOException, WebApplicationException {
			
    		StreamResult saxOutput = new StreamResult(output);    		 
    	    Parser parser = new OOXMLParser();
    	    SAXTransformerFactory factory = (SAXTransformerFactory)SAXTransformerFactory.newInstance();
    	    TransformerHandler handler;
    	    try (InputStream stream = document.getData()) {
				handler = factory.newTransformerHandler();
				handler.setResult(saxOutput);
    	        parser.parse(stream, handler, new Metadata(), new ParseContext());
    	    } catch (TransformerConfigurationException | SAXException | TikaException e) {
    	    	throw new WebApplicationException(e);
			}
		}
		
		public XMLOutput(Document document) { this.document = document; }
}

