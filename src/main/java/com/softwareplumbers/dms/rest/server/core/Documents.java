package com.softwareplumbers.dms.rest.server.core;

import static com.softwareplumbers.dms.rest.server.model.Constants.*;

import java.io.InputStream;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonObject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.MultiPartMediaTypes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.softwareplumbers.dms.rest.server.model.Document;
import com.softwareplumbers.dms.rest.server.model.Reference;
import com.softwareplumbers.dms.rest.server.model.RepositoryService;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidDocumentId;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidReference;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspace;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspaceState;

/** Handle CRUD operations on documents.
 * 
 * Create/Read/Update/Delete operations on documents in a  repository are all performed 
 * via this interface under the /docs/{repository} path.
 * 
 * @author Jonathan Essex
 *
 */
@Component
@Path("/docs")
public class Documents {
	
	///////////--------- Static member variables --------////////////

	private static Logger LOG = Logger.getLogger("docs");

	///////////---------  member variables --------////////////

	private RepositoryServiceFactory repositoryServiceFactory;
    
	///////////---------  methods --------////////////
    
	/**
	 * Use by Spring to inject a service factory for retrieval of a named repository service.
	 * 
	 * @param serviceFactory A factory for retrieving named services
	 */
    @Autowired
    public void setRepositoryServiceFactory(RepositoryServiceFactory serviceFactory) {
        this.repositoryServiceFactory = serviceFactory;
    }

