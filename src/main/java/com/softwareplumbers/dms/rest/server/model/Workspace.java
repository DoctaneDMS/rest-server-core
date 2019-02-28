package com.softwareplumbers.dms.rest.server.model;

import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.dms.rest.server.model.RepositoryObject.Type;

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
public interface Workspace extends RepositoryObject {
	
	/** Possible states of a workspace */
	enum State { Open, Closed, Finalized }
	
	/** Get the name of a workspace. 
	 * 
	 * Returns the name of the workspace relative to the root. In the case of an anonymous workspace,
	 * this should return '~/<workspace id>'. This enables the children of an anonyomous workspace to
	 * be consistently referenced via the APIs.
	 */
	QualifiedName getName();
	/** Get the state of a workspace */
	State getState();
	/** Get the id of a workspace */
	String getId();

	/** Default implementation returns Type.WORKSPACE */	
	default Type getType() { return Type.WORKSPACE; }
	
	/** Get the default Json representation for a workspace */
	default JsonObject toJson() {
		
		QualifiedName name = getName();
		State state = getState();
		String id = getId();
		JsonObject metadata =getMetadata();
		
		JsonObjectBuilder builder = Json.createObjectBuilder();	
		if (name != null) builder.add("name", name.join("/"));
		if (state != null) builder.add("state", state.toString());
		if (id != null) builder.add("id", id);
		if (id != null) builder.add("metadata", metadata);
		return builder.build();

	}
}
