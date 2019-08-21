/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.dms.rest.server.core.MediaTypes;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Objects;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.TreeMap;
import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author jonathan.local
 */
public class ZipFileNavigatorService implements DocumentNavigatorService {
        
    private static class ZipFilePart implements DocumentPart {
        
        private StreamableRepositoryObject document;
        private QualifiedName name;
        private byte[] bytes;
        private MediaType mediaType;

        @Override
        public QualifiedName getName() {
            return name;
        }

        @Override
        public JsonObject getMetadata() {
            return Constants.EMPTY_METADATA;
        }

        @Override
        public MediaType getMediaType() {
            return mediaType;
        }

        @Override
        public void writeDocument(OutputStream target) throws IOException {
            target.write(bytes);
        }
        
        @Override
        public StreamableRepositoryObject getDocument() {
            return document;
        }

        @Override
        public InputStream getData() throws IOException {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public long getLength() {
            return bytes.length;
        }   
        
        public ZipFilePart(StreamableRepositoryObject document, QualifiedName name, MediaType mediaType, byte[] bytes) throws IOException {
            this.name = name;
            this.mediaType = mediaType;
            this.bytes = bytes;
            this.document = document;
        }
        
        public static ZipFilePart from(StreamableRepositoryObject document, long id, QualifiedName name, MediaType mediaType, InputStream bytes) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            IOUtils.copy(bytes, buffer);
            return new ZipFilePart(document, name, mediaType, buffer.toByteArray());
        }
    }

    @Override
    public Optional<DocumentPart> getOptionalPartByName(StreamableRepositoryObject document, QualifiedName partName) throws DocumentFormatException {
        return catalogParts(document)
                .filter(part -> Objects.equals(part.getName(), partName))
                .findAny();
    }
    
    private static class ZipIterator implements Iterator<DocumentPart>, Closeable {

        private StreamableRepositoryObject zipfile;
        private ZipEntry currentEntry;
        private ZipInputStream zifs;
        private int index = 0;
        
        public ZipIterator(StreamableRepositoryObject zipfile) throws IOException {
            this.zifs = new ZipInputStream(zipfile.getData());
            this.currentEntry = zifs.getNextEntry();
            this.zipfile = zipfile;
        }
        
        @Override
        public boolean hasNext() {
            return currentEntry != null;
        }

        @Override
        public DocumentPart next() {
            try {
                QualifiedName name = QualifiedName.parse(currentEntry.getName(), "/");
                MediaType type = MediaTypes.getTypeFromFilename(name.part);
                ZipFilePart result = ZipFilePart.from(zipfile, index, name, type, zifs);
                index++;
                zifs.closeEntry();
                currentEntry = zifs.getNextEntry();
                return result;
            } catch (IOException e) {
                throw new RuntimeException("Could not retrieve next entry in zip stream", e);
            }
        }
        
        @Override
        public void close() throws IOException {
            zifs.close();
        }
    }

    public Stream<DocumentPart> catalogParts(StreamableRepositoryObject document) throws DocumentFormatException {
        try {
        ZipIterator zi = new ZipIterator(document);
        return StreamSupport
                .stream(Spliterators.spliteratorUnknownSize(zi, Spliterator.ORDERED | Spliterator.IMMUTABLE), true)
                .onClose(()-> { try { zi.close(); } catch (IOException e) { } });
        } catch (IOException ex) {
            throw new DocumentFormatException("Can't parse as a zipfile", ex);
        }
    }

    @Override
    public Stream<DocumentPart> catalogParts(StreamableRepositoryObject document, QualifiedName partName) throws DocumentFormatException {
        return catalogParts(document).filter(part -> Objects.equals(part.getName().parent, partName));
    }

    @Override
    public boolean canNavigate(StreamableRepositoryObject document) {
        return document.getMediaType().equals(MediaTypes.ZIP);
    }
    
}
