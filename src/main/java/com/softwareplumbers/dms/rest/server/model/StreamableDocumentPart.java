/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.model;

/**
 *
 * @author jonathan.local
 */
public interface StreamableDocumentPart extends DocumentPart, StreamableRepositoryObject {

   /** Get type of object
    * 
    * @return DOCUMENT_PART 
    */
    @Override
    default Type getType() { return Type.STREAMABLE_DOCUMENT_PART; }
}
