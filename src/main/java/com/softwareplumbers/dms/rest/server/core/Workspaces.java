package com.softwareplumbers.dms.rest.server.core;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
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

import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.MultiPartMediaTypes;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

import com.softwareplumbers.dms.Document;
import com.softwareplumbers.dms.DocumentLink;
import com.softwareplumbers.dms.NamedRepositoryObject;
import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.RepositoryObject;
import com.softwareplumbers.dms.RepositoryService;
import com.softwareplumbers.dms.Exceptions.InvalidObjectName;
import com.softwareplumbers.dms.Exceptions.InvalidReference;
import com.softwareplumbers.dms.Exceptions.InvalidWorkspace;
import com.softwareplumbers.dms.Exceptions.InvalidWorkspaceState;
import com.softwareplumbers.dms.rest.server.model.UpdateType;
import org.slf4j.ext.XLogger;
import com.softwareplumbers.dms.Workspace;
import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractquery.Query;
import com.softwareplumbers.dms.rest.server.model.AuthorizationService;
import com.softwareplumbers.dms.rest.server.model.AuthorizationService.ObjectAccessRole;

import com.softwareplumbers.dms.DocumentNavigatorService;
import com.softwareplumbers.dms.DocumentNavigatorService.DocumentFormatException;
import com.softwareplumbers.dms.DocumentNavigatorService.PartNotFoundException;
import com.softwareplumbers.dms.Exceptions.InvalidDocumentId;
import com.softwareplumbers.dms.Options;
import com.softwareplumbers.dms.StreamableRepositoryObject;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import javax.ws.rs.POST;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.UriInfo;
import org.slf4j.ext.XLoggerFactory;


/** Handle catalog operations on repositories and documents.
 * 
 * operations on a workspace all performed 
 * via this interface under the /ws/{repository}/{workspace} path.
 * 
 * @author Jonathan Essex
 *
 */
@Component
@Authenticated
@Path("/ws")
public class Workspaces {
    
    
    ///////////--------- Static member variables --------////////////

    private static final XLogger LOG = XLoggerFactory.getXLogger(Workspaces.class);
    
    private static final List<MediaType> GET_RESULT_TYPES = Arrays.asList(
        MediaType.WILDCARD_TYPE,
        MediaType.MULTIPART_FORM_DATA_TYPE, 
        MediaType.APPLICATION_XHTML_XML_TYPE, 
        MediaType.APPLICATION_JSON_TYPE
    );

    ///////////---------  member variables --------////////////

    private RepositoryServiceFactory repositoryServiceFactory;
    private DocumentNavigatorService navigator;
    private AuthorizationServiceFactory authorizationServiceFactory;

    ///////////---------  static methods ----------///////////
    
