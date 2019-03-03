package com.softwareplumbers.dms.rest.server.model;

import javax.json.JsonObject;

/* Base inteface for all repository objects
 * 
 */
public interface RepositoryObject {
	
	enum Type { WORKSPACE, DOCUMENT, DOCUMENT_LINK };

	/** Get metadata */
	JsonObject getMetadata();
	
	/** Get type of object */
	Type getType();
	/** Get id of object */
	String getId();
	/** Get Json representation of object */
	JsonObject toJson();
}
