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

import org.xml.sax.SAXException;

import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.TransformerConfigurationException;

import com.softwareplumbers.dms.rest.server.model.Document;
import com.softwareplumbers.dms.rest.server.model.DocumentLink;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.tika.parser.microsoft.OfficeParser;
import org.apache.tika.parser.mail.RFC822Parser;

/** Adapts a document into XHTML output using TIKA */
public class XMLOutput implements StreamingOutput {
    
    public static class CannotConvertFormatException extends Exception {

        public final MediaType mediaType;
        public final String fileName;

        CannotConvertFormatException(MediaType mediaType, String fileName) {
            super(String.format("Cannot convert %s to XML, or cannot determine type from name: %s", mediaType, fileName));
            this.mediaType = mediaType;
            this.fileName = fileName;
        }
    }

		private final Document document;

		@Override
		public void write(OutputStream output) throws IOException, WebApplicationException {
			
    		StreamResult saxOutput = new StreamResult(output);    		 
    	    SAXTransformerFactory factory = (SAXTransformerFactory)SAXTransformerFactory.newInstance();
    	    TransformerHandler handler;
        	Parser parser;
            MediaType type = document.getMediaType();
            String name = document instanceof DocumentLink ? ((DocumentLink)document).getName().part : null;
            
    	    try (InputStream stream = document.getData()) {
                if (MediaTypes.isOpenOfficeXMLDoc(document.getMediaType(), name))
                    parser = new OOXMLParser();
                else if (MediaTypes.isLegacyOfficeDoc(document.getMediaType(), name))
                    parser = new OfficeParser();
                else if (MediaTypes.isRFC822Message(document.getMediaType(), name))
                    parser = new RFC822Parser();
                else
                    throw new CannotConvertFormatException(type, name);
            
				handler = factory.newTransformerHandler();
				handler.setResult(saxOutput);
    	        parser.parse(stream, handler, new Metadata(), new ParseContext());
    	    } catch (CannotConvertFormatException e) {
    	    	throw new WebApplicationException(e, Response.status(Response.Status.INTERNAL_SERVER_ERROR).type(MediaType.APPLICATION_JSON_TYPE).entity(Error.mapServiceError(e)).build());
			} catch (TransformerConfigurationException | SAXException | TikaException e) {
                throw new WebApplicationException(e);
            }
		}
		
		public XMLOutput(Document document) { this.document = document; }
}

