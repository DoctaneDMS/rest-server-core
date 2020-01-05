package com.softwareplumbers.dms;

import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractquery.Query;
import com.softwareplumbers.dms.RepositoryObject.Type;
import java.util.Optional;

import javax.json.Json;
import javax.json.JsonArrayBuilder;

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
public interface Workspace extends NamedRepositoryObject {
	
	/** Possible states of a workspace */
	enum State { Open, Closed, Finalized }
	
    public static State getState(JsonObject object) {
        return object.containsKey("state") ? State.valueOf(object.getString("state")) : null;
    }
    
	/** Get id of object */
	String getId();

	/** Get the state of a workspace */
	State getState();

	/** Default implementation returns Type.WORKSPACE */	
	default Type getType() { return Type.WORKSPACE; }
	
	/** Get the default Json representation for a workspace */
	default JsonObject toJson(RepositoryService repository, DocumentNavigatorService navigator, int parentLevels, int childLevels) {
		
		QualifiedName name = getName();
		State state = getState();
		String id = getId();
		JsonObject metadata =getMetadata();
		Type type = getType();

	    JsonObjectBuilder builder = Json.createObjectBuilder(); 
        
        if (parentLevels > 0) {
            Optional<NamedRepositoryObject> parent = getParent(repository);
            if (parent.isPresent()) builder.add("parent", parent.get().toJson(repository, navigator, parentLevels-1, 0));
        }
        
        if (childLevels > 0) {
            try {
                JsonArrayBuilder childrenBuilder = Json.createArrayBuilder();
                repository.catalogueByName(Constants.ROOT_ID, name, Query.UNBOUNDED, false)
                    .forEach(part -> childrenBuilder.add(part.toJson(repository, navigator, 0, childLevels-1)));
                builder.add("parts", childrenBuilder);
            } catch (RepositoryService.InvalidWorkspace err) {
                throw new RuntimeException(err);
            }
        }

	    // Base fields  
	    if (id != null) builder.add("id", id);
	    if (metadata != null) builder.add("metadata", metadata);
	    if (type != null) builder.add("type", type.toString());
	    // Named Repository Item fields
		if (name != null) builder.add("name", name.join("/"));
		// Workspace fields
		if (state != null) builder.add("state", state.toString());
		return builder.build();

	}
}
