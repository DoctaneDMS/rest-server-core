package com.softwareplumbers.dms.rest.server.core;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;

/** Generate error reports in Json */
public class Error {
	
	public static JsonObject repositoryNotFound(String repository) {
		return Json.createObjectBuilder()
				.add("error", "Repository " + repository + " does not exist")
				.add("repository", repository)
				.build();
	}
	
	public static JsonObject documentNotFound(String repository, String id, Integer version) {
		return Json.createObjectBuilder()
				.add("error", "Document " + id + " with version " + (version == null ? "none" : version.toString()) + " does not exist in repository " + repository)
				.add("id", id)
				.add("version", version == null ? JsonObject.NULL : Json.createValue(version))
				.add("repository", repository)
				.build();
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
