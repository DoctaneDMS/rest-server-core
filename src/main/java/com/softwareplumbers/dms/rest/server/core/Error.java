package com.softwareplumbers.dms.rest.server.core;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import com.softwareplumbers.common.abstractquery.Query;
import com.softwareplumbers.dms.rest.server.core.XMLOutput.CannotConvertFormatException;
import com.softwareplumbers.dms.Document;
import com.softwareplumbers.dms.Exceptions.BaseException;
import com.softwareplumbers.dms.NamedRepositoryObject;
import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.RepositoryPath;
import org.slf4j.ext.XLogger;

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

	public static JsonObject mapServiceError(BaseException err) {
		return err.toJson();		
	}
	
    public static JsonObject mapServiceError(InvalidRepository err) {
		return Json.createObjectBuilder()
				.add("error", "Invalid repository " + err.repository)
				.add("name", err.repository)
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

    public static JsonObject objectNotFound(String repository, RepositoryPath name) {
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

	public static JsonObject wildcardNotAllowed() {
		return Json.createObjectBuilder().add("error", "wildcard not allowed in path").build();						
	}
    
	public static JsonObject partNotAllowed() {
		return Json.createObjectBuilder().add("error", "part not allowed in path").build();						
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
        return unauthorized(acl, doc.getReference());
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

    public static JsonObject unauthorized(Query acl, RepositoryPath obj) {
        return Json.createObjectBuilder()
            .add("error", "No rights to access object " + obj)
            .add("acl", acl.toJSON())
            .build();
    }
    
    public static JsonObject unauthorized(Query acl, Reference ref) {
        return Json.createObjectBuilder()
            .add("error", "No rights to access object " + ref.toString())
            .add("acl", acl.toJSON())
            .build();
    }    

    public static Response errorResponse(Status status, JsonObject error) {
        return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE).entity(error).build();
    }
}