    private static String stripBraces(String pathPart) {
        return pathPart.replaceAll("\\{", "%7B").replaceAll("\\}", "%7D");
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
     * Used by Spring to inject a document navigator service.
     * 
     * The document navigator service is used to navigate between parts of a single document.
     * 
     * @param documentNavigatorService 
     */
    @Autowired
    public void setDocumentNavigatorService(DocumentNavigatorService documentNavigatorService) {
        this.navigator = documentNavigatorService;
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

    /** GET information from a workspace path /ws/{repository}/{path}
     * 
     * Retrieves information about the given workspace. The workspace may be a path (i.e.
     * have several elements separated by '/'). An element beginning with '~' is assumed to be
     * an id. Thus, knowing the id of a workspace, we can use a path like ~AF2s6sv34/subworkspace/document.txt 
     * to find a document in that workspace without knowing the full path to the parent workspace. 
     * 
     * A path in a workspace identifies either a document or a workspace. Paths may contain simple
     * wildcards ('*' for 0 or more characters, '?' for a single character). If a path contains wildcards,
     * the result of this endpoint will always be a JSON list containing metadata for all objects with paths
     * matching the template provided. If no items are matched, the result will be an empty array.
     * 
     * If a path contains no wildcards, and it does not match any item, the result will be a HTTP Status NOT FOUND.
     * 
     * If a path contains no wildcards, and it matches something, the result depends on the type of object matched and
     * the content type specified in the accepts header of the request.
     * 
     * If the item matched is a document:
     * 
     * * If there is no content type specified, the raw document will be returned
     * * If the content type is APPLICATION_JSON, document metadata is returned in JSON format
     * * If the content type is MULTIPART_FORM_DATA, both the raw document and metadata are returned
     * * If the content type is APPLICATION_XHTML_XML, and XHTML representation of the document may be returned
     * 
     * If the item matched is a workspace, workspace metadata is returned in JSON format.
     * 
     * Content type can be specified either in the HTTP Accept header or in the contentType query parameter. The reason for
     * this is that it is not possible to specify the Accept header in some important use cases (for example in the source
     * of an iframe). 
     * 
     * @param repository string identifier of a document repository
     * @param workspacePath identifier of a workspace or document
     * @param filter AbstractQuery filter (in Base64 encoded Json) to fine down results of wildcard searches
     * @param contentType explicitly require contentType
     * @param headers including content type information
     * @param requestContext context (from which we get userMetadata)
     * @return Information about the workspace or file in json format, or the file itself, depending on the requested content type
     */
    @GET
    @Path("/{repository}/{workspace:[^?]+}")
    public Response get(
        @PathParam("repository") String repository,
        @PathParam("workspace") WorkspacePath workspacePath,
        @QueryParam("filter") String filter,
        @QueryParam("contentType") @DefaultValue("*/*") String contentType,
        @Context HttpHeaders headers,
        @Context ContainerRequestContext requestContext
    ) {
        LOG.entry(repository, workspacePath, filter, contentType, headers.getAcceptableMediaTypes());
        
        try {
            
            Query filterConstraint = filter != null && filter.length() > 0 ? Query.urlDecode(filter) : Query.UNBOUNDED;
            LOG.debug("Decoded filter: {}", filterConstraint);
            RepositoryService service = repositoryServiceFactory.getService(repository);
            AuthorizationService authorizationService = authorizationServiceFactory.getService(repository);
            JsonObject userMetadata = (JsonObject)requestContext.getProperty("userMetadata");

            if (service == null || authorizationService == null) 
                return LOG.exit(Error.errorResponse(Status.NOT_FOUND, Error.repositoryNotFound(repository)));

            if (!workspacePath.queryPath.isEmpty() || !workspacePath.queryPartPath.isEmpty()) {
                Stream<NamedRepositoryObject> results;
                Query accessConstraint = authorizationService.getAccessConstraint(userMetadata, workspacePath.rootId, workspacePath.staticPath);
                Query combinedConstraint = accessConstraint.intersect(filterConstraint);
                QualifiedName fullPath = workspacePath.staticPath.addAll(workspacePath.queryPath);
                if (workspacePath.documentId != null) {
                    results = service.listWorkspaces(workspacePath.documentId, fullPath, combinedConstraint)
                        .map(item->(NamedRepositoryObject)item);
                } else {
                    results = service.catalogueByName(workspacePath.rootId, fullPath, combinedConstraint, false);
                }
                if (!workspacePath.queryPartPath.isEmpty() || !workspacePath.staticPartPath.isEmpty()) {
                    results = results
                        .filter(obj->obj.getType() == RepositoryObject.Type.DOCUMENT_LINK)
                        .flatMap(link->navigator.catalogParts(((DocumentLink)link), workspacePath.staticPartPath.addAll(workspacePath.queryPartPath)));
                } 
                JsonArrayBuilder response = Json.createArrayBuilder();
                results.forEach(item -> response.add(item.toJson(service, navigator, 1, 0)));
                JsonArray responseObj = response.build();
                LOG.debug("response: {}", responseObj);
                return LOG.exit(Response.ok().type(MediaType.APPLICATION_JSON).entity(responseObj).build());
            } else {
  
                // Path has no wildcards, so we are returning at most one object
                NamedRepositoryObject result;
                if (workspacePath.documentId != null) {
                    result = service.getDocumentLink(workspacePath.rootId, workspacePath.staticPath, workspacePath.documentId);
                } else {
                    result = service.getObjectByName(workspacePath.rootId, workspacePath.staticPath);
                }
                if (result != null) { 
                    Query acl = authorizationService.getObjectACL(result, AuthorizationService.ObjectAccessRole.READ);
                    if (!acl.containsItem(userMetadata)) {
                        return LOG.exit(Error.errorResponse(Status.FORBIDDEN, Error.unauthorized(acl, result)));
                    }
                    switch (result.getType()) {
                        case WORKSPACE:
                            if (workspacePath.staticPartPath.isEmpty()) {
                        		return LOG.exit(Response.ok().type(MediaType.APPLICATION_JSON).entity(result.toJson(service,navigator,1,0)).build());                    		
                            } else {
                                return LOG.exit(Error.errorResponse(Status.BAD_REQUEST, Error.badOperation("Workspaces do not have document parts")));
                            }
                    	case DOCUMENT_LINK:
                            String documentName = workspacePath.staticPath.part;
                            if (!workspacePath.staticPartPath.isEmpty()) {
                                result = navigator.getPartByName(((DocumentLink)result), workspacePath.staticPartPath);
                                documentName = workspacePath.staticPartPath.part;
                            }
                            List<MediaType> acceptableTypes = MediaTypes.getAcceptableMediaTypes(headers.getAcceptableMediaTypes(), MediaType.valueOf(contentType));
                            MediaType requestedMediaType = MediaTypes.getPreferredMediaType(acceptableTypes, GET_RESULT_TYPES);  
                            if (requestedMediaType == MediaType.MULTIPART_FORM_DATA_TYPE)  {
                                StreamableRepositoryObject document = (StreamableRepositoryObject)result;
                                FormDataBodyPart metadata = new FormDataBodyPart();
                                metadata.setName("metadata");
                                metadata.setMediaType(MediaType.APPLICATION_JSON_TYPE);
                                metadata.setEntity(document.toJson(service, navigator, 1, 0));
                                FormDataBodyPart file = new FormDataBodyPart();
                                file.setName("file");
                                file.setMediaType(MediaType.valueOf(document.getMediaType()));
                                file.getHeaders().add("Content-Length", Long.toString(document.getLength()));
                                file.setEntity(new DocumentOutput(document));
                                MultiPart response = new MultiPart()
                                    .bodyPart(metadata)
                                    .bodyPart(file);                                   
                                return LOG.exit(Response.ok(response, MultiPartMediaTypes.MULTIPART_MIXED_TYPE).build());                                	
                            } else if (requestedMediaType == MediaType.APPLICATION_XHTML_XML_TYPE) {
                                Document document = (Document)result;
                                return LOG.exit(Response.ok()
                                    .type(MediaType.APPLICATION_XHTML_XML_TYPE)
                                    .entity(new XMLOutput(document)).build());                                	
                            } else if (requestedMediaType == MediaType.APPLICATION_JSON_TYPE) {
                                return LOG.exit(Response.ok().type(MediaType.APPLICATION_JSON).entity(result.toJson(service, navigator, 1, 0)).build());
                            } else {
                                Document document = (Document)result;
                                return LOG.exit(Response.ok()
                                    .header("content-disposition", "attachment; filename=" + URLEncoder.encode(documentName, StandardCharsets.UTF_8.name()))
                                    .type(document.getMediaType())
                                    .entity(new DocumentOutput(document)).build());
                            }
                        default:
                            throw new RuntimeException("Unknown result type:" + result.getType());
                    	}
                } else {
                    return LOG.exit(Error.errorResponse(Status.NOT_FOUND, Error.objectNotFound(repository, workspacePath)));
                }
            }

        } catch (InvalidWorkspace err) {
            return LOG.exit(Error.errorResponse(Status.NOT_FOUND, Error.mapServiceError(err)));
        } catch (InvalidDocumentId err) {
            return LOG.exit(Error.errorResponse(Status.NOT_FOUND, Error.mapServiceError(err)));
        } catch (UnsupportedEncodingException err) {
            return LOG.exit(Error.errorResponse(Status.INTERNAL_SERVER_ERROR, Error.reportException(err)));
        } catch (InvalidObjectName err) {
            return LOG.exit(Error.errorResponse(Status.NOT_FOUND, Error.mapServiceError(err)));
        } catch (InvalidContentType err) {
            return LOG.exit(Error.errorResponse(Status.BAD_REQUEST, Error.mapServiceError(err)));
        } catch (DocumentFormatException err) {
            return LOG.exit(Error.errorResponse(Status.BAD_REQUEST, Error.mapServiceError(err)));
        } catch (PartNotFoundException err) {
            return LOG.exit(Error.errorResponse(Status.NOT_FOUND, Error.mapServiceError(err)));
        } catch (RuntimeException e) {
            LOG.error(e.getMessage());
            e.printStackTrace(System.err);
            return LOG.exit(Error.errorResponse(Status.INTERNAL_SERVER_ERROR, Error.reportException(e)));
        }
    }



    /** GET workspaces that a given document belongs to state on path /ws/{repository}
     * 
     * Retrieves information about the workspaces a document belongs to 
     * 
     * @param repository string identifier of a document repository
     * @param documentId string identifier of a document
     * @param requestContext context (from which we get userMetadata)
     * @return Information about the workspaces in json format
     */
    @GET
    @Path("/{repository}")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getWorkspaces(
        @PathParam("repository") String repository,
        @QueryParam("id") String documentId,
        @Context ContainerRequestContext requestContext
    ) {
        LOG.entry(repository);
        try {
            RepositoryService service = repositoryServiceFactory.getService(repository);
            AuthorizationService authorizationService = authorizationServiceFactory.getService(repository);
            JsonObject userMetadata = (JsonObject)requestContext.getProperty("userMetadata");

            if (service == null || authorizationService == null) 
                return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.repositoryNotFound(repository)));

            Query accessConstraint = authorizationService.getAccessConstraint(userMetadata, null, QualifiedName.ROOT);

            JsonArrayBuilder result = Json.createArrayBuilder();
            service.listWorkspaces(documentId, null, accessConstraint)
                .map(DocumentLink::toJson)
                .forEach(value -> result.add(value));
            
            //TODO: must be able to do this in a stream somehow.
            return LOG.exit(Response.ok().type(MediaType.APPLICATION_JSON).entity(result.build()).build());
        } catch (RuntimeException e) {
            LOG.error(e.getMessage());
            e.printStackTrace(System.err);
            return LOG.exit(Error.errorResponse(Status.INTERNAL_SERVER_ERROR,Error.reportException(e)));
        } catch (InvalidDocumentId e) {
            return LOG.exit(Error.errorResponse(Status.NOT_FOUND, Error.mapServiceError(e)));
        }
    }
    
