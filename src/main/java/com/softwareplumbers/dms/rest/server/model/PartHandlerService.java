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

/**
 *
 * @author jonathan
 */
public class PartHandlerService extends RepositoryDecorator {
  
    Map<Reference, DocumentPart> cache = Collections.synchronizedMap(new WeakHashMap<>());
    PartHandler[] handlers;
    
    Optional<PartHandler> getHandler(Document document) {
        return Stream.of(handlers).filter(handler->handler.canHandle(document)).findAny();
    }
    
    Optional<DocumentPart> getRootPart(Document document) {
        DocumentPart result = cache.get(document.getReference());
        if (result == null) {
            Optional<PartHandler> handler = getHandler(document);
            if (handler.isPresent()) {
                result = handler.get().build(document); 
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
            return (DocumentPart)rootPart.get().getChild(this, partName);                           
        } else {
            throw new Exceptions.InvalidObjectName(Constants.NO_ID, partName);                    
        }        
    }
    
    StreamableDocumentPart getStreamableChildPart(Document document, QualifiedName partName) throws Exceptions.InvalidObjectName {
        DocumentPart part = getChildPart(document, partName);
        if (part.getType() != RepositoryObject.Type.STREAMABLE_DOCUMENT_PART) 
            throw new Exceptions.InvalidObjectName(Constants.NO_ID, partName);
        else {
            return (StreamableDocumentPart)part;
        } 
    }
       
    @Override
    public InputStream getData(String rootId, QualifiedName objectName, Options.Get... options) throws Exceptions.InvalidObjectName, IOException {
        
        Optional<QualifiedName> partName = Options.PART.getValue(options);
        
        if (partName.isPresent()) {
            try {
                DocumentLink object = baseRepository.getDocumentLink(rootId, objectName, options);
                return getStreamableChildPart(object, partName.get()).getData(this);
            } catch (Exceptions.InvalidWorkspace e) {
                throw new Exceptions.InvalidObjectName(rootId, objectName);
            }           
        } else {
            return baseRepository.getData(rootId, objectName, options);
        }
    }

    @Override
    public void writeData(String rootId, QualifiedName objectName, OutputStream out, Options.Get... options) throws Exceptions.InvalidObjectName, IOException {
        Optional<QualifiedName> partName = Options.PART.getValue(options);
        
        if (partName.isPresent()) {
            try {
                DocumentLink object = baseRepository.getDocumentLink(rootId, objectName, options);
                getStreamableChildPart(object, partName.get()).writeDocument(this, out);
            } catch (Exceptions.InvalidWorkspace e) {
                throw new Exceptions.InvalidObjectName(rootId, objectName);
            }           
        } else {
            baseRepository.writeData(rootId, objectName, out, options);
        }
    }
    
    @Override
    public DocumentPart getPart(Reference rfrnc, QualifiedName partName) throws Exceptions.InvalidReference, Exceptions.InvalidObjectName {
        Document object = baseRepository.getDocument(rfrnc);
        return getChildPart(object, partName);
    }

    @Override
    public InputStream getData(Reference rfrnc, Optional<QualifiedName> partName) throws Exceptions.InvalidReference, Exceptions.InvalidObjectName, IOException {
        if (partName.isPresent()) {            
            Document object = baseRepository.getDocument(rfrnc);
            return getStreamableChildPart(object, partName.get()).getData(this);
        } else {    
            return baseRepository.getData(rfrnc, partName);
        }
    }

    @Override
    public void writeData(Reference rfrnc, Optional<QualifiedName> partName, OutputStream out) throws Exceptions.InvalidReference, Exceptions.InvalidObjectName, IOException {
        if (partName.isPresent()) {            
            Document object = baseRepository.getDocument(rfrnc);
            getStreamableChildPart(object, partName.get()).writeDocument(this, out);
        } else {    
            baseRepository.writeData(rfrnc, partName, out);
        }
    }
    
    Stream<DocumentPart> getMatchingChildren(DocumentPart part, QualifiedName partName) {
        if (partName.parent.isEmpty()) {
            try {
                Predicate<String> matcher = Parsers.parseUnixWildcard(partName.part).build(Builders.toPattern()).asPredicate();
                return part.getChildren(this).filter(child->matcher.test(child.getName().part)).map(DocumentPart.class::cast);
            } catch (PatternSyntaxException e) {
                throw new RuntimeException(e);
            }
        } else {
            return getMatchingChildren(part, partName.parent).flatMap(child->getMatchingChildren(child, QualifiedName.of(partName.part)));
        }
    }

    @Override
    public Stream<DocumentPart> catalogueParts(Reference rfrnc, QualifiedName partName) throws Exceptions.InvalidReference, Exceptions.InvalidObjectName {
        Document object = baseRepository.getDocument(rfrnc);
        Optional<DocumentPart> rootPart = getRootPart(object);
        if (rootPart.isPresent()) return getMatchingChildren(rootPart.get(), partName);
        else throw new InvalidReference(rfrnc);
        
    }

    @Override
    public NamedRepositoryObject getObjectByName(String rootId, QualifiedName objectName, Options.Get... options) throws Exceptions.InvalidWorkspace, Exceptions.InvalidObjectName {
        Optional<QualifiedName> partName = Options.PART.getValue(options);
        
        if (partName.isPresent()) {
            try {
                DocumentLink object = baseRepository.getDocumentLink(rootId, objectName, options);
                return getChildPart(object, partName.get());
            } catch (Exceptions.InvalidWorkspace e) {
                throw new Exceptions.InvalidObjectName(rootId, objectName);
            }           
        } else {
            return baseRepository.getObjectByName(rootId, objectName, options);
        }
    }

}
