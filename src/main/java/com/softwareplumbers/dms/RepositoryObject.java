package com.softwareplumbers.dms;

import javax.json.JsonObject;

/* Base inteface for all repository objects
 * 
 */
public interface RepositoryObject {
	
	enum Type { WORKSPACE, DOCUMENT, DOCUMENT_LINK, DOCUMENT_PART, STREAMABLE_DOCUMENT_PART };
    
    /** Get the type from a standard json representation of a RepositoryObject.
     * 
     * @param object a JSON representation of a document (as might be returned by this.toJson)
     * @return the value of the object's type attribute converted into a Type 
     */
    public static Type getType(JsonObject object) {
        // For historical reasons, an empty type attribute implies the object is a workspace.
        return Type.valueOf(object.getString("type", Type.WORKSPACE.name()));
    }
    
    /** Get the metadata from a standard json representation of a RepositoryObject.
     * 
     * @param object a JSON representation of a document (as might be returned by this.toJson)
     * @return the value of the object's metadata attribute 
     */
    public static JsonObject getMetadata(JsonObject object) {
        return object.getJsonObject("metadata");
    }
    
	/** Get metadata
     * @return metadata associated with this repository object */
	JsonObject getMetadata();
	
	/** Get type of object 
     * @return type of object 
     */
	Type getType();
    
	/** Get Json representation of object
     * 
     * The repository parameter is optional if no parent or child information is requested.
     * 
     * @param repository repository from which to retrieve parent/child information 
     * @param navigator service for parsing documents into document parts
     * @param parentLevels levels of parent information to retrieve
     * @param childLevels levels of child information to retrieve
     * @return JSON representation of this object, with the requested levels of context and detail
     */
	JsonObject toJson(RepositoryService repository, DocumentNavigatorService navigator, int parentLevels, int childLevels);
    
    /** Get default JSON representation
     * 
     * Equivalent to toJson(null,null,0,0);
     * 
     * @return the default JSON representation for this object.
     */
    default JsonObject toJson() {
        return toJson(null,null,0,0);
    }
    
}