    private static ObjectAccessRole getRequiredRole(UpdateType updateType) {
        switch (updateType) {
            case CREATE: return ObjectAccessRole.CREATE;
            case UPDATE: return ObjectAccessRole.UPDATE;
            case CREATE_OR_UPDATE: return ObjectAccessRole.CREATE;
            default:
                throw new RuntimeException("Uknown UpdateType");
        }
    }

    /** Update workspace state on path /ws/{repository}/{objectName}.
     * 
     * The objectName is a path (i.e.have several elements separated by '/'). If the first
     * element begins with a '~', what follows is assumed to be a workspace id. If not, we assume it 
     * is a name and call the service accordingly.
     * 
     * Can be used to modify workspace state (e.g. Closing or Finalizing a workspace),
     * create a new workspace, or put an existing document in a workspace.
     * 
     * The updateType parameter defaults to CREATE_OR_UPDATE and influences behavior as follows:
     * 
     * | updateType       | Behavior |
     * | -----------------|----------|
     * | CREATE           | Creates a new workspace or document, error if objectName exists already |
     * | UPDATE           | Updates a workspace or document, error if objectName does not exist already |
     * | CREATE_OR_UPDATE | Create or update a workspace or document |
     * 
     * @param repository string identifier of a document repository
     * @param objectName string identifier of a workspace or document
     * @param createWorkspace if true, proxy will create any parent workspaces specified in the path
     * @param updateType controls update behavior on actual object referenced by objectName
     * @param object Json representation of object to update
     * @param requestContext context (from which we get userMetadata)
     * @return response accepted or error
     */
    @PUT
    @Path("/{repository}/{workspace:[^?]+}")
    @Consumes({ MediaType.APPLICATION_JSON })
    public Response put(
            @PathParam("repository") String repository,
            @PathParam("workspace") WorkspacePath workspacePath,
            @QueryParam("createWorkspace") @DefaultValue("false") boolean createWorkspace,
            @QueryParam("updateType") @DefaultValue("CREATE_OR_UPDATE") UpdateType updateType,
            @Context ContainerRequestContext requestContext,
            JsonObject object) {
        LOG.entry(repository, workspacePath, createWorkspace, updateType, object);
        try {
            RepositoryService service = repositoryServiceFactory.getService(repository);
            AuthorizationService authorizationService = authorizationServiceFactory.getService(repository);
            JsonObject userMetadata = (JsonObject)requestContext.getProperty("userMetadata");

            if (service == null || authorizationService == null) 
                return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.repositoryNotFound(repository)));

