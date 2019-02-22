package com.softwareplumbers.dms.rest.server.model;

import javax.json.JsonObject;

public interface RepositoryObject {
	
	enum Type { WORKSPACE, DOCUMENT };

	/** Get workspace metadata */
	JsonObject getMetadata();
	Type getType();
}
