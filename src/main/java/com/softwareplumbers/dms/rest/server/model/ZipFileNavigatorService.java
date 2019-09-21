/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractpattern.parsers.Parsers;
import com.softwareplumbers.common.abstractpattern.visitor.Builders;
import com.softwareplumbers.common.abstractpattern.visitor.Visitor.PatternSyntaxException;
import com.softwareplumbers.dms.rest.server.core.MediaTypes;
import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.Optional;
import java.util.Spliterator;
import javax.ws.rs.core.MediaType;
import org.apache.commons.io.IOUtils;

/** Implementation of DocumentNavigatorService over ZipFiles.
 *
 * @author Jonathan Essex
 */
public class ZipFileNavigatorService implements DocumentNavigatorService {
            
    private static class ZipIterator implements Iterator<DocumentPart>, Closeable {

        private StreamableRepositoryObject zipfile;
        private ZipEntry currentEntry;
        private ZipInputStream zifs;
        
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
                DocumentPart result;
                if (currentEntry.isDirectory()) {
                    result = new DocumentPartImpl(zipfile, name, Constants.EMPTY_METADATA);
                } else  {
                    MediaType type = MediaTypes.getTypeFromFilename(name.part);
                    result = new StreamableDocumentPartImpl(zipfile, name, type, IOUtils.toByteArray(zifs), Constants.EMPTY_METADATA);
                }
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

    @Override
    public Optional<DocumentPart> getOptionalPartByName(StreamableRepositoryObject document, QualifiedName partName) throws DocumentFormatException {
        return catalogParts(document)
                .filter(part -> Objects.equals(part.getName(), partName))
                .findAny();
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
        try {
            boolean isSimple = partName.apply(true, (a, element) -> a && Parsers.parseUnixWildcard(element).isSimple());
            if (!isSimple) {
                QualifiedName template = partName.transform(element -> Parsers.parseUnixWildcard(element).build(Builders.toRegex()));
                return catalogParts(document).filter(part -> part.getName().matches(template, true));
            } else {
                return catalogParts(document).filter(part -> Objects.equals(part.getName().parent, partName));
            }
        } catch (PatternSyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean canNavigate(StreamableRepositoryObject document) {
        return document.getMediaType().equals(MediaTypes.ZIP);
    }
    
    @Override
    public boolean canNavigate(StreamableRepositoryObject document, QualifiedName partName) {
        return canNavigate(document) && catalogParts(document)
                .anyMatch(part -> Objects.equals(part.getName().parent, partName));
                
    }
}
