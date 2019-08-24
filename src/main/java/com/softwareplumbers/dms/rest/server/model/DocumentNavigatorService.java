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
 * API used to navigate within a retrieved document (for example, to list the files within a zipfile.)
 *
 * @author Jonathan Essex
 */
public interface DocumentNavigatorService { 
    
    /** Exception thrown when a document is not of the expected format and cannot be navigated at all */
    public static class DocumentFormatException extends RuntimeException {
        public DocumentFormatException(String msg, Exception cause) {
            super(msg, cause);
        }
    }
    
    /** Exception thrown when a requested part cannot be found in a document */
    public static class PartNotFoundException extends Exception {
        public final StreamableRepositoryObject document;
        public final QualifiedName part;
        public PartNotFoundException(StreamableRepositoryObject document, QualifiedName part) {
            super(String.format("Could not find part %s in document %s", part, document));
            this.document = document;
            this.part = part;
        }
    }
    
    /** Get a named part from a document.
     * 
     * This default operation calls getOptionalPartByName and throws PartNotFoundException if the result is empty.
     * 
     * @param document Document in which to find a document part
     * @param partName Name of part to find
     * @return the named document part
     * @throws DocumentFormatException If document cannot be navigated to find the part
     * @throws PartNotFoundException  If the named part is not found in the document.
     */
    default DocumentPart getPartByName(StreamableRepositoryObject document, QualifiedName partName) throws DocumentFormatException, PartNotFoundException {
        return getOptionalPartByName(document,partName).orElseThrow(()->new PartNotFoundException(document, partName));
    }
    
    /** Get a named part from a document
     * 
     * @param document Document in which to find a document part
     * @param partName Name of part to find
     * @return the named document part, or Optional.empty()
     * @throws DocumentFormatException If document cannot be navigated to find the part
     */
    Optional<DocumentPart> getOptionalPartByName(StreamableRepositoryObject document, QualifiedName partName) throws DocumentFormatException;
    
    /** Catalog the parts of a document.
     * 
     * In the simple case, this method lists all the sub-parts of a given document part. Thus `catalogParts(doc, QualifiedName.ROOT)`
     * will return all the top-level parts of the given document.
     * 
     * The part name provided may also contain the wildcard characters * and ?. In this
     * case the operation returns all parts which have a name fully matching the supplied partName. In essence, the
     * path 'abc/def' (with no wildcards) returns the exact same result as 'abc/def/*' - in both cases a listing of 
     * all the sub-parts of the part 'abc/def'.
     * 
     * @param document Document in which to list document parts
     * @param partName Part name in which to list sub-parts *or* wildcard pattern to filter returned parts 
     * @return A stream of matching document parts
     * @throws com.softwareplumbers.dms.rest.server.model.DocumentNavigatorService.DocumentFormatException 
     */
    Stream<DocumentPart> catalogParts(StreamableRepositoryObject document, QualifiedName partName) throws DocumentFormatException;
    
    /** Test to see whether the given document can be navigated by this document navigator
     * 
     * @param document 
     * @return true if this navigator can be used to access the sub-parts of the given document.
     */
    boolean canNavigate(StreamableRepositoryObject document);
}
