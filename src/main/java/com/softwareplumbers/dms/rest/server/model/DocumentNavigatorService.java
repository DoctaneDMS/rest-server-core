/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.common.QualifiedName;
import java.util.stream.Stream;

/** Document Navigation Service.
 * 
 * API used to navigate within a retrieved document (for example, to list the files within a zipfile.
 *
 * @author Jonathan Essex
 */
public interface DocumentNavigatorService { 
    
    public static class DocumentFormatException extends Exception {
        public DocumentFormatException(String msg, Exception cause) {
            super(msg, cause);
        }
    }
    
    public static class PartNotFoundException extends Exception {
        public PartNotFoundException(Document document, QualifiedName part) {
            super(String.format("Could not find part %s in document %s", part, document.getId()));
        }
    }
    
    DocumentPart getPartByName(Document document, QualifiedName partName) throws DocumentFormatException, PartNotFoundException;
    Stream<DocumentPart> catalogParts(Document document, QualifiedName partName) throws DocumentFormatException;
    boolean canNavigate(Document document);
}
