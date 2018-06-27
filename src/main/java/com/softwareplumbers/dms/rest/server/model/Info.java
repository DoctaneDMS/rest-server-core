package com.softwareplumbers.dms.rest.server.model;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

public class Info {
	public final Reference reference;
	public final JsonObject metadata;
	public final String mimeType;
	
	public Info(Reference reference, String mimeType, JsonObject metadata) {
		this.reference = reference;
		this.mimeType = mimeType;
		this.metadata = metadata;
	}
	
	public Info(Reference reference, Document document) {
		this.reference = reference;
		this.mimeType = document.getMediaType().toString();
		this.metadata = document.getMetadata();
	}
	
	public Reference getReference() { return reference; }
	public String getMimeType() { return mimeType; }
	public JsonObject getMetadata() { return metadata; }
	
	public static Info fromJson(JsonObject obj) {
		return new Info(
				Reference.fromJson(obj.getJsonObject("reference")),
				obj.getString("mimeType"),
				obj.getJsonObject("metadata")
			);
	}
	
	public JsonObject toJson() {
		JsonObjectBuilder builder = Json.createObjectBuilder();
		return builder
			.add("reference", reference.toJson())
			.add("mimeType", mimeType)
			.add("metadata", metadata)
			.build();
	}
}
