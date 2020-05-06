/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.common.abstractpattern.visitor.Visitor.PatternSyntaxException;
import com.softwareplumbers.common.abstractquery.Query;
import com.softwareplumbers.dms.Constants;
import com.softwareplumbers.dms.Document;
import com.softwareplumbers.dms.DocumentLink;
import com.softwareplumbers.dms.DocumentPart;
import com.softwareplumbers.dms.Exceptions;
import com.softwareplumbers.dms.Exceptions.InvalidReference;
import com.softwareplumbers.dms.NamedRepositoryObject;
import com.softwareplumbers.dms.Options;
import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.RepositoryObject;
import com.softwareplumbers.dms.RepositoryPath;
import com.softwareplumbers.dms.RepositoryPath.ElementType;
import com.softwareplumbers.dms.RepositoryPath.NamedElement;
import com.softwareplumbers.dms.RepositoryService;
import com.softwareplumbers.dms.StreamableDocumentPart;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

/**
 *
 * @author jonathan
 */
public class PartHandlerService extends RepositoryDecorator {
    
    private static final XLogger LOG = XLoggerFactory.getXLogger(PartHandlerService.class);
  
    private final Map<Reference, DocumentPart> cache = Collections.synchronizedMap(new WeakHashMap<>());
    private PartHandler[] handlers;
    
    @Override
    public <T extends RepositoryObject> T mapResult(T result) {
        switch(result.getType()) {
            case DOCUMENT:
            case DOCUMENT_LINK:
                Optional<PartHandler> handler = getHandler((Document)result);
                if (handler.isPresent())
                    return (T)result.setNavigable(true);
                else
                    return result;
            default:
                return result;
        }
    }    
    
    @Override
    public <T extends RepositoryObject> Stream<T> mapResult(Stream<T> result) {
        return result.map(this::mapResult);
    }   
    
    public PartHandlerService(RepositoryService repository, PartHandler... handlers) {
        super(repository);
        LOG.entry(repository, handlers);
        this.handlers = handlers;
        LOG.exit();
    }
    
    public PartHandlerService() {
        this(null, new PartHandler[] {});
    }
    
    public void setHandlers(PartHandler[] handlers) {
        this.handlers = handlers;
    }
    
    Optional<PartHandler> getHandler(Document document) {
        return Stream.of(handlers).filter(handler->handler.canHandle(document)).findAny();
    }
    
    Optional<DocumentPart> getRootPart(Document document) {
        LOG.entry(document);
        DocumentPart result = cache.get(document.getReference());
        if (result == null) {
            Optional<PartHandler> handler = getHandler(document);
            if (handler.isPresent()) {
                result = handler.get().build(this, document); 
                cache.put(document.getReference(), result);
                return LOG.exit(Optional.of(result));
            } else {
                return LOG.exit(Optional.empty());
            }
        } else {
            return LOG.exit(Optional.of(result));
        }
    }
    
    DocumentPart getChildPart(Document document, RepositoryPath partName) throws Exceptions.InvalidObjectName {
        LOG.entry(document, partName);
        Optional<DocumentPart> rootPart = getRootPart(document);
        if (rootPart.isPresent()) {
            if (partName.isEmpty())
                return LOG.exit(rootPart.get());
           else
                return LOG.exit((DocumentPart)rootPart.get().getChild(this, partName));                           
        } else {
            throw LOG.throwing(new Exceptions.InvalidObjectName(partName));                    
        }        
    }
    
    StreamableDocumentPart getStreamableChildPart(Document document, RepositoryPath partName) throws Exceptions.InvalidObjectName {
        LOG.entry(document, partName);
        DocumentPart part = getChildPart(document, partName);
        if (part.getType() != RepositoryObject.Type.STREAMABLE_DOCUMENT_PART) 
            throw LOG.throwing(new Exceptions.InvalidObjectName(partName));
        else {
            return LOG.exit((StreamableDocumentPart)part);
        } 
    }
       
    @Override
    public InputStream getData(RepositoryPath objectName, Options.Get... options) throws Exceptions.InvalidObjectName, IOException {
        LOG.entry(objectName, Options.loggable(options));
        RepositoryPath partName = objectName.getPartPath();
        
        if (!partName.isEmpty()) {
            try {
                DocumentLink object = baseRepository.getDocumentLink(objectName.getDocumentPath(), options);
                return LOG.exit(getStreamableChildPart(object, partName).getData(this));
            } catch (Exceptions.InvalidWorkspace e) {
                throw LOG.throwing(new Exceptions.InvalidObjectName(objectName));
            }           
        } else {
            return LOG.exit(baseRepository.getData(objectName, options));
        }
    }

