package com.softwareplumbers.dms.rest.server.core;

import com.softwareplumbers.common.immutablelist.QualifiedName;
import com.softwareplumbers.common.abstractquery.Query;
import com.softwareplumbers.dms.rest.server.model.AuthorizationService;
import com.softwareplumbers.dms.rest.server.model.AuthorizationService.DocumentAccessRole;
import static com.softwareplumbers.dms.Constants.*;

import java.io.InputStream;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
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
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;

import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.MultiPartMediaTypes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

import com.softwareplumbers.dms.Document;
import com.softwareplumbers.dms.DocumentLink;
import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.RepositoryService;
import com.softwareplumbers.dms.Exceptions.InvalidDocumentId;
import com.softwareplumbers.dms.Exceptions.InvalidReference;
import java.io.ByteArrayInputStream;
import org.slf4j.ext.XLogger;
import java.util.Arrays;
import java.util.List;
import javax.json.JsonValue;
import javax.ws.rs.container.ContainerRequestContext;
import org.slf4j.ext.XLoggerFactory;

/** Handle CRUD operations on documents.
 * 
 * Create/Read/Update/Delete operations on documents in a repository are all performed 
 * via this interface under the /docs/{repository} path.
 * 
 * @author Jonathan Essex
 *
 */
@Component
@Path("/docs")
@Authenticated
public class Documents {
	
	///////////--------- Static member variables --------////////////

	private static final XLogger LOG = XLoggerFactory.getXLogger(Documents.class);
    
    private static final List<MediaType> GET_FILE_RESULT_TYPES = Arrays.asList(MediaType.WILDCARD_TYPE, MediaType.APPLICATION_XHTML_XML_TYPE);

	///////////---------  member variables --------////////////

	private RepositoryServiceFactory repositoryServiceFactory;
    private AuthorizationServiceFactory authorizationServiceFactory;
    
    private  AuthorizationService getAuthorizationService(String repository) throws InvalidRepository {
        try {
            return authorizationServiceFactory.getService(repository);
        } catch (NoSuchBeanDefinitionException e) {
            throw new InvalidRepository(repository);
        }
    }
    
    private  RepositoryService getRepositoryService(String repository) throws InvalidRepository {
        try {
            return repositoryServiceFactory.getService(repository);
        } catch (NoSuchBeanDefinitionException e) {
            throw new InvalidRepository(repository);
        }
    }
    
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
    
