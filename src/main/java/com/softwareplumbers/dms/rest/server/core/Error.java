package com.softwareplumbers.dms.rest.server.core;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import com.fasterxml.jackson.annotation.JsonValue;

/** Generate error reports in Json */
public class Error {
	
	public static JsonObject repositoryNotFound(String repository) {
		return Json.createObjectBuilder()
				.add("error", "Repository " + repository + " does not exist")
				.add("repository", repository)
				.build();
	}
	
	public static JsonObject documentNotFound(String repository, String id, Integer version) {
		JsonObjectBuilder builder = Json.createObjectBuilder()
				.add("error", "Document " + id + " with version " + (version == null ? "none" : version.toString()) + " does not exist in repository " + repository)
				.add("id", id)
				.add("repository", repository);
		
		builder = (version == null) 
			? builder.add("version", JsonObject.NULL)
			: builder.add("version", version);
		
		return builder.build();
	}

	public static JsonObject reportException(Throwable e) {
		return Json.createObjectBuilder().add("error", "Exception " + e.toString()).build();		
	}
	
	public static JsonObject missingFile() {
		return Json.createObjectBuilder().add("error", "Document file missing from request").build();				
	}

	public static JsonObject missingMetadata() {
		return Json.createObjectBuilder().add("error", "Document metadata missing from request").build();				
	}

	public static JsonObject unexpectedFailure() {
		return Json.createObjectBuilder().add("error", "Request failed for unknown reason").build();				
	}
}
