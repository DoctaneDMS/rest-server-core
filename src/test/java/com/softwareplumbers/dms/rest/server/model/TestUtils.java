/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.common.QualifiedName;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Supplier;
import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;

/**
 *
 * @author SWPNET\jonessex
 */
public class TestUtils {
    
	public static final String[] NAMES = { "julien", "peter", "fairfax", "austen", "celtic", "a", "the", "halibut", "eaten" };
	public static final String CHARACTERS = "-._";
	public static final String RESERVED = "&$+,/:;=?@#";
	
	public static int unique = 0;
	
	public static final String randomUrlSafeName() {
		StringBuilder buffer = new StringBuilder(NAMES[(int)(Math.random() * NAMES.length)]);
		buffer.append(CHARACTERS.charAt((int)(Math.random() * CHARACTERS.length())));
		buffer.append(Integer.toHexString(unique++));
		return buffer.toString();
	}
	
	public static final String randomReservedName() {
		StringBuilder buffer = new StringBuilder();
		for (int i = 1; i < 3; i++) {
			buffer.append(RESERVED.charAt((int)(Math.random() * RESERVED.length())));
			buffer.append(randomUrlSafeName());
		}
		return buffer.toString();
	}
	
	public static QualifiedName randomQualifiedName() {
		QualifiedName result = QualifiedName.ROOT;
		for (int i = 0; i < 3; i++) result = result.add(randomUrlSafeName());
		return result;
	}
		
	public static final String randomText() {
		StringBuilder buffer = new StringBuilder();
		for (int i = 1; i < 10; i++) {
			buffer.append(randomUrlSafeName());
			buffer.append(" ");
		}
		return buffer.toString();		
	}
	
	public static final InputStream toStream(String out) {
		return new ByteArrayInputStream(out.getBytes());
	}

	public static final String getDocText(Document doc) throws IOException {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		doc.writeDocument(stream);
		return new String(stream.toByteArray());
	}
    
    @FunctionalInterface
    public static interface DocDataConsumer {
        public void consume(byte[] data, JsonObject metadata, MediaType type);
    }
    	
    public static void generateDocs(int count, Supplier<JsonObject> metadataSupplier, DocDataConsumer consumer) {
        for (int i = 0; i < count; i++) {
            consumer.consume(randomText().getBytes(), metadataSupplier.get(), MediaType.TEXT_PLAIN_TYPE);
        }
    }
    
}
