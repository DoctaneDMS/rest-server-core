package com.softwareplumbers.dms.rest.server.core;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import com.fasterxml.jackson.annotation.JsonValue;
import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.dms.rest.server.core.XMLOutput.CannotConvertFormatException;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidDocumentId;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidObjectName;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidReference;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspace;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspaceState;
import com.softwareplumbers.dms.rest.server.util.Log;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

/** Generate error reports in Json */
public class Error {
    
    public static final Log LOG = new Log(Error.class);
	
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
	
	public static JsonObject mapServiceError(InvalidWorkspace err) {
		return Json.createObjectBuilder()
				.add("error", "Workspace name " + err.workspace + " is invalid")
				.add("workspaceName", err.workspace)
				.build();		
	}
    
    public static JsonObject mapServiceError(CannotConvertFormatException err) {
        return Json.createObjectBuilder()
				.add("error", "Cannot convert " + err.mediaType + " to XML")
				.add("mediaType", err.mediaType.toString())
				.build();
    }

	public static JsonObject mapServiceError(InvalidWorkspaceState err) {
		JsonObjectBuilder builder = Json.createObjectBuilder()
				.add("error", "Workspace " + err.workspace + " is in invalid state " + err.state)
				.add("workspaceName", err.workspace);
		
		if (err.state != null) builder = builder.add("state", err.state.toString());
				
		return builder.build();		
	}
	
	public static JsonObject mapServiceError(InvalidObjectName err) {
		return Json.createObjectBuilder()
				.add("error", "Object name " + err.name + " is not valid")
				.add("name", err.name.toString())
				.build();		
	}
	
	
	public static JsonObject mapServiceError(InvalidDocumentId err) {
		return Json.createObjectBuilder()
				.add("error", "Document " + err.id + " is not found")
				.add("id", err.id)
				.build();		
	}

	public static JsonObject documentNotFound(String repository, String id, String version) {
		JsonObjectBuilder builder = Json.createObjectBuilder()
				.add("error", "Document " + id + " with version " + (version == null ? "none" : version.toString()) + " does not exist in repository " + repository)
				.add("id", id)
				.add("repository", repository);
		
		builder = (version == null) 
			? builder.add("version", JsonObject.NULL)
			: builder.add("version", version);
		
		return builder.build();
	}

	public static JsonObject objectNotFound(String repository, QualifiedName name) {
		JsonObjectBuilder builder = Json.createObjectBuilder()
				.add("error", "Object " + name + " does not exist in repository " + repository)
				.add("name", name.toString())
				.add("repository", repository);
				
		return builder.build();
	}

	public static JsonObject reportException(Throwable e) {
	    LOG.logError("reportException", e);
		return Json.createObjectBuilder().add("error", "Exception " + e.toString()).build();		
	}
	
	public static JsonObject missingResourcePath() {
		return Json.createObjectBuilder().add("error", "resource path missing from request").build();						
	}
	
	public static JsonObject missingFile() {
		return Json.createObjectBuilder().add("error", "Document file missing from request").build();				
	}

	public static JsonObject missingMetadata() {
		return Json.createObjectBuilder().add("error", "Document metadata missing from request").build();				
	}

	public static JsonObject missingContentType() {
		return Json.createObjectBuilder().add("error", "Content type is mandatory when creating a document").build();				
	}

	public static JsonObject unexpectedFailure() {
		return Json.createObjectBuilder().add("error", "Request failed for unknown reason").build();				
	}
	
	public static JsonObject bothVersionAndWorkspacePresent() {
		return Json.createObjectBuilder().add("error", "Cannot specify both version and workspace when getting a document").build();				
	}
    
    public static Response errorResponse(Status status, JsonObject error) {
        return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE).entity(error).build();
    }
}
