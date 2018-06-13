package com.softwareplumbers.dms.rest.server.model;

import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonValue;

/** Class representing a reference to a specific version of a document in a repository.
 * 
 * @author Jonathan Essex
 *
 */
public class Reference implements Comparable<Reference> {
	public final String id;
	public final Integer version;
	public Reference(String id, Integer version) { this.id = id; this.version = version; }
	public Reference(String id) { this.id = id; this.version = null; }
	
	public String getId() { return id; }
	public Integer getVersion() { return version; }
	
	public boolean equals(Reference other) { 
		return compareTo(other) == 0;
	}
	
	public boolean equals(Object other) {
		return other instanceof Reference && equals((Reference)other);
	}
	
	public int hashCode() {
		return id.hashCode() ^ (version == null ? 0 : version.intValue());
	}

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
	
	public static Reference fromJSON(JsonObject object) {
		JsonNumber version = object.getJsonNumber("version");
		return new Reference(
				object.getString("id"), 
				version != null && version != JsonValue.NULL ? (Integer)version.intValue() : null);
	}
	
	public String toString() {
		return id + (version == null ? "" : "." + version);
	}
}