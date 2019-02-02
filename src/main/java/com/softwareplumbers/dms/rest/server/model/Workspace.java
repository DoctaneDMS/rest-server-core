package com.softwareplumbers.dms.rest.server.model;

import javax.json.JsonObject;
import javax.json.Json;

/** Representation of a Workspace in a repository 
 *
 * A workspace may be Open, Closed, or Finalized. This means:
 * 
 * | State		| Effect |
 * |------------|--------|
 * | Open 		| Documents may be freely added and removed; workspace reflects most recent version |
 * | Closed 	| Any attempt to add or remove document will cause an error; workspace shows versions current when closed |
 * | Finalized 	| Documents may be added or removed; workspace shows versions current when closed |
 */
public interface Workspace {
	
	/** Possible states of a workspace */
	enum State { Open, Closed, Finalized }
	
	/** Get the name of a workspace */
	String getName();
	/** Get the state of a workspace */
	State getState();
	
	/** Get the default Json representation for a workspace */
	default JsonObject toJson() {
		return Json.createObjectBuilder()
			.add("name", getName())
			.add("state", getState().toString())
			.build();

	}
}
