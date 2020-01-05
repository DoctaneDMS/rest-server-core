/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.common.impl;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.dms.Document;
import com.softwareplumbers.dms.DocumentLink;
import com.softwareplumbers.dms.InputStreamSupplier;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;
import org.apache.commons.compress.utils.IOUtils;

/**
 *
 * @author jonathan
 */
public class DocumentImpl implements Document {
    
    private final JsonObject metadata;
    private final String id;
    private final String version;
    private final MediaType type;
    private final InputStreamSupplier data;
    private final long length;
    
    public DocumentImpl(String id, String version, MediaType type, InputStreamSupplier data, long length, JsonObject metadata) {
        this.id = id;
        this.version = version;
        this.type = type;
        this.data = data;
        this.length = length;
        this.metadata = metadata;
    }
    
   @Override
    public JsonObject getMetadata() {
        return metadata;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public MediaType getMediaType() {
        return type;
    }

    @Override
    public void writeDocument(OutputStream target) throws IOException {
        try (InputStream is = data.get()) {
            IOUtils.copy(is, target);
        }
    }

    @Override
    public InputStream getData() throws IOException {
        return data.get();
    }

    @Override
    public long getLength() {
        return length;
    }
    
}