    /** GET a document on path /docs/{repository}/{id}
     * 
     * Retrieves a specific document by its unique identifier. On success, a multipart
     * response contains the document in binary format and metadata as a Json object.
     * 
     * Without any query parameters, requests the most recent version of the document.
     * 
     * May specify a specific version, or request the most recent version in the given workspace.
     * It is not valid to specify both workspace and version.
     * 
     * @param repository string identifier of a document repository
     * @param id string document id
     * @param version (optional) string version id of document
     * @param workspaceId (optional) workspace Id 
     * @return Normally a multipart response.
     */
    @GET
    @Path("{repository}/{id}")
    @Produces({ MediaType.MULTIPART_FORM_DATA, MediaType.APPLICATION_JSON })
    public Response get(
    	@PathParam("repository") String repository, 
    	@PathParam("id") String id,
    	@QueryParam("version") String version,
    	@QueryParam("workspaceId") String workspaceId) {
    	try {
    		RepositoryService service = repositoryServiceFactory.getService(repository);

    		if (service == null) 
    			return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();
    		
    		if (version != null && workspaceId != null)
    			return Response.status(Status.BAD_REQUEST).entity(Error.bothVersionAndWorkspacePresent()).build();

    		Document document;
    		if (workspaceId == null)
    			document = service.getDocument(new Reference(id, version));
    		else
    			document = service.getDocument(id, workspaceId);
    		
    		if (document != null) { 
    			FormDataBodyPart metadata = new FormDataBodyPart();
    			metadata.setName("metadata");
    			metadata.setMediaType(MediaType.APPLICATION_JSON_TYPE);
    			metadata.setEntity(document.getMetadata());
    			FormDataBodyPart file = new FormDataBodyPart();
    			file.setName("file");
    			file.setMediaType(document.getMediaType());
    			file.getHeaders().add("Content-Length", Long.toString(document.getLength()));
    			file.setEntity(new DocumentOutput(document));
    			
    			MultiPart response = new MultiPart()
    				.bodyPart(metadata)
    				.bodyPart(file);
    			
    			return Response.ok(response, MultiPartMediaTypes.MULTIPART_MIXED_TYPE).build();
    		} else {
    			return Response.status(Status.NOT_FOUND).entity(Error.documentNotFound(repository,id,version)).build();    			
    		}
    	} catch(InvalidReference e) { 
    		return Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(e)).build();
    	} catch (Throwable e) {
    		LOG.severe(e.getMessage());
    		e.printStackTrace(System.err);
    		return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
    	}
    }
    
    /** GET a document on path /docs/{repository}/{id}/file
     * 
     * retrieves a specific document by its unique identifier. On success returns
     * the original uploaded file as binary data with mime type as set when uploaded.
     * 
     * @param repository string identifier of a document repository
     * @param id string document id
     * @param version (optional) integer version number of document
     * @return A response, typically binary, with variable mime type.
     */
    @GET
    @Path("{repository}/{id}/file")
    public Response getFile(
    	@PathParam("repository") String repository, 
    	@PathParam("id") String id,
    	@QueryParam("version") String version) {
    	try {
    		RepositoryService service = repositoryServiceFactory.getService(repository);

    		if (service == null) 
    			return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();

    		Document document = service.getDocument(new Reference(id, version));
        
    		if (document != null) { 
    			return Response
    				.status(Status.OK)
    				.type(document.getMediaType())
    				.entity(new DocumentOutput(document))
    				.build();
    		} else {
    			return Response.status(Status.NOT_FOUND).entity(Error.documentNotFound(repository,id,version)).build();    			
    		}
    	} catch (Throwable e) {
    		LOG.severe(e.getMessage());
    		e.printStackTrace(System.err);
    		return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
    	}
    }
    
    /** GET metadata object on path /docs/{repository}/{id}/metadata
     * 
     * Retrieves metadata for a specific document by its unique identifier. Returns
     * the Json-encoded metadata that was orignally uploaded with the document.
     * 
     * @param repository string identifier of a document repository
     * @param id string document id
     * @param version (optional) integer version number of document
     * @return Response wrapping JSON metadata
     */
    @GET
    @Path("{repository}/{id}/metadata")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMetadata(
    	@PathParam("repository") String repository, 
    	@PathParam("id") String id,
    	@QueryParam("version") String version) {
    	try {
    		RepositoryService service = repositoryServiceFactory.getService(repository);

    		if (service == null) 
    			return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();

    		Document document = service.getDocument(new Reference(id, version));
        
    		if (document != null) { 
    			return Response
    				.status(Status.OK)
    				.entity(document.getMetadata())
    				.build();
    		} else {
    			return Response.status(Status.NOT_FOUND).entity(Error.documentNotFound(repository,id,version)).build();    			
    		}
    	} catch (Throwable e) {
    		LOG.severe(e.getMessage());
    		e.printStackTrace(System.err);
    		return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
    	}
    }
    
    /** POST a new document on path /docs/{repository}
     * 
     * Upload a new file to the repository, together with a metadata JSON object, 
     * as a mime-encoded stream. Returns a JSON object with the new document reference
     * and version. 
     * 
     * @param repository string identifier of a document repository
     * @param metadata_part
     * @param file_part
     * @return A document reference (document id and version) as JSON.
     */
    @POST
    @Path("{repository}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response post(
    	@PathParam("repository") String repository,
    	@FormDataParam("metadata") FormDataBodyPart metadata_part,
    	@FormDataParam("file") FormDataBodyPart file_part,
    	@QueryParam("workspace") String workspace,
    	@QueryParam("createWorkspace") @DefaultValue("false") boolean createWorkspace
    	) {
    	try {
    		RepositoryService service = repositoryServiceFactory.getService(repository);

    		if (service == null) 
    			return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();
    					
    		if (metadata_part == null) 
    			return Response.status(Status.BAD_REQUEST).entity(Error.missingMetadata()).build();

    		if (file_part == null) 
    			return Response.status(Status.BAD_REQUEST).entity(Error.missingFile()).build();

    		if (file_part.getMediaType() == null)
    			return Response.status(Status.BAD_REQUEST).entity(Error.missingContentType()).build();

    		Reference reference = 
    			service
    				.createDocument(
    					file_part.getMediaType(),
    					()->file_part.getEntityAs(InputStream.class),
						metadata_part.getEntityAs(JsonObject.class),
						workspace,
						createWorkspace
					);

    		if (reference != null) {
    			return Response.status(Status.CREATED).entity(reference).build();
    		} else {
    			return Response.status(Status.BAD_REQUEST).entity(Error.unexpectedFailure()).build();    			
    		}
    	} catch (InvalidWorkspace e) {
    		return Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(e)).build();
    	} catch (InvalidWorkspaceState e) {
    		return Response.status(Status.FORBIDDEN).entity(Error.mapServiceError(e)).build();    		
    	} catch (Exception e) {
    		LOG.severe(e.getMessage());
    		e.printStackTrace(System.err);
    		return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
    	}
    }
    
    /** POST a new document on path /docs/{repository}/file
     * 
     * Upload a new document to the repository, as a binary stream. Metadata for
     * this will initially be set to an empty Json object.
     * 
     * @param repository string identifier of a document repository
     * @param request complete HTTP request. Body should contain binary document.
     * @return A document reference (document id and version) as JSON.
     */
    @POST
    @Path("{repository}/file")
    @Produces(MediaType.APPLICATION_JSON)
    public Response postFile(
    	@PathParam("repository") String repository,
    	@QueryParam("workspace") String workspace,
    	@QueryParam("createWorkspace") @DefaultValue("false") boolean createWorkspace,
    	@Context HttpServletRequest request
    	) {
    	try {
    		RepositoryService service = repositoryServiceFactory.getService(repository);

    		if (service == null) 
    			return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();

    		Reference reference = 
    			service
    				.createDocument(
    					MediaType.valueOf(request.getContentType()),
    					() -> request.getInputStream(),
						EMPTY_METADATA,
						workspace,
						createWorkspace
					);

    		if (reference != null) {
    			return Response.status(Status.CREATED).entity(reference).build();
    		} else {
    			return Response.status(Status.BAD_REQUEST).entity(Error.unexpectedFailure()).build();    			
    		}
    	} catch (InvalidWorkspace e) {
    		return Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(e)).build();
    	} catch (InvalidWorkspaceState e) {
    		return Response.status(Status.FORBIDDEN).entity(Error.mapServiceError(e)).build();    		
    	} catch (Exception e) {
    		LOG.severe(e.getMessage());
    		e.printStackTrace(System.err);
    		return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
    	}
    }
  
    /** PUT a document on path /docs/{repository}/{id}
     * 
     * Updates a specific document by its unique identifier. On success, a JSON
     * response contains the new version is identifier for the document.
     * 
     * @param repository string identifier of a document repository
     * @param id string document id
     * @return A JSON response including the document id and new version id
     */
    @PUT
    @Path("{repository}/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response put(
    	@PathParam("repository") String repository,
    	@PathParam("id") String id,
    	@FormDataParam("metadata") FormDataBodyPart metadata_part,
    	@FormDataParam("file") FormDataBodyPart file_part,
    	@QueryParam("workspace") String workspace,
    	@QueryParam("createWorkspace") @DefaultValue("false") boolean createWorkspace
    	) {
    	try {
    		RepositoryService service = repositoryServiceFactory.getService(repository);

    		if (service == null) 
    			return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();
    					
    		if (metadata_part == null) 
    			return Response.status(Status.BAD_REQUEST).entity(Error.missingMetadata()).build();

    		if (file_part == null) 
    			return Response.status(Status.BAD_REQUEST).entity(Error.missingFile()).build();

    		Reference reference = 
    			service
    				.updateDocument(
    					id,
    					file_part.getMediaType(),
    					()->file_part.getEntityAs(InputStream.class),
						metadata_part.getEntityAs(JsonObject.class),
						workspace,
						createWorkspace
					);

    		if (reference != null) {
    			return Response.status(Status.ACCEPTED).entity(reference).build();
    		} else {
    			return Response.status(Status.NOT_FOUND).entity(Error.documentNotFound(repository, id, null)).build();    			
    		}
    	} catch (InvalidWorkspace e) {
    		return Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(e)).build();
    	} catch (InvalidWorkspaceState e) {
    		return Response.status(Status.FORBIDDEN).entity(Error.mapServiceError(e)).build();    		
    	} catch (InvalidDocumentId e) {
    		return Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(e)).build();    		
    	} catch (Exception e) {
    		LOG.severe(e.getMessage());
    		e.printStackTrace(System.err);
    		return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
    	}
    }
   
    /** PUT a document file on path /docs/{repository}/{id}/file
     * 
     * Updates a specific document file by its unique identifier. On success, a JSON
     * response contains the new version is identifier for the document.
     * 
     * @param repository string identifier of a document repository
     * @param id string document id
     * @return A JSON response including the document id and new version id
     */
    @PUT
    @Path("{repository}/{id}/file")
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateFile(
    	@PathParam("repository") String repository,
    	@PathParam("id") String id,
    	@QueryParam("workspace") String workspace,
    	@QueryParam("createWorkspace") @DefaultValue("false") boolean createWorkspace,
    	@Context HttpServletRequest request
    	) {
    	try {
    		RepositoryService service = repositoryServiceFactory.getService(repository);

    		if (service == null) 
    			return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();
    					
    		Reference reference = 
    			service
    				.updateDocument(
    					id,
    					MediaType.valueOf(request.getContentType()),
    					()->request.getInputStream(),
						null,
						workspace,
						createWorkspace
					);

    		if (reference != null) {
    			return Response.status(Status.ACCEPTED).entity(reference).build();
    		} else {
    			return Response.status(Status.NOT_FOUND).entity(Error.documentNotFound(repository, id, null)).build();    			
    		}
    	}  catch (InvalidWorkspace e) {
    		return Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(e)).build();
    	} catch (InvalidWorkspaceState e) {
    		return Response.status(Status.FORBIDDEN).entity(Error.mapServiceError(e)).build();    		
    	} catch (InvalidDocumentId e) {
    		return Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(e)).build();    		
    	}catch (Exception e) {
    		LOG.severe(e.getMessage());
    		e.printStackTrace(System.err);
    		return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
    	}
    }

    /** PUT a document meta-data on path /docs/{repository}/{id}/metadata
     * 
     * Updates specific document meta-data by its unique identifier. On success, a JSON
     * response contains the new version identifier for the document.
     * 
     * @param repository string identifier of a document repository
     * @param id string document id
     * @return A JSON response including the document id and new version id
     */
    @PUT
    @Path("{repository}/{id}/metadata")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateMetadata(
    	@PathParam("repository") String repository,
    	@PathParam("id") String id,
    	@QueryParam("workspace") String workspace,
    	@QueryParam("createWorkspace") @DefaultValue("false") boolean createWorkspace,
    	JsonObject metadata
    	) {
    	try {
    		RepositoryService service = repositoryServiceFactory.getService(repository);

    		if (service == null) 
    			return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();
    					
    		Reference reference = 
    			service
    				.updateDocument(
    					id,
    					null,
    					null,
						metadata,
						workspace,
						createWorkspace
					);

    		if (reference != null) {
    			return Response.status(Status.ACCEPTED).entity(reference).build();
    		} else {
    			return Response.status(Status.NOT_FOUND).entity(Error.documentNotFound(repository, id, null)).build();    			
    		}
    	}  catch (InvalidWorkspace e) {
    		return Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(e)).build();
    	} catch (InvalidWorkspaceState e) {
    		return Response.status(Status.FORBIDDEN).entity(Error.mapServiceError(e)).build();    		
    	} catch (InvalidDocumentId e) {
    		return Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(e)).build();    		
    	}catch (Exception e) {
    		LOG.severe(e.getMessage());
    		e.printStackTrace(System.err);
    		return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
    	}
    }
}
