/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.dms.StreamableRepositoryObject;
import com.softwareplumbers.dms.DocumentPart;
import com.softwareplumbers.dms.Constants;
import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.dms.Document;
import com.softwareplumbers.dms.Exceptions.InvalidObjectName;
import com.softwareplumbers.dms.NamedRepositoryObject;
import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.RepositoryBrowser;
import com.softwareplumbers.dms.RepositoryObject;
import com.softwareplumbers.dms.RepositoryService;
import com.softwareplumbers.dms.common.impl.DocumentPartImpl;
import com.softwareplumbers.dms.common.impl.LocalData;
import com.softwareplumbers.dms.common.impl.StreamableDocumentPartImpl;
import com.softwareplumbers.dms.rest.server.core.MediaTypes;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.Optional;
import javax.ws.rs.core.MediaType;
import org.apache.commons.io.IOUtils;

/** Implementation of DocumentNavigatorService over ZipFiles.
 *
 * @author Jonathan Essex
 */
public class ZipFileHandler {
    
    private static class ZipFileData implements LocalData {
        public final LocalData delegate;
        public final LinkedList<NamedRepositoryObject> children;
        public final RepositoryObject parent;
        public DocumentPart self;
        public final byte[] data;

        @Override
        public Stream<NamedRepositoryObject> getChildren(RepositoryBrowser service, RepositoryObject object) {
            return children.stream();
        }

        @Override
        public NamedRepositoryObject getChild(RepositoryBrowser service, RepositoryObject object, QualifiedName name) throws InvalidObjectName {
            if (name.parent.isEmpty()) {
                return children.stream().filter(child -> child.getName().part.equals(name.part)).findAny().orElseThrow(()->new InvalidObjectName(Constants.NO_ID, name));
            } else {
                return getChild(service, object, name.parent).getChild(service, QualifiedName.of(name.part));
            }
        }
        
        @Override
        public Optional<RepositoryObject> getParent(RepositoryBrowser service, NamedRepositoryObject object) {
            return Optional.of(parent);
        }

        @Override
        public Optional<RepositoryObject> getObject(RepositoryBrowser service, Reference ref, Optional<QualifiedName> part) {
            return delegate.getObject(service, ref, part);
        }

        @Override
        public Optional<RepositoryObject> getObject(RepositoryBrowser service, QualifiedName name, Optional<QualifiedName> part) {
            return delegate.getObject(service, name, part);
        }
        
        public ZipFileData(LocalData delegate, RepositoryObject parent, byte[] data) {
            this.delegate = delegate;
            this.parent = parent;
            this.children = new LinkedList<>();
            this.data = data;
            this.self = null;
        }
                
        @Override
        public InputStream getData(RepositoryBrowser service, StreamableRepositoryObject object) throws IOException {
            return new ByteArrayInputStream(data);
        }

        @Override
        public void writeData(RepositoryBrowser service, StreamableRepositoryObject object, OutputStream output) throws IOException {
            output.write(data);
        }
    }
            
    public DocumentPart build(RepositoryService service, Document zipfile, Optional<QualifiedName> parentName) throws IOException {

        Map<QualifiedName, ZipFileData> localData = new HashMap<>();

        try (ZipInputStream zifs = new ZipInputStream(zipfile.getData(service))) {
            ZipEntry currentEntry = zifs.getNextEntry();    
        
            while (currentEntry != null) {
                QualifiedName name = QualifiedName.parse(currentEntry.getName(), "/");
                Optional<ZipFileData> parentData = name.isEmpty() ? Optional.empty() : Optional.of(localData.get(name.parent));
                RepositoryObject parentObject = name.isEmpty() ? zipfile : parentData.get().self;
                DocumentPart result;
                ZipFileData node;
                if (currentEntry.isDirectory()) {
                    node  = new ZipFileData(LocalData.NONE, parentObject, null);
                    result = new DocumentPartImpl(zipfile.getReference(), parentName, name, Constants.EMPTY_METADATA, true, node);
                    
                } else  {
                    MediaType type = MediaTypes.getTypeFromFilename(name.part);
                    node  = new ZipFileData(LocalData.NONE, parentObject, IOUtils.toByteArray(zifs));
                    result = new StreamableDocumentPartImpl(zipfile.getReference(), parentName, name, type.toString(), node.data.length, Constants.NO_DIGEST, Constants.EMPTY_METADATA, false, node);
                }
                node.self = result;
                if (parentData.isPresent()) parentData.get().children.add(result);
                zifs.closeEntry();
                currentEntry = zifs.getNextEntry();
            }
        }
        
        // Technically we should try to correct any directory entries with zero members to navigable = false;
        
        return localData.get(QualifiedName.ROOT).self;
        
    }

    
    public boolean canHandle(Document document) {
        return MediaTypes.ZIP.isCompatible(MediaType.valueOf(document.getMediaType()));
    }
}
