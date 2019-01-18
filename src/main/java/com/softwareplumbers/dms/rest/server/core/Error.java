package com.softwareplumbers.dms.rest.server.core;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import com.fasterxml.jackson.annotation.JsonValue;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidDocumentId;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidReference;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspaceName;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspaceState;

/** Generate error reports in Json */
public class Error {
	
	public static JsonObject repositoryNotFound(String repository) {
		return Json.createObjectBuilder()
				.add("error", "Repository " + repository + " does not exist")
				.add("repository", repository)
				.build();
	}
	
	public static JsonObject mapServiceError(InvalidReference err) {
		return Json.createObjectBuilder()
				.add("error", "Reference " + err.reference + " is invalid")
				.add("reference", err.reference.toJson())
				.build();		
	}
	
	public static JsonObject mapServiceError(InvalidWorkspaceName err) {
		return Json.createObjectBuilder()
				.add("error", "Workspace name " + err.workspace + " is invalid")
				.add("workspaceName", err.workspace)
				.build();		
	}

	public static JsonObject mapServiceError(InvalidWorkspaceState err) {
		return Json.createObjectBuilder()
				.add("error", "Workspace " + err.workspace + " is in invalid state " + err.state)
				.add("workspaceName", err.workspace)
				.add("workspaceState", err.state.toString())
				.build();		
	}
	
	public static JsonObject mapServiceError(InvalidDocumentId err) {
		return Json.createObjectBuilder()
				.add("error", "Document " + err.id + " is not found")
				.add("id", err.id)
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
