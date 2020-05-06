/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.dms.StreamableRepositoryObject;
import com.softwareplumbers.dms.DocumentPart;
import com.softwareplumbers.dms.Constants;
import com.softwareplumbers.dms.Document;
import com.softwareplumbers.dms.Exceptions.InvalidObjectName;
import com.softwareplumbers.dms.NamedRepositoryObject;
import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.RepositoryBrowser;
import com.softwareplumbers.dms.RepositoryObject;
import com.softwareplumbers.dms.RepositoryPath;
import com.softwareplumbers.dms.RepositoryPath.NamedElement;
import com.softwareplumbers.dms.RepositoryService;
import com.softwareplumbers.dms.common.impl.DocumentPartImpl;
import com.softwareplumbers.dms.common.impl.LocalData;
import com.softwareplumbers.dms.common.impl.StreamableDocumentPartImpl;
import com.softwareplumbers.dms.rest.server.core.MediaTypes;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.core.MediaType;
import org.apache.commons.io.IOUtils;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

/** Implementation of DocumentNavigatorService over ZipFiles.
 *
 * @author Jonathan Essex
 */
public class ZipFileHandler implements PartHandler {
    
    private static final XLogger LOG = XLoggerFactory.getXLogger(ZipFileHandler.class);

    
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
        public NamedRepositoryObject getChild(RepositoryBrowser service, RepositoryObject object, RepositoryPath name) throws InvalidObjectName {
            LOG.entry(service, object, name);
            if (name.parent.isEmpty()) {
                switch (name.part.type) {
                    case PART_ROOT:
                        return LOG.exit(self);
                    case PART_PATH:
                        NamedElement part = (NamedElement)name.part;
                        return LOG.exit(
                            children.stream().filter(child -> child.getName().part.equals(name.part)).findAny()
                                .orElseThrow(()->new InvalidObjectName(name))
                        );
                    default:
                        throw LOG.throwing(new InvalidObjectName(name));
                           
                }
            } else {
                return LOG.exit(
                    getChild(service, object, name.parent).getChild(service, RepositoryPath.ROOT.add(name.part))
                );
            }
        }
        
        @Override
        public Optional<RepositoryObject> getParent(RepositoryBrowser service, NamedRepositoryObject object) {
            return Optional.of(parent);
        }

        @Override
        public Optional<RepositoryObject> getObject(RepositoryBrowser service, Reference ref, RepositoryPath part) {
            return delegate.getObject(service, ref, part);
        }

        @Override
        public Optional<RepositoryObject> getObject(RepositoryBrowser service, RepositoryPath part) {
            return delegate.getObject(service, part);
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
    
    private ZipFileData createEntry(RepositoryPath name, Document zipfile) {
        ZipFileData newEntry = new ZipFileData(LocalData.NONE, zipfile, null);
        DocumentPart part = new DocumentPartImpl(zipfile.getReference(), name, Constants.EMPTY_METADATA, true, newEntry);
        newEntry.self = part;
        return newEntry;
    }
    
    private ZipFileData getOrCreateEntry(Map<RepositoryPath, ZipFileData> localData, RepositoryPath path, Document zipfile) {
        return localData.computeIfAbsent(path, name->createEntry(name, zipfile));
    }
    
    private void addIfNotNull(JsonObjectBuilder builder, String name, Object value) {
        if (value != null) builder.add(name, value.toString());        
    }
        
    private JsonObject convertMetadata(ZipEntry entry) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        addIfNotNull(builder, "Comment", entry.getComment());
        addIfNotNull(builder, "CreationTime", entry.getCreationTime());
        addIfNotNull(builder, "LastModifiedTime", entry.getLastModifiedTime());
        addIfNotNull(builder, "LastAccessTime", entry.getLastAccessTime());
        return builder.build();
    }
            
    public DocumentPart build(RepositoryService service, Document zipfile) {
        
        LOG.entry(service, zipfile);

        Map<RepositoryPath, ZipFileData> localData = new HashMap<>();
        RepositoryPath parentName = zipfile instanceof NamedRepositoryObject ? ((NamedRepositoryObject)zipfile).getName() : RepositoryPath.ROOT;

        getOrCreateEntry(localData, parentName, zipfile);
        
        try (ZipInputStream zifs = new ZipInputStream(zipfile.getData(service))) {
            ZipEntry currentEntry = zifs.getNextEntry();    
        
            while (currentEntry != null) {
                RepositoryPath name = parentName.addRootPart().addPartPaths(currentEntry.getName().split("/"));
                ZipFileData parentData = getOrCreateEntry(localData, name.parent, zipfile);
                RepositoryObject parentObject = parentData.self;
                DocumentPart result;
                ZipFileData node;
                if (currentEntry.isDirectory()) {
                    node  = new ZipFileData(LocalData.NONE, parentObject, null);
                    result = new DocumentPartImpl(zipfile.getReference(), name, convertMetadata(currentEntry), true, node);                    
                } else  {
                    MediaType type = MediaTypes.getTypeFromFilename(name.part.getName().get());
                    node  = new ZipFileData(LocalData.NONE, parentObject, IOUtils.toByteArray(zifs));
                    result = new StreamableDocumentPartImpl(zipfile.getReference(), name, type.toString(), node.data.length, Constants.NO_DIGEST, convertMetadata(currentEntry), false, node);
                }
                node.self = result;
                parentData.children.add(result);
                localData.put(name, node);
                zifs.closeEntry();
                currentEntry = zifs.getNextEntry();
            }
        } catch (IOException e) {
            throw LOG.throwing(new RuntimeException(e));
        }
        
        // Technically we should try to correct any directory entries with zero members to navigable = false;
        
        return LOG.exit(localData.get(parentName.addRootPart()).self);
        
    }

    
    public boolean canHandle(Document document) {
        return MediaTypes.ZIP.isCompatible(MediaType.valueOf(document.getMediaType()));
    }
}
