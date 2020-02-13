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
        DocumentPart result = cache.get(document.getReference());
        if (result == null) {
            Optional<PartHandler> handler = getHandler(document);
            if (handler.isPresent()) {
                result = handler.get().build(this, document); 
                cache.put(document.getReference(), result);
                return Optional.of(result);
            } else {
                return Optional.empty();
            }
        } else {
            return Optional.of(result);
        }
    }
    
    DocumentPart getChildPart(Document document, QualifiedName partName) throws Exceptions.InvalidObjectName {
        Optional<DocumentPart> rootPart = getRootPart(document);
        if (rootPart.isPresent()) {
            return LOG.exit((DocumentPart)rootPart.get().getChild(this, partName));                           
        } else {
            throw new Exceptions.InvalidObjectName(Constants.NO_ID, partName);                    
        }        
    }
    
    StreamableDocumentPart getStreamableChildPart(Document document, QualifiedName partName) throws Exceptions.InvalidObjectName {
        DocumentPart part = getChildPart(document, partName);
        if (part.getType() != RepositoryObject.Type.STREAMABLE_DOCUMENT_PART) 
            throw new Exceptions.InvalidObjectName(Constants.NO_ID, partName);
        else {
            return LOG.exit((StreamableDocumentPart)part);
        } 
    }
       
    @Override
    public InputStream getData(String rootId, QualifiedName objectName, Options.Get... options) throws Exceptions.InvalidObjectName, IOException {
        LOG.entry(rootId, objectName, Options.loggable(options));
        Optional<QualifiedName> partName = Options.PART.getValue(options);
        
        if (partName.isPresent()) {
            try {
                DocumentLink object = baseRepository.getDocumentLink(rootId, objectName, options);
                return LOG.exit(getStreamableChildPart(object, partName.get()).getData(this));
            } catch (Exceptions.InvalidWorkspace e) {
                throw LOG.throwing(new Exceptions.InvalidObjectName(rootId, objectName));
            }           
        } else {
            return LOG.exit(baseRepository.getData(rootId, objectName, options));
        }
    }

    @Override
    public void writeData(String rootId, QualifiedName objectName, OutputStream out, Options.Get... options) throws Exceptions.InvalidObjectName, IOException {
        LOG.entry(rootId, objectName, "<out>", Options.loggable(options));
        Optional<QualifiedName> partName = Options.PART.getValue(options);
        
        if (partName.isPresent()) {
            try {
                DocumentLink object = baseRepository.getDocumentLink(rootId, objectName, options);
                getStreamableChildPart(object, partName.get()).writeDocument(this, out);
            } catch (Exceptions.InvalidWorkspace e) {
                throw LOG.throwing(new Exceptions.InvalidObjectName(rootId, objectName));
            }           
        } else {
            baseRepository.writeData(rootId, objectName, out, options);
        }
        LOG.exit();
    }
    
    @Override
    public DocumentPart getPart(Reference rfrnc, QualifiedName partName) throws Exceptions.InvalidReference, Exceptions.InvalidObjectName {
        LOG.entry(rfrnc, partName);
        Document object = baseRepository.getDocument(rfrnc);
        return LOG.exit(getChildPart(object, partName));
    }

    @Override
    public InputStream getData(Reference rfrnc, Optional<QualifiedName> partName) throws Exceptions.InvalidReference, Exceptions.InvalidObjectName, IOException {
        LOG.entry(rfrnc, partName);
        if (partName.isPresent()) {            
            Document object = baseRepository.getDocument(rfrnc);
            return LOG.exit(getStreamableChildPart(object, partName.get()).getData(this));
        } else {    
            return LOG.exit(baseRepository.getData(rfrnc, partName));
        }
    }

    @Override
    public void writeData(Reference rfrnc, Optional<QualifiedName> partName, OutputStream out) throws Exceptions.InvalidReference, Exceptions.InvalidObjectName, IOException {
        LOG.entry(rfrnc, partName, "<out>");
        if (partName.isPresent()) {            
            Document object = baseRepository.getDocument(rfrnc);
            getStreamableChildPart(object, partName.get()).writeDocument(this, out);
        } else {    
            baseRepository.writeData(rfrnc, partName, out);
        }
        LOG.exit();
    }
    
    private Stream<DocumentPart> getMatchingChildren(DocumentPart part, QualifiedName partName) {
        if (partName.parent.isEmpty()) {
            try {
                Predicate<String> matcher = Parsers.parseUnixWildcard(partName.part).build(Builders.toPattern()).asPredicate();
                return part.getChildren(this).filter(child->matcher.test(child.getName().part)).map(DocumentPart.class::cast);
            } catch (PatternSyntaxException e) {
                throw LOG.throwing(new RuntimeException(e));
            }
        } else {
            return getMatchingChildren(part, partName.parent).flatMap(child->getMatchingChildren(child, QualifiedName.of(partName.part)));
        }
    }

    @Override
    public Stream<DocumentPart> catalogueParts(Reference rfrnc, QualifiedName partName) throws Exceptions.InvalidReference, Exceptions.InvalidObjectName {
        LOG.entry(rfrnc, partName);
        Document object = baseRepository.getDocument(rfrnc);
        Optional<DocumentPart> rootPart = getRootPart(object);
        if (rootPart.isPresent()) return LOG.exit(getMatchingChildren(rootPart.get(), partName));
        else throw LOG.throwing(new InvalidReference(rfrnc));
        
    }

    @Override
    public NamedRepositoryObject getObjectByName(String rootId, QualifiedName objectName, Options.Get... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName {
        LOG.entry(rootId, objectName, Options.loggable(options));

        Optional<QualifiedName> partName = Options.PART.getValue(options);
        
        if (partName.isPresent()) {
            try {
                DocumentLink object = baseRepository.getDocumentLink(rootId, objectName, options);
                return LOG.exit(getChildPart(object, partName.get()));
            } catch (Exceptions.InvalidWorkspace e) {
                throw LOG.throwing(new Exceptions.InvalidObjectName(rootId, objectName));
            }           
        } else {
            return LOG.exit(baseRepository.getObjectByName(rootId, objectName, options));
        }
    }

    @Override
    public Stream<NamedRepositoryObject> catalogueByName(String rootId, QualifiedName objectName, Query query, Options.Search... options) throws Exceptions.InvalidWorkspace {
        LOG.entry(rootId, objectName, query, Options.loggable(options));
        Optional<QualifiedName> partName = Options.PART.getValue(options);
        
        if (partName.isPresent()) {
            return baseRepository.catalogueByName(rootId, objectName, query, options)
                .filter(object -> object.getType() == RepositoryObject.Type.DOCUMENT_LINK)
                .map(DocumentLink.class::cast)
                .map(this::getRootPart)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .flatMap(document -> getMatchingChildren(document, partName.get()));
        } else {
            return LOG.exit(baseRepository.catalogueByName(rootId, objectName, query, options));        
        }
    }
}
