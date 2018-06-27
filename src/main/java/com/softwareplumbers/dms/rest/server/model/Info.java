package com.softwareplumbers.dms.rest.server.model;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

public class Info {
	public final Reference reference;
	public final JsonObject metadata;
	public final String mime_type;
	
	public Info(Reference reference, String mime_type, JsonObject metadata) {
		this.reference = reference;
		this.mime_type = mime_type;
		this.metadata = metadata;
	}
	
	public Info(Reference reference, Document document) {
		this.reference = reference;
		this.mime_type = document.getMediaType().toString();
		this.metadata = document.getMetadata();
	}
	
	public static Info fromJson(JsonObject obj) {
		return new Info(
				Reference.fromJSON(obj.getJsonObject("reference")),
				obj.getString("mimeType"),
				obj.getJsonObject("metadata")
			);
	}
	
	public JsonObject toJson() {
		JsonObjectBuilder builder = Json.createObjectBuilder();
		return builder
			.add("reference", reference.toJSON())
			.add("mimeType", metadata)
			.add("metadata", metadata)
			.build();
	}
}