            if (workspacePath == null || workspacePath.isEmpty())
                return LOG.exit(Error.errorResponse(Status.BAD_REQUEST,Error.missingResourcePath()));

            RepositoryObject.Type type = RepositoryObject.getType(object);

            Query acl = authorizationService.getObjectACL(workspacePath.rootId, workspacePath.staticPath, type, null, getRequiredRole(updateType));
                
            if (!acl.containsItem(userMetadata)) {
                return LOG.exit(Error.errorResponse(Status.FORBIDDEN, Error.unauthorized(acl, workspacePath.toString())));
            }
            
            if (type == RepositoryObject.Type.WORKSPACE) {

                Workspace.State state = Workspace.getState(object);
                JsonObject metadata = RepositoryObject.getMetadata(object);
    
                Workspace workspace;

                if (updateType == UpdateType.CREATE) {
                    Options.Create.Builder options = Options.Create.EMPTY
                        .addOptionIf(Options.CREATE_MISSING_PARENT, createWorkspace);
                    workspace = service.createWorkspaceByName(workspacePath.rootId, workspacePath.staticPath, state, metadata, options.build());
                } else {
                    Options.Update.Builder options = Options.Update.EMPTY
                        .addOptionIf(Options.CREATE_MISSING_PARENT, createWorkspace)
                        .addOptionIf(Options.CREATE_MISSING_ITEM, updateType == UpdateType.CREATE_OR_UPDATE);
                    workspace = service.updateWorkspaceByName(workspacePath.rootId, workspacePath.staticPath, state, metadata, options.build());
                }
                
                return LOG.exit(Response.accepted().type(MediaType.APPLICATION_JSON).entity(workspace.toJson()).build());    

            } else {
                
                Reference reference = Document.getReference(object);                
                JsonObject metadata = RepositoryObject.getMetadata(object);
                DocumentLink link;
                
                if (updateType == UpdateType.CREATE) {
                    if (reference == null) {
                        throw new InvalidDocumentId("null");
                    } else {
                        Options.Create.Builder options = Options.Create.EMPTY.addOptionIf(Options.CREATE_MISSING_PARENT, createWorkspace);
                        link = service.createDocumentLink(workspacePath.rootId, workspacePath.staticPath, reference, options.build());
                    }
                    
                } else {
                    Options.Update.Builder options = Options.Update.EMPTY
                        .addOptionIf(Options.CREATE_MISSING_PARENT, createWorkspace)
                        .addOptionIf(Options.CREATE_MISSING_ITEM, updateType == UpdateType.CREATE_OR_UPDATE);
                    link = service.updateDocumentLink(workspacePath.rootId, workspacePath.staticPath, reference, options.build());
                }
                
                if (metadata != null && !metadata.isEmpty()) {
                    link = service.updateDocumentLink(link, null, null, metadata);
                }
                
                return LOG.exit(Response.accepted().entity(link.toJson()).build());
            }
        } catch (InvalidWorkspace err) {
            LOG.error(err.getMessage());
            return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(err)));
        } catch (InvalidDocumentId err) {
            LOG.error(err.getMessage());
            return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(err)));
        } catch (InvalidObjectName err) {
            LOG.error(err.getMessage());
            return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(err)));
        } catch (InvalidReference err) {
            LOG.error(err.getMessage());
            return LOG.exit(Error.errorResponse(Status.BAD_REQUEST,Error.mapServiceError(err)));
        } catch (InvalidWorkspaceState err) {
            LOG.error(err.getMessage());
            return LOG.exit(Error.errorResponse(Status.FORBIDDEN,Error.mapServiceError(err)));
        } catch (RuntimeException e) {
            LOG.error(e.getMessage());
            return LOG.exit(Error.errorResponse(Status.INTERNAL_SERVER_ERROR,Error.reportException(e)));
        } 
    }

    /** Create a new document in workspace path /ws/{repository}/{objectName}
     * 
     * The objectName is a path (i.e.have several elements separated by '/').If the first
     * element begins with a '~', what follows is assumed to be a workspace id.If not, we assume it 
     * is a name and call the service accordingly.Can be used to put an existing document in a workspace. The name of
     * the document in the workspace is determined by the underlying implementation; this may use metatdata from the 
     * saved document. The name will be included in the returned data, and is guaranteed to be unique in the workspace.
     * 
     * The updateType parameter defaults to CREATE_OR_UPDATE and influences behavior as follows:
     * 
     *  | updateType       | Behavior |
     *  | -----------------|----------|
     *  | CREATE           | Creates a new document link, error if document exists already in workspace |
     *  | UPDATE           | Error, use Put to update a document link |
     *  | CREATE_OR_UPDATE | Create a new document link, do not fail, return name of any existing link to the specified document |
     * 
     * @param repository string identifier of a document repository
     * @param workspacePath string identifier of a workspace or document
     * @param createWorkspace if true, proxy will create any parent workspaces specified in the path
     * @param returnExisting if true, where a link already exists to referenced document, will return existing link
     * @param uriInfo URI of request used to build relative response
     * @param object JSON-format DocumentLink which contains a document reference
     * @param requestContext context (from which we get userMetadata)
     * @return A Response including JSON-format DocumentLink object with the full path of the created object
     */
    @POST
    @Path("/{repository}/{workspace:[^?]+}")
    @Consumes({ MediaType.APPLICATION_JSON })
    public Response post(
            @PathParam("repository") String repository,
            @PathParam("workspace") WorkspacePath workspacePath,
            @QueryParam("createWorkspace") @DefaultValue("false") boolean createWorkspace,
            @QueryParam("returnExisting") @DefaultValue("false") boolean returnExisting,
            @Context UriInfo uriInfo,
            @Context ContainerRequestContext requestContext,
            JsonObject object) {
        LOG.entry(repository, workspacePath, createWorkspace, object);
        try {
            RepositoryService service = repositoryServiceFactory.getService(repository);
            AuthorizationService authorizationService = authorizationServiceFactory.getService(repository);
            JsonObject userMetadata = (JsonObject)requestContext.getProperty("userMetadata");

            if (service == null || authorizationService == null) 
                return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.repositoryNotFound(repository)));

            if (workspacePath == null || workspacePath.isEmpty())
                return LOG.exit(Error.errorResponse(Status.BAD_REQUEST,Error.missingResourcePath()));

            RepositoryObject.Type type = RepositoryObject.Type.valueOf(object.getString("type", RepositoryObject.Type.WORKSPACE.name()));

            Query acl = authorizationService.getObjectACL(workspacePath.rootId, workspacePath.staticPath, type, null, ObjectAccessRole.CREATE);
            if (!acl.containsItem(userMetadata)) {
                return LOG.exit(Error.errorResponse(Status.FORBIDDEN, Error.unauthorized(acl, workspacePath.toString())));
            }

            
            if (type == RepositoryObject.Type.WORKSPACE) {
                return LOG.exit(Error.errorResponse(Status.BAD_REQUEST,Error.badOperation("Can't post a new workspace - use put")));
            } else {
                Reference reference = Reference.fromJson(object.getJsonObject("reference"));
                JsonObject metadata = object.getJsonObject("metadata");
                if (metadata != null && !metadata.isEmpty()) {
                    service.updateDocument(reference.id, null, null, metadata);
                }
                Options.Create.Builder options = Options.Create.EMPTY
                    .addOptionIf(Options.CREATE_MISSING_PARENT, createWorkspace)
                    .addOptionIf(Options.RETURN_EXISTING_LINK_TO_SAME_DOCUMENT, returnExisting);
                DocumentLink link = service.createDocumentLinkAndName(workspacePath.rootId, workspacePath.staticPath, reference, options.build());
                URI created = uriInfo.getAbsolutePathBuilder().path(link.getName().transform(Workspaces::stripBraces).join("/")).build();
                return LOG.exit(Response.created(created).entity(link.toJson(service, navigator, 1, 0)).build());
            }
        } catch (InvalidWorkspace err) {
            LOG.error(err.getMessage());
            return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(err)));
        } catch (InvalidObjectName err) {
            LOG.error(err.getMessage());
            return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(err)));
        } catch (InvalidDocumentId err) {
            LOG.error(err.getMessage());
            return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(err)));
        }catch (InvalidReference err) {
            LOG.error(err.getMessage());
            return LOG.exit(Error.errorResponse(Status.BAD_REQUEST,Error.mapServiceError(err)));
        } catch (InvalidWorkspaceState err) {
            LOG.error(err.getMessage());
            return LOG.exit(Error.errorResponse(Status.FORBIDDEN,Error.mapServiceError(err)));
        } catch (RuntimeException e) {
            LOG.error(e.getMessage());
            return LOG.exit(Error.errorResponse(Status.INTERNAL_SERVER_ERROR,Error.reportException(e)));
        } 
    }

    /** PUT document on path /ws/{repository}/{path}
     * 
     * A path as several elements separated by '/'. If the first
     * element is a '~', what follows is assumed to be a workspace id. If not, we assume it 
     * is a workspace name and call the service accordingly. The final element of the path is in
     * this case the name of the document in the workspace. Note that a document may be filed under
     * different names in several workspaces.
     * 
     * @param repository string identifier of a document repository
     * @param path to document
     * @param newPath to document
     * @param metadata_part metadata for document
     * @param file_part actual document file
     * @param createWorkspace set true if we want to create any workspaces which do not exist, but are specified in the path
     * @param createDocument set true if we want to create a new document if one does not exist on the specified path
     * @param requestContext context (from which we get userMetadata)
     */
    @PUT
    @Path("/{repository}/{workspace:[^?]+}")
    @Consumes({ MediaType.MULTIPART_FORM_DATA })
    public Response putDocument(
        @PathParam("repository") String repository,
        @PathParam("workspace") WorkspacePath path,
        @FormDataParam("metadata") FormDataBodyPart metadata_part,
        @FormDataParam("file") FormDataBodyPart file_part,
        @QueryParam("createWorkspace") @DefaultValue("true") boolean createWorkspace,
        @QueryParam("createDocument") @DefaultValue("true") boolean createDocument,
        @Context ContainerRequestContext requestContext
    ) {
        LOG.entry(repository, path, metadata_part, file_part, createWorkspace, createDocument);
        try {
            RepositoryService service = repositoryServiceFactory.getService(repository);
            AuthorizationService authorizationService = authorizationServiceFactory.getService(repository);
            JsonObject userMetadata = (JsonObject)requestContext.getProperty("userMetadata");

            if (service == null || authorizationService == null) 
                return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.repositoryNotFound(repository)));

            if (path == null || path.isEmpty())
                return LOG.exit(Error.errorResponse(Status.BAD_REQUEST,Error.missingResourcePath()));

            Query acl = authorizationService.getObjectACL(path.rootId, path.staticPath, RepositoryObject.Type.DOCUMENT_LINK, null, ObjectAccessRole.CREATE);
            if (!acl.containsItem(userMetadata)) {
                return LOG.exit(Error.errorResponse(Status.FORBIDDEN, Error.unauthorized(acl, path.toString())));
            }

            MediaType computedMediaType = MediaTypes.getComputedMediaType(file_part.getMediaType(), path.staticPath.part);
            
            Options.Update.Builder options = Options.Update.EMPTY
                .addOptionIf(Options.CREATE_MISSING_PARENT, createWorkspace)
                .addOptionIf(Options.CREATE_MISSING_ITEM, createDocument);

            DocumentLink result = service.updateDocumentLink(
                    path.rootId, 
                    path.staticPath, 
                    computedMediaType.toString(),
                    ()->file_part.getEntityAs(InputStream.class),
                    metadata_part.getEntityAs(JsonObject.class), 
                    options.build()
                    );

            return LOG.exit(Response.accepted().type(MediaType.APPLICATION_JSON).entity(result.toJson()).build());
        } catch (InvalidWorkspace err) {
            return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(err)));
        } catch (InvalidObjectName err) {
            return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(err)));
        } catch (InvalidWorkspaceState err) {
            return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(err)));
        } catch (RuntimeException e) {
            LOG.error(e.getMessage());
            e.printStackTrace(System.err);
            return LOG.exit(Error.errorResponse(Status.INTERNAL_SERVER_ERROR,Error.reportException(e)));
        } 
    }
    
    /** POST document on path /ws/{repository}/{path}
     * 
     * A path as several elements separated by '/'. If the first
     * element is a '~', what follows is assumed to be a workspace id. If not, we assume it 
     * is a workspace name and call the service accordingly. The final element of the path is in
     * this case the name of the document in the workspace. Note that a document may be filed under
     * different names in several workspaces.
     * 
     * @param repository string identifier of a document repository
     * @param path to document
     * @param metadata_part metadata for document
     * @param file_part actual document file
     * @param createWorkspace set true if we want to create any workspaces which do not exist, but are specified in the path
     * @param createDocument set true if we want to create a new document if one does not exist on the specified path
     * @param requestContext context (from which we get userMetadata)
     */
    @POST
    @Path("/{repository}/{workspace:[^?]+}")
    @Consumes({ MediaType.MULTIPART_FORM_DATA })
    public Response postDocument(
        @PathParam("repository") String repository,
        @PathParam("workspace") WorkspacePath path,
        @FormDataParam("metadata") FormDataBodyPart metadata_part,
        @FormDataParam("file") FormDataBodyPart file_part,
        @QueryParam("createWorkspace") @DefaultValue("true") boolean createWorkspace,
        @Context ContainerRequestContext requestContext,
        @Context UriInfo uriInfo
    ) {
        LOG.entry(repository, path, metadata_part, file_part, createWorkspace);
        try {
            RepositoryService service = repositoryServiceFactory.getService(repository);
            AuthorizationService authorizationService = authorizationServiceFactory.getService(repository);
            JsonObject userMetadata = (JsonObject)requestContext.getProperty("userMetadata");

            if (service == null || authorizationService == null) 
                return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.repositoryNotFound(repository)));

            if (path == null || path.isEmpty())
                return LOG.exit(Error.errorResponse(Status.BAD_REQUEST,Error.missingResourcePath()));

            Query acl = authorizationService.getObjectACL(path.rootId, path.staticPath, RepositoryObject.Type.DOCUMENT_LINK, null, ObjectAccessRole.CREATE);
            if (!acl.containsItem(userMetadata)) {
                return LOG.exit(Error.errorResponse(Status.FORBIDDEN, Error.unauthorized(acl, path.toString())));
            }
            
            Options.Create.Builder options = Options.Create.EMPTY.addOptionIf(Options.CREATE_MISSING_PARENT, createWorkspace);


            DocumentLink result = service.createDocumentLinkAndName(
                    path.rootId, 
                    path.staticPath, 
                    file_part.getMediaType().toString(),
                    ()->file_part.getEntityAs(InputStream.class),
                    metadata_part.getEntityAs(JsonObject.class), 
                    options.build()
                );
            
            URI created = uriInfo.getAbsolutePathBuilder().path(result.getName().transform(Workspaces::stripBraces).join("/")).build();


            return LOG.exit(Response.created(created).type(MediaType.APPLICATION_JSON).entity(result.toJson()).build());
        } catch (InvalidWorkspace err) {
            return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(err)));
        } catch (InvalidObjectName err) {
            return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(err)));
        } catch (InvalidWorkspaceState err) {
            return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(err)));
        } catch (RuntimeException e) {
            LOG.error(e.getMessage());
            e.printStackTrace(System.err);
            return LOG.exit(Error.errorResponse(Status.INTERNAL_SERVER_ERROR,Error.reportException(e)));
        } 
    }
    
    /** Delete document from workspace on path /ws/{repository}/{workspace}/{id}
     * 
     * @param repository string identifier of a document repository
     * @param path path to document
     * @param requestContext context (from which we get userMetadata)
     */
    @DELETE
    @Path("/{repository}/{path:[^?]+}")
    public Response deleteDocument(
        @PathParam("repository") String repository,
        @PathParam("path") WorkspacePath path,
        @Context ContainerRequestContext requestContext
    ) {
        LOG.entry(repository, path);
        try {
            RepositoryService service = repositoryServiceFactory.getService(repository);
            AuthorizationService authorizationService = authorizationServiceFactory.getService(repository);
            JsonObject userMetadata = (JsonObject)requestContext.getProperty("userMetadata");


            if (service == null || authorizationService == null) 
                return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.repositoryNotFound(repository)));

            if (path.queryPath != QualifiedName.ROOT)
                return LOG.exit(Error.errorResponse(Status.BAD_REQUEST, Error.badOperation("wildcards not permitted in deleted")));
            
            if (path.documentId != null) {
                Query acl = authorizationService.getObjectACLById(path.rootId, path.staticPath, path.documentId, ObjectAccessRole.DELETE);
                if (acl.containsItem(userMetadata)) {
                    service.deleteDocument(path.rootId, path.staticPath, path.documentId);
                } else {
                    return LOG.exit(Error.errorResponse(Status.FORBIDDEN, Error.unauthorized(acl, path.staticPath, path.documentId)));                
                }
            } else {
                Query acl = authorizationService.getObjectACL(path.rootId, path.staticPath, null, null, ObjectAccessRole.DELETE);
                if (acl.containsItem(userMetadata)) {
                    service.deleteObjectByName(path.rootId, path.staticPath);
                } else {
                    return LOG.exit(Error.errorResponse(Status.FORBIDDEN, Error.unauthorized(acl, path.staticPath)));                                    
                }
            }

            return LOG.exit(Response.status(Status.NO_CONTENT).build());
        } catch (InvalidWorkspace err) {
            return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(err)));
        } catch (InvalidObjectName err) {
            return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(err)));
        } catch (InvalidWorkspaceState err) {
            return LOG.exit(Error.errorResponse(Status.FORBIDDEN,Error.mapServiceError(err)));
        } catch (InvalidDocumentId err) {
            return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(err)));
        } catch (RuntimeException e) {
            LOG.error(e.getMessage());
            e.printStackTrace(System.err);
            return LOG.exit(Error.errorResponse(Status.INTERNAL_SERVER_ERROR,Error.reportException(e)));
        }
    }    
}