    @Override
    public void writeData(RepositoryPath objectName, OutputStream out, Options.Get... options) throws Exceptions.InvalidObjectName, IOException {
        LOG.entry(objectName, "<out>", Options.loggable(options));
        RepositoryPath partName = objectName.getPartPath();
        
        if (!partName.isEmpty()) {
            try {
                DocumentLink object = baseRepository.getDocumentLink(objectName, options);
                getStreamableChildPart(object, partName).writeDocument(this, out);
            } catch (Exceptions.InvalidWorkspace e) {
                throw LOG.throwing(new Exceptions.InvalidObjectName(objectName));
            }           
        } else {
            baseRepository.writeData(objectName, out, options);
        }
        LOG.exit();
    }
    
    @Override
    public DocumentPart getPart(Reference rfrnc, RepositoryPath partName) throws Exceptions.InvalidReference, Exceptions.InvalidObjectName {
        LOG.entry(rfrnc, partName);
        Document object = baseRepository.getDocument(rfrnc);
        return LOG.exit(getChildPart(object, partName));
    }

    @Override
    public InputStream getData(Reference rfrnc, RepositoryPath partName) throws Exceptions.InvalidReference, Exceptions.InvalidObjectName, IOException {
        LOG.entry(rfrnc, partName);
        if (partName.isEmpty()) {            
            return LOG.exit(baseRepository.getData(rfrnc, partName));
        } else {    
            Document object = baseRepository.getDocument(rfrnc);
            return LOG.exit(getStreamableChildPart(object, partName).getData(this));
        }
    }

    @Override
    public void writeData(Reference rfrnc, RepositoryPath partName, OutputStream out) throws Exceptions.InvalidReference, Exceptions.InvalidObjectName, IOException {
        LOG.entry(rfrnc, partName, "<out>");
        if (partName.isEmpty()) {            
            baseRepository.writeData(rfrnc, partName, out);
        } else {    
            Document object = baseRepository.getDocument(rfrnc);
            getStreamableChildPart(object, partName).writeDocument(this, out);
        }
        LOG.exit();
    }
    
    private Stream<DocumentPart> getMatchingChildren(DocumentPart part, RepositoryPath partName) {
        LOG.entry(part, partName);
        if (partName.parent.isEmpty() || partName.parent.part.type == ElementType.PART_ROOT) {
            Predicate<NamedRepositoryObject> matcher = element-> { 
                try {
                    return ((NamedElement)partName.part).pattern.match(((NamedElement)element.getName().part).name);
                } catch (PatternSyntaxException e) {
                    throw LOG.throwing(new RuntimeException(e));
                }
            };
            return LOG.exit(part.getChildren(this).filter(matcher).map(DocumentPart.class::cast));

        } else {
            return LOG.exit(getMatchingChildren(part, partName.parent).flatMap(child->getMatchingChildren(child, RepositoryPath.ROOT.add(partName.part))));
        }
    }

    @Override
    public Stream<DocumentPart> catalogueParts(Reference rfrnc, RepositoryPath partName) throws Exceptions.InvalidReference, Exceptions.InvalidObjectName {
        LOG.entry(rfrnc, partName);
        Document object = baseRepository.getDocument(rfrnc);
        Optional<DocumentPart> rootPart = getRootPart(object);
        if (rootPart.isPresent()) return LOG.exit(getMatchingChildren(rootPart.get(), partName));
        else throw LOG.throwing(new InvalidReference(rfrnc));
        
    }

    @Override
    public NamedRepositoryObject getObjectByName(RepositoryPath objectName, Options.Get... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName {
        LOG.entry(objectName, Options.loggable(options));

        RepositoryPath partName = objectName.getPartPath();
        
        if (partName.isEmpty()) {
            return LOG.exit(mapResult(baseRepository.getObjectByName(objectName, options)));
        } else {
            try {
                DocumentLink object = baseRepository.getDocumentLink(objectName.getDocumentPath(), options);
                return LOG.exit(getChildPart(object, partName));
            } catch (Exceptions.InvalidWorkspace e) {
                throw LOG.throwing(new Exceptions.InvalidObjectName(objectName));
            }           
        }
    }

    @Override
    public Stream<NamedRepositoryObject> catalogueByName(RepositoryPath objectName, Query query, Options.Search... options) throws Exceptions.InvalidWorkspace {
        LOG.entry(objectName, query, Options.loggable(options));
        RepositoryPath partName = objectName.getPartPath();
        
        if (partName.isEmpty()) {
            return LOG.exit(mapResult(baseRepository.catalogueByName(objectName, query, options)));        
        } else {
            Options.Search.Builder newOptions = Options.Search.EMPTY.addOptions(options).addOption(Options.NO_IMPLICIT_WILDCARD);
            return LOG.exit(baseRepository.catalogueByName(objectName.getDocumentPath(), query, newOptions.build())
                .filter(object -> object.getType() == RepositoryObject.Type.DOCUMENT_LINK)
                .map(DocumentLink.class::cast)
                .map(this::getRootPart)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .flatMap(document -> getMatchingChildren(document, partName)));
        }
    }
}