    /**
     * Used by Spring to inject an authorization service.
     * 
     * The authorization service is used to check that an authenticated user has the right to perform an operation.
     * 
     * @param authorizationServiceFactory 
     */
    @Autowired
    public void setAuthorizationServiceFactory(AuthorizationServiceFactory authorizationServiceFactory) {
        this.authorizationServiceFactory = authorizationServiceFactory;
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
     * @param requestContext request context (provides user metadata)
     * @return Normally a multipart response.
     */
    @GET
    @Path("{repository}/{id}")
    @Produces({ MediaType.MULTIPART_FORM_DATA, MediaType.APPLICATION_JSON })
    public Response get(
    	@PathParam("repository") String repository, 
    	@PathParam("id") String id,
    	@QueryParam("version") String version,
        @Context ContainerRequestContext requestContext
    ) {
        LOG.entry(repository, id, version);
    	try {
    		RepositoryService service = getRepositoryService(repository);
            AuthorizationService authorizationService = authorizationServiceFactory.getService(repository);
            JsonObject userMetadata = (JsonObject)requestContext.getProperty("userMetadata");

    		if (service == null || authorizationService == null) 
    			return Error.errorResponse(Status.NOT_FOUND,Error.repositoryNotFound(repository));
    		
    		Document document;
    			document = service.getDocument(new Reference(id, version));
    		
    		if (document != null) { 
                Query acl = authorizationService.getDocumentACL(document, DocumentAccessRole.READ);
                if (!acl.containsItem(userMetadata))
                    return LOG.exit(Error.errorResponse(Status.FORBIDDEN, Error.unauthorized(acl, document)));
                
                if (requestContext.getAcceptableMediaTypes().contains(MediaType.MULTIPART_FORM_DATA_TYPE)) {
                    FormDataBodyPart metadata = new FormDataBodyPart();
                    metadata.setName("metadata");
                    metadata.setMediaType(MediaType.APPLICATION_JSON_TYPE);
                    // Breaking change 20190303 - returned Json includes id, version, as well as metadata 
                    // Old metadata is in 'metadata' property of returned object
                    metadata.setEntity(document.toJson());
                    FormDataBodyPart file = new FormDataBodyPart();
                    file.setName("file");
                    file.setMediaType(MediaType.valueOf(document.getMediaType()));
                    file.getHeaders().add("Content-Length", Long.toString(document.getLength()));
                    file.setEntity(new DocumentOutput(service, document));

                    MultiPart response = new MultiPart()
                        .bodyPart(metadata)
                        .bodyPart(file);

                    return LOG.exit(Response.ok(response, MultiPartMediaTypes.MULTIPART_MIXED_TYPE).build());
                } else {                    
                    Response response = Response
                        .ok() 
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        // Breaking change 20190303 - returned Json includes id, version, as well as metadata 
                        // Old metadata is in 'metadata' property of returned object
                        .entity(document.toJson())
                        .build();
                    
                    return LOG.exit(response);        
                }
    		} else {
    			return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.documentNotFound(repository,id,version)));    			
    		}
    	} catch(InvalidRepository e) { 
    		return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(e)));
    	} catch(InvalidReference e) { 
    		return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(e)));
    	} catch (RuntimeException e) {
    		LOG.error(e.getMessage());
    		e.printStackTrace(System.err);
    		return LOG.exit(Error.errorResponse(Status.INTERNAL_SERVER_ERROR,Error.reportException(e)));
    	} 
    }
    
    /** GET a document on path /docs/{repository}/{id}/file
     * 
     * Retrieves a specific document by its unique identifier. If no media type is specified in the 
     * request, on success returns the original uploaded file as binary data with mime type as set when
     * uploaded. If application/xhml+xml is specified, will attempt to return a representation of the
     * document content in XHTML format.
     * 
     * @param repository string identifier of a document repository
     * @param id string document id
     * @param version (optional) integer version number of document
     * @param contentType type of document requested
     * @param headers http header data including optional list of acceptable content types
     * @param requestContext request context (supplies user metadata)
     * @return A response, typically binary, with variable mime type.
     */
    @GET
    @Path("{repository}/{id}/file")
    public Response getFile(
    	@PathParam("repository") String repository, 
    	@PathParam("id") String id,
    	@QueryParam("version") String version,
        @QueryParam("contentType") @DefaultValue("*/*") MediaType contentType,
        @Context HttpHeaders headers,
        @Context ContainerRequestContext requestContext
    ) {
        LOG.entry(repository, id, version, contentType);
    	try {
    		RepositoryService service = getRepositoryService(repository);
            AuthorizationService authorizationService = getAuthorizationService(repository);
            JsonObject userMetadata = (JsonObject)requestContext.getProperty("userMetadata");

    		if (service == null || authorizationService == null) 
    			return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.repositoryNotFound(repository)));

    		Document document = service.getDocument(new Reference(id, version));
            		
            if (document != null) { 
                Query acl = authorizationService.getDocumentACL(document, DocumentAccessRole.READ);
                if (!acl.containsItem(userMetadata))
                    return LOG.exit(Error.errorResponse(Status.FORBIDDEN, Error.unauthorized(acl, document)));

        		StreamingOutput responseStream;	
                List<MediaType> acceptableTypes = MediaTypes.getAcceptableMediaTypes(headers.getAcceptableMediaTypes(), contentType);
                MediaType requestedMediaType = MediaTypes.getPreferredMediaType(acceptableTypes, GET_FILE_RESULT_TYPES);  
                MediaType responseMediaType;
                
                if (requestedMediaType == MediaType.APPLICATION_XHTML_XML_TYPE) {
                    responseStream = new XMLOutput(service, document);
                    responseMediaType = MediaType.APPLICATION_XHTML_XML_TYPE;
                } else {
                    responseStream = new DocumentOutput(service, document);
                    responseMediaType = MediaType.valueOf(document.getMediaType());
                }
    		
    			return LOG.exit(Response
    				.status(Status.OK)
    				.type(responseMediaType)
    				.entity(responseStream)
    				.build());
    		} else {
    			return LOG.exit(Response
                    .status(Status.NOT_FOUND)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(Error.documentNotFound(repository,id,version))
                    .build());    			
    		}
    	} catch(InvalidRepository e) { 
    		return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(e)));
    	} catch (RuntimeException e) {
    		LOG.error(e.getMessage());
    		e.printStackTrace(System.err);
    		return LOG.exit(Response
                .status(Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Error.reportException(e))
                .build());
    	} catch (InvalidContentType err) {
            return LOG.exit(Error.errorResponse(Status.BAD_REQUEST,Error.mapServiceError(err)));
        } catch (InvalidReference e) {
            return LOG.exit(Error.errorResponse(Status.NOT_FOUND, Error.mapServiceError(e)));
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
     * @param requestContext (supplies user metadata)
     * @return Response wrapping JSON metadata
     */
    @GET
    @Path("{repository}/{id}/metadata")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMetadata(
    	@PathParam("repository") String repository, 
    	@PathParam("id") String id,
    	@QueryParam("version") String version,
        @Context ContainerRequestContext requestContext
    ) {
        LOG.entry(repository, id, version);
    	try {
    		RepositoryService service = getRepositoryService(repository);
            AuthorizationService authorizationService = getAuthorizationService(repository);
            JsonObject userMetadata = (JsonObject)requestContext.getProperty("userMetadata");

    		if (service == null || authorizationService == null) 
    			return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.repositoryNotFound(repository)));

    		Document document = service.getDocument(new Reference(id, version));
        
    		if (document != null) { 
                Query acl = authorizationService.getDocumentACL(document, DocumentAccessRole.READ);
                if (!acl.containsItem(userMetadata))
                    return LOG.exit(Error.errorResponse(Status.FORBIDDEN, Error.unauthorized(acl, document)));

    			return LOG.exit(Response
    				.status(Status.OK) 
                    .type(MediaType.APPLICATION_JSON_TYPE)
    				// Breaking change 20190303 - returned Json includes id, version, as well as metadata 
    				// Old metadata is in 'metadata' property of returned object
    				.entity(document.toJson())
    				.build());
    		} else {
    			return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.documentNotFound(repository,id,version)));    			
    		}
    	} catch(InvalidRepository e) { 
    		return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(e)));
    	} catch (RuntimeException e) {
    		LOG.error(e.getMessage());
    		e.printStackTrace(System.err);
    		return LOG.exit(Error.errorResponse(Status.INTERNAL_SERVER_ERROR,Error.reportException(e)));
    	} catch (InvalidReference e) {
            return LOG.exit(Error.errorResponse(Status.NOT_FOUND, Error.mapServiceError(e)));
        }
    }

    /** list workspaces in repository {repository} which document {id} belongs
     * 
     * Retrieves a list of workspaces to which a given document belongs.
     * 
     * @param repository string identifier of a document repository
     * @param id string document id
     * @param version (optional) integer version number of document
     * @param requestContext request context (supplies user metadata)
     * @return Response wrapping JSON metadata
     */
    @GET
    @Path("{repository}/{id}/workspaces")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getWorkspaces(
        @PathParam("repository") String repository, 
        @PathParam("id") String id,
        @QueryParam("version") String version,
        @Context ContainerRequestContext requestContext
    ) {
        LOG.entry(repository, id, version);
        try {
            RepositoryService service = getRepositoryService(repository);
            AuthorizationService authorizationService = getAuthorizationService(repository);
            JsonObject userMetadata = (JsonObject)requestContext.getProperty("userMetadata");

    		if (service == null || authorizationService == null) 
                return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.repositoryNotFound(repository)));

            Query accessConstraint = authorizationService.getAccessConstraint(userMetadata, null, QualifiedName.ROOT);
            JsonArrayBuilder results = Json.createArrayBuilder();
            Stream<DocumentLink> links = service.listWorkspaces(id, null, accessConstraint);
            if (links != null) links.forEach(item -> results.add(item.toJson()));
            return LOG.exit(Response.ok().type(MediaType.APPLICATION_JSON).type(MediaType.APPLICATION_JSON).entity(results.build()).build());
                    
        } catch(InvalidRepository e) { 
    		return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(e)));
    	} catch (RuntimeException e) {
            LOG.error(e.getMessage());
            e.printStackTrace(System.err);
            return LOG.exit(Error.errorResponse(Status.INTERNAL_SERVER_ERROR,Error.reportException(e)));
        } catch (InvalidDocumentId e) {
            return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(e)));
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
     * @param requestContext request context (supplies user metadata)
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
        @Context ContainerRequestContext requestContext        
    	) {
        LOG.entry(repository, metadata_part.getName(), file_part.getName());
    	try {
    		RepositoryService service = getRepositoryService(repository);
            AuthorizationService authorizationService = getAuthorizationService(repository);
            JsonObject userMetadata = (JsonObject)requestContext.getProperty("userMetadata");

    		if (service == null || authorizationService == null) 
    			return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.repositoryNotFound(repository)));
    					
    		if (metadata_part == null) 
    			return LOG.exit(Error.errorResponse(Status.BAD_REQUEST,Error.missingMetadata()));

    		if (file_part == null) 
    			return LOG.exit(Error.errorResponse(Status.BAD_REQUEST,Error.missingFile()));

    		if (file_part.getMediaType() == null)
    			return LOG.exit(Error.errorResponse(Status.BAD_REQUEST,Error.missingContentType()));
            
            MediaType computedMediaType = MediaTypes.getComputedMediaType(file_part.getMediaType(), file_part.getName());
                        
            JsonObject metadata = metadata_part.getEntityAs(JsonObject.class);
            
            Query acl = authorizationService.getDocumentACL(null, computedMediaType.toString(), metadata, DocumentAccessRole.CREATE);
            if (!acl.containsItem(userMetadata))
                return LOG.exit(Error.errorResponse(Status.FORBIDDEN, Error.unauthorized(acl, computedMediaType, metadata)));
            
    		Reference reference = 
    			service
    				.createDocument(
    					computedMediaType.toString(),
    					()->file_part.getEntityAs(InputStream.class),
						metadata
                    );

    		if (reference != null) {
    			return Response.status(Status.CREATED).type(MediaType.APPLICATION_JSON).entity(reference).build();
    		} else {
    			return LOG.exit(Error.errorResponse(Status.BAD_REQUEST,Error.unexpectedFailure()));    			
    		}
    	} catch(InvalidRepository e) { 
    		return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(e)));
    	} catch (RuntimeException e) {
    		LOG.error(e.getMessage());
    		e.printStackTrace(System.err);
    		return LOG.exit(Error.errorResponse(Status.INTERNAL_SERVER_ERROR,Error.reportException(e)));
    	} catch (InvalidReference e) {
    		return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(e)));
        }
    }
    
    /** POST a new document on path /docs/{repository}/file
     * 
     * Upload a new document to the repository, as a binary stream. Metadata for
     * this will initially be set to an empty Json object.
     * 
     * @param repository string identifier of a document repository
     * @param request complete HTTP request. Body should contain binary document.
     * @param requestContext request context (supplies user metadata)
     * @return A document reference (document id and version) as JSON.
     */
    @POST
    @Path("{repository}/file")
    @Produces(MediaType.APPLICATION_JSON)
    public Response postFile(
    	@PathParam("repository") String repository,
    	@Context HttpServletRequest request,
        @Context ContainerRequestContext requestContext        
    	) {
        LOG.entry(repository, request.getMethod());
    	try {
    		RepositoryService service = getRepositoryService(repository);
            AuthorizationService authorizationService = getAuthorizationService(repository);
            JsonObject userMetadata = (JsonObject)requestContext.getProperty("userMetadata");

    		if (service == null || authorizationService == null) 
    			return Error.errorResponse(Status.NOT_FOUND,Error.repositoryNotFound(repository));
            
            Query acl = authorizationService.getDocumentACL(null, request.getContentType(), JsonValue.EMPTY_JSON_OBJECT, DocumentAccessRole.CREATE);
            if (!acl.containsItem(userMetadata))
                return LOG.exit(Error.errorResponse(Status.FORBIDDEN, Error.unauthorized(acl, request.getContentType(), JsonValue.EMPTY_JSON_OBJECT)));

    		Reference reference = 
    			service
    				.createDocument(
    					request.getContentType(),
    					() -> request.getInputStream(),
						EMPTY_METADATA
					);

    		if (reference != null) {
    			return Response.status(Status.CREATED).type(MediaType.APPLICATION_JSON).entity(reference).build();
    		} else {
    			return Error.errorResponse(Status.BAD_REQUEST,Error.unexpectedFailure());    			
    		}
    	} catch(InvalidRepository e) { 
    		return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(e)));
    	} catch (InvalidReference e) {
    		return Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(e));
        } catch (RuntimeException e) {
    		LOG.error(e.getMessage());
    		e.printStackTrace(System.err);
    		return Error.errorResponse(Status.INTERNAL_SERVER_ERROR,Error.reportException(e));
    	} 
    }
  
    /** PUT a document on path /docs/{repository}/{id}
     * 
     * Updates a specific document by its unique identifier. On success, a JSON
     * response contains the new version is identifier for the document.
     * 
     * @param repository string identifier of a document repository
     * @param id string document id
     * @param requestContext request context (supplies user metadata)
     * @param metadata_part metadata for document
     * @param file_part binary document data
     * @param workspace workspace id
     * @param createWorkspace flag to tell system to create workspace
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
        @Context ContainerRequestContext requestContext        
    ) {
        LOG.entry(repository, id, metadata_part.getName(), file_part.getName());
    	try {
    		RepositoryService service = getRepositoryService(repository);
            AuthorizationService authorizationService = getAuthorizationService(repository);
            JsonObject userMetadata = (JsonObject)requestContext.getProperty("userMetadata");

    		if (service == null || authorizationService == null) 
    			return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.repositoryNotFound(repository)));
    					
    		if (metadata_part == null) 
    			return LOG.exit(Error.errorResponse(Status.BAD_REQUEST,Error.missingMetadata()));

    		if (file_part == null) 
    			return LOG.exit(Error.errorResponse(Status.BAD_REQUEST,Error.missingFile()));

            MediaType computedMediaType = MediaTypes.getComputedMediaType(file_part.getMediaType(), file_part.getName());
						
            JsonObject metadata = metadata_part.getEntityAs(JsonObject.class);
                        
            Query acl = authorizationService.getDocumentACL(new Reference(id), computedMediaType.toString(), null, DocumentAccessRole.UPDATE);
            if (!acl.containsItem(userMetadata))
                return LOG.exit(Error.errorResponse(Status.FORBIDDEN, Error.unauthorized(acl, id)));
           
            byte[] data = file_part.getEntityAs(byte[].class);
            
            Reference reference = 
    			service
    				.updateDocument(
    					id,
    					computedMediaType.toString(),
    					()->new ByteArrayInputStream(data),
						metadata
					);

    		if (reference != null) {
    			return LOG.exit(Error.errorResponse(Status.ACCEPTED,reference.toJson()));
    		} else {
    			return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.documentNotFound(repository, id, null)));    			
    		}
    	} catch(InvalidRepository e) { 
    		return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(e)));
    	} catch (InvalidDocumentId e) {
    		return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(e)));    		
    	} catch (RuntimeException e) {
    		LOG.error(e.getMessage());
    		e.printStackTrace(System.err);
    		return LOG.exit(Error.errorResponse(Status.INTERNAL_SERVER_ERROR,Error.reportException(e)));
    	} catch (InvalidReference e) {
    		return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(e)));
        }
    }
   
    /** PUT a document file on path /docs/{repository}/{id}/file
     * 
     * Updates a specific document file by its unique identifier. On success, a JSON
     * response contains the new version is identifier for the document.
     * 
     * @param repository string identifier of a document repository
     * @param id unique identifier of document to update
     * @param workspace (optional) id of workspace in which to put document
     * @param createWorkspace if true, the workspace will be created if id does not already exist 
     * @param requestContext request context (supplies user metadata)
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
    	@Context HttpServletRequest request,
        @Context ContainerRequestContext requestContext
    ) {
        LOG.entry(repository, id, workspace, createWorkspace, request);
    	try {
    		RepositoryService service = getRepositoryService(repository);
            AuthorizationService authorizationService = getAuthorizationService(repository);
            JsonObject userMetadata = (JsonObject)requestContext.getProperty("userMetadata");

    		if (service == null || authorizationService == null) 
    			return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.repositoryNotFound(repository)));
    		
            Query acl = authorizationService.getDocumentACL(new Reference(id), request.getContentType(), null, DocumentAccessRole.UPDATE);
            if (!acl.containsItem(userMetadata))
                return LOG.exit(Error.errorResponse(Status.FORBIDDEN, Error.unauthorized(acl, id)));

    		Reference reference = 
    			service
    				.updateDocument(
    					id,
    					request.getContentType(),
    					()->request.getInputStream(),
						null
					);

    		if (reference != null) {
    			return LOG.exit(Error.errorResponse(Status.ACCEPTED,reference.toJson()));
    		} else {
    			return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.documentNotFound(repository, id, null)));    			
    		}
    	} catch(InvalidRepository e) { 
    		return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(e)));
    	} catch (InvalidDocumentId e) {
    		return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(e)));    		
    	}catch (RuntimeException e) {
    		LOG.error(e.getMessage());
    		e.printStackTrace(System.err);
    		return LOG.exit(Error.errorResponse(Status.INTERNAL_SERVER_ERROR,Error.reportException(e)));
    	} catch (InvalidReference e) {
    		return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(e)));    		
        }
    }

    /** PUT a document meta-data on path /docs/{repository}/{id}/metadata
     * 
     * Updates specific document meta-data by its unique identifier. On success, a JSON
     * response contains the new version identifier for the document.
     * 
     * @param repository string identifier of a document repository
     * @param id string document id
     * @param workspace workspace Id
     * @param createWorkspace flag to tell system to create parent workspace if necessary
     * @param requestContext request context (for user metadata)
     * @param metadata new metadata
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
        @Context ContainerRequestContext requestContext,
    	JsonObject metadata
    	) {
        LOG.entry(repository, id, workspace, createWorkspace, metadata);
    	try {
    		RepositoryService service = getRepositoryService(repository);
            AuthorizationService authorizationService = getAuthorizationService(repository);
            JsonObject userMetadata = (JsonObject)requestContext.getProperty("userMetadata");

    		if (service == null || authorizationService == null) 
    			return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.repositoryNotFound(repository)));
    					
    		Query acl = authorizationService.getDocumentACL(new Reference(id), null, null, DocumentAccessRole.UPDATE);
            if (!acl.containsItem(userMetadata))
                return LOG.exit(Error.errorResponse(Status.FORBIDDEN, Error.unauthorized(acl, id)));

            Reference reference = 
    			service
    				.updateDocument(
    					id,
    					null,
    					null,
						metadata
					);

    		if (reference != null) {
    			return LOG.exit(Error.errorResponse(Status.ACCEPTED,reference.toJson()));
    		} else {
    			return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.documentNotFound(repository, id, null)));    			
    		}
    	} catch(InvalidRepository e) { 
    		return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(e)));
    	} catch (InvalidDocumentId e) {
    		return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(e)));    		
    	}catch (RuntimeException e) {
    		LOG.error(e.getMessage());
    		e.printStackTrace(System.err);
    		return LOG.exit(Error.errorResponse(Status.INTERNAL_SERVER_ERROR,Error.reportException(e)));
    	} catch (InvalidReference e) {
    		return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(e)));
        }
    }
    

}
