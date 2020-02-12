package com.softwareplumbers.dms.rest.server.core;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractquery.Query;
import com.softwareplumbers.dms.rest.server.core.XMLOutput.CannotConvertFormatException;
import com.softwareplumbers.dms.Document;
import com.softwareplumbers.dms.DocumentNavigatorService.DocumentFormatException;
import com.softwareplumbers.dms.DocumentNavigatorService.PartNotFoundException;
import com.softwareplumbers.dms.NamedRepositoryObject;
import com.softwareplumbers.dms.Exceptions.InvalidDocumentId;
import com.softwareplumbers.dms.Exceptions.InvalidObjectName;
import com.softwareplumbers.dms.Exceptions.InvalidReference;
import com.softwareplumbers.dms.Exceptions.InvalidWorkspace;
import com.softwareplumbers.dms.Exceptions.InvalidWorkspaceState;
import org.slf4j.ext.XLogger;
import javax.json.JsonValue;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.slf4j.ext.XLoggerFactory;

/** Generate error reports in Json */
public class Error {
    
    public static final XLogger LOG = XLoggerFactory.getXLogger(Error.class);
	
	public static JsonObject repositoryNotFound(String repository) {
		return Json.createObjectBuilder()
				.add("error", "Repository " + repository + " does not exist")
				.add("repository", repository)
				.build();
	}
	
    public static JsonObject badOperation(String cause) {
		return Json.createObjectBuilder()
				.add("error", "Bad Operation")
				.add("cause", cause)
				.build();
	}
    
    public static JsonObject mapServiceError(DocumentFormatException e) {
		return Json.createObjectBuilder()
				.add("error", "Document cannot be parsed in the expected manner")
				.add("cause", e.getMessage())
				.build();		        
    }
        
	public static JsonObject mapServiceError(InvalidReference err) {
		return Json.createObjectBuilder()
                .add("code","INVALID_REFERENCE")
				.add("error", "Reference " + err.reference + " is invalid")
				.add("reference", err.reference.toJson())
				.build();		
	}
	
	public static JsonObject mapServiceError(InvalidWorkspace err) {
		return Json.createObjectBuilder()
                .add("code","INVALID_WORKSPACE")
				.add("error", "Workspace name " + err.workspace + " is invalid")
				.add("workspaceId", err.rootId == null ? JsonValue.NULL : Json.createValue(err.rootId))
				.add("workspaceName", err.workspace.toString())
				.build();		
	}
    
    public static JsonObject mapServiceError(InvalidContentType err) {
		return Json.createObjectBuilder()
				.add("error", "Content type is invalid")
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
                .add("code","INVALID_WORKSPACE_STATE")
				.add("error", "Workspace " + err.workspace + " is in invalid state " + err.state)
				.add("workspaceName", err.workspace);
		
		if (err.state != null) builder = builder.add("state", err.state.toString());
				
		return builder.build();		
	}
	
	public static JsonObject mapServiceError(InvalidObjectName err) {
		return Json.createObjectBuilder()
                .add("code","INVALID_OBJECT_NAME")
				.add("error", "Object name " + err.name + " is not valid")

				.add("name", err.name.toString())
				.build();		
	}
	
	public static JsonObject mapServiceError(PartNotFoundException err) {
		return Json.createObjectBuilder()
				.add("error", "Object part " + err.part + " is not valid")
				.add("name", err.part.toString())
				.build();		
	}

    public static JsonObject mapServiceError(InvalidRepository err) {
		return Json.createObjectBuilder()
				.add("error", "Invalid repository " + err.repository)
				.add("name", err.repository)
				.build();		
	}
    
	public static JsonObject mapServiceError(InvalidDocumentId err) {
		return Json.createObjectBuilder()
                .add("code","INVALID_DOCUMENT_ID")
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

    public static JsonObject objectNotFound(String repository, WorkspacePath name) {
		JsonObjectBuilder builder = Json.createObjectBuilder()
				.add("error", "Object " + name + " does not exist in repository " + repository)
				.add("name", name.toString())
				.add("repository", repository);
				
		return builder.build();
	}

	public static JsonObject reportException(Throwable e) {
	    LOG.catching(e);
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
    
    public static JsonObject unauthorized(Query acl, NamedRepositoryObject obj) {
        return unauthorized(acl, obj.getName());
    }

    public static JsonObject unauthorized(Query acl, Document doc) {
        return unauthorized(acl, doc.getId());
    }
    
    public static JsonObject unauthorized(Query acl, String id) {
        return Json.createObjectBuilder()
            .add("error", "No rights to access document with id " + id)
            .add("acl", acl.toJSON())
            .build();        
    }
    
    public static JsonObject unauthorized(Query acl, MediaType type, JsonObject metadata) {
        return unauthorized(acl, type.toString(), metadata);      
    }
    
    public static JsonObject unauthorized(Query acl, String type, JsonObject metadata) {
        return Json.createObjectBuilder()
            .add("error", "No rights to create document")
            .add("acl", acl.toJSON())
            .add("mediaType", type)
            .add("metadata", metadata)
            .build();        
    }

    public static JsonObject unauthorized(Query acl, QualifiedName obj, String documentId) {
        return unauthorized(acl, obj.add("~" + documentId));
    }
    
    public static JsonObject unauthorized(Query acl, QualifiedName obj) {
        return Json.createObjectBuilder()
            .add("error", "No rights to access object " + obj)
            .add("acl", acl.toJSON())
            .build();
    }

    public static Response errorResponse(Status status, JsonObject error) {
        return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE).entity(error).build();
    }
}
