/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms;
import com.softwareplumbers.dms.Exceptions.*;
import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;

/**  Simple Document API
 *
 * @author SWPNET\jonessex
 */
public interface DocumentService {
    
	/** Get a document from a Reference.
 	 * 
	 * @param reference
	 * @return the requested document
	 * @throws InvalidReference if there is no document matching the reference in the repository 
	 */
	public Document getDocument(Reference reference) throws Exceptions.InvalidReference;
    
	/** Create a new document in the repository
	 * 
	 * @param mediaType the type of document
	 * @param stream a supplier function that produces a stream of binary data representing the document
	 * @param metadata a Json object describing the document
	 * @return A Reference object that can later be used to retrieve the document
	 */
	public Reference createDocument(MediaType mediaType, 
			InputStreamSupplier stream, 
			JsonObject metadata);
    
	/** Update a document in the repository.
	 * <p>
	 * Update a document in the repository. Uses an id rather than a Reference because a Reference
	 * also encompasses the version of the document. This function creates a new version of the document
	 * rather than actually updating the old document.
	 * </p><p>
	 * If any parameter is null, this implies the value will be unchanged by the update operation. Thus,
	 * it is possible to update a document's meta-data without actually updating the document itself
	 * by simply setting the stream parameter to null.
	 * </p><p>
	 * Setting the workspace in this function does not remove the document from any workspace it is already
	 * in; it merely adds it to the specified workspace.
	 * </p><p>
	 * Meta-data passed in to the update operation will be merged with the existing metadata; for example,
	 * if a property is missing in the supplied meta-data object, that property will remain unchanged. To
	 * actually remove meta-data it is necessary to explicitly pass in a JsonObject.NULL value.
	 * </p>
	 * @param id A string id for the document.
	 * @param mediaType (optional) new media type for document
	 * @param stream (optional) a supplier function that produces a stream of binary data representing the document 
	 * @param metadata (optional) a json object describing the document
	 * @return A reference to the new version of the document.
	 * @throws InvalidDocumentId if no document exists with the given id
	 */
	public Reference updateDocument(String id, 
		MediaType mediaType, 
		InputStreamSupplier stream, 
		JsonObject metadata) throws InvalidDocumentId;    
}
