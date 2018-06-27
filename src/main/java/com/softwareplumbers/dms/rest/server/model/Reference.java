package com.softwareplumbers.dms.rest.server.model;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

/** Class representing a reference to a specific version of a document in a repository.
 * 
 * @author Jonathan Essex
 */
public class Reference implements Comparable<Reference> {
	
	/** Document identifier */
	public final String id;
	/** Version number */
	public final Integer version;
	/** Construct a reference to a particular version of a document */
	public Reference(String id, Integer version) { this.id = id; this.version = version; }
	/** Construct a reference to the latest version of a document */
	public Reference(String id) { this.id = id; this.version = null; }
	
	/** Get the identifier */
	public String getId() { return id; }
	/** Get the version number */
	public Integer getVersion() { return version; }
	/** Compare two references for equality */
	public boolean equals(Reference other) { return compareTo(other) == 0; }
	
	/** Compare a reference for equality with another object */	
	public boolean equals(Object other) {
		return other instanceof Reference && equals((Reference)other);
	}
	
	/** Create a hash value for this reference */		
	public int hashCode() {
		return id.hashCode() ^ (version == null ? 0 : version.intValue());
	}

	/** Compare two references
	 *
	 * A reference (id, null) is greater than a reference (id, version). 
	 */		
	public int compareTo(Reference other) {
		int result = id.compareTo(other.id);
		if (result != 0) return result;
		if (version == other.version) return 0; 
		if (version == null) return 1;
		if (other.version == null) return -1;
		if (version < other.version) return -1;
		if (version > other.version) return 1;
		return 0;
	}
	
	/** Create a reference from a JSON object.
	 * 
	 * The JSON representation of a reference is { id: _id_, version _version_ }
	 * 
	 * @param object
	 * @return A reference object
	 */
	public static Reference fromJSON(JsonObject object) {
		JsonNumber version = object.getJsonNumber("version");
		return new Reference(
				object.getString("id"), 
				version != null && version != JsonValue.NULL ? (Integer)version.intValue() : null);
	}
	
	public JsonObject toJSON() {
		JsonObjectBuilder builder = Json.createObjectBuilder();
		builder.add("id", id);
		if (version != null) builder.add("version", version);
		return builder.build();
	}
	
	/** Create a string from a reference.
	 * 
	 * The string representation of a reference is id:version
	 * 
	 * @return id:version
	 */
	public String toString() {
		return id + (version == null ? "" : ":" + version);
	}
}