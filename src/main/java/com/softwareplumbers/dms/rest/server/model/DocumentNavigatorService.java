/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.common.QualifiedName;
import java.util.Optional;
import java.util.stream.Stream;

/** Document Navigation Service.
 * 
 * API used to navigate within a retrieved document (for example, to list the files within a zipfile.
 *
 * @author Jonathan Essex
 */
public interface DocumentNavigatorService { 
    
    public static class DocumentFormatException extends RuntimeException {
        public DocumentFormatException(String msg, Exception cause) {
            super(msg, cause);
        }
    }
    
    public static class PartNotFoundException extends Exception {
        public final StreamableRepositoryObject document;
        public final QualifiedName part;
        public PartNotFoundException(StreamableRepositoryObject document, QualifiedName part) {
            super(String.format("Could not find part %s in document %s", part, document));
            this.document = document;
            this.part = part;
        }
    }
    
    default DocumentPart getPartByName(StreamableRepositoryObject document, QualifiedName partName) throws DocumentFormatException, PartNotFoundException {
        return getOptionalPartByName(document,partName).orElseThrow(()->new PartNotFoundException(document, partName));
    }
    
    Optional<DocumentPart> getOptionalPartByName(StreamableRepositoryObject document, QualifiedName partName) throws DocumentFormatException;
    Stream<DocumentPart> catalogParts(StreamableRepositoryObject document, QualifiedName partName) throws DocumentFormatException;
    boolean canNavigate(StreamableRepositoryObject document);
}
