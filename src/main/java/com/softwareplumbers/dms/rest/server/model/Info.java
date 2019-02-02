package com.softwareplumbers.dms.rest.server.model;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

/** Information about a Document returned by search APIs.
 * 
 * @author SWPNET\jonessex
 *
 */
public class Info {
	
	/** A reference to a document */
	public final Reference reference;
	/** Metadata about the referenced document */
	public final JsonObject metadata;
	/** Mime type of the reference document */
	public final String mimeType;
	
	/** Create an Info object*/
	public Info(Reference reference, String mimeType, JsonObject metadata) {
		this.reference = reference;
		this.mimeType = mimeType;
		this.metadata = metadata;
	}
	
	/** Create an Info object from a document */
	public Info(Reference reference, Document document) {
		this.reference = reference;
		this.mimeType = document.getMediaType().toString();
		this.metadata = document.getMetadata();
	}
	
	/** Get a reference to a document */
	public Reference getReference() { return reference; }
	/** Get the mime type of a document */
	public String getMimeType() { return mimeType; }
	/** Get other metadata about a document */
	public JsonObject getMetadata() { return metadata; }
	
	/** Create an Info object from Json */
	public static Info fromJson(JsonObject obj) {
		return new Info(
				Reference.fromJson(obj.getJsonObject("reference")),
				obj.getString("mimeType"),
				obj.getJsonObject("metadata")
			);
	}
	
	/** Output an Info object in Json format */
	public JsonObject toJson() {
		JsonObjectBuilder builder = Json.createObjectBuilder();
		return builder
			.add("reference", reference.toJson())
			.add("mimeType", mimeType)
			.add("metadata", metadata)
			.build();
	}
}
