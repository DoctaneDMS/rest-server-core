package com.softwareplumbers.dms.rest.server.core;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
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

import com.softwareplumbers.dms.rest.server.model.Document;
import com.softwareplumbers.dms.rest.server.model.DocumentLink;
import com.softwareplumbers.dms.rest.server.model.NamedRepositoryObject;
import com.softwareplumbers.dms.rest.server.model.Reference;
import com.softwareplumbers.dms.rest.server.model.RepositoryObject;
import com.softwareplumbers.dms.rest.server.model.RepositoryService;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidObjectName;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidReference;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspace;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspaceState;
import com.softwareplumbers.dms.rest.server.model.UpdateType;
import com.softwareplumbers.dms.rest.server.util.Log;
import com.softwareplumbers.dms.rest.server.model.Workspace;
import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractquery.Query;
import com.softwareplumbers.dms.rest.server.model.AuthorizationService;
import com.softwareplumbers.dms.rest.server.model.AuthorizationService.ObjectAccessRole;

import static com.softwareplumbers.dms.rest.server.model.Constants.*;
import com.softwareplumbers.dms.rest.server.model.DocumentNavigatorService;
import com.softwareplumbers.dms.rest.server.model.DocumentNavigatorService.DocumentFormatException;
import com.softwareplumbers.dms.rest.server.model.DocumentNavigatorService.PartNotFoundException;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidDocumentId;
import com.softwareplumbers.dms.rest.server.model.StreamableRepositoryObject;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import javax.ws.rs.POST;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.UriInfo;


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

    private static Log LOG = new Log(Workspaces.class);
    
    private static List<MediaType> GET_RESULT_TYPES = Arrays.asList(
        MediaType.WILDCARD_TYPE,
        MediaType.MULTIPART_FORM_DATA_TYPE, 
        MediaType.APPLICATION_XHTML_XML_TYPE, 
        MediaType.APPLICATION_JSON_TYPE
    );

    ///////////---------  member variables --------////////////

    private RepositoryServiceFactory repositoryServiceFactory;
    private DocumentNavigatorService navigator;
    private AuthorizationServiceFactory authorizationServiceFactory;

    
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
    
    @Autowired
    public void setDocumentNavigatorService(DocumentNavigatorService documentNavigatorService) {
        this.navigator = documentNavigatorService;
    }
    
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
     * @param workspacePath string identifier of a workspace or document
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
        LOG.logEntering("get", repository, workspacePath, filter, contentType, headers.getAcceptableMediaTypes());
        
        try {
            
            Query filterConstraint = filter != null && filter.length() > 0 ? Query.urlDecode(filter) : Query.UNBOUNDED;
            LOG.log.fine(()->String.format("Decoded filter: " + filterConstraint));
            RepositoryService service = repositoryServiceFactory.getService(repository);
            AuthorizationService authorizationService = authorizationServiceFactory.getService(repository);
            JsonObject userMetadata = (JsonObject)requestContext.getProperty("userMetadata");

            if (service == null || authorizationService == null) 
                return Response.status(Status.NOT_FOUND).type(MediaType.APPLICATION_JSON).entity(Error.repositoryNotFound(repository)).build();

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
                LOG.log.finest(()->responseObj.toString());
                return LOG.logResponse("get", Response.ok().type(MediaType.APPLICATION_JSON).entity(responseObj).build());
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
                        return LOG.logResponse("get", Error.errorResponse(Status.FORBIDDEN, Error.unauthorized(acl, result)));
                    }
                    switch (result.getType()) {
                        case WORKSPACE:
                            if (workspacePath.staticPartPath.isEmpty()) {
                        		return LOG.logResponse("get", Response.ok().type(MediaType.APPLICATION_JSON).entity(result.toJson(service,navigator,1,0)).build());                    		
                            } else {
                                return LOG.logResponse("get", Error.errorResponse(Status.BAD_REQUEST, Error.badOperation("Workspaces do not have document parts")));
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
                                file.setMediaType(document.getMediaType());
                                file.getHeaders().add("Content-Length", Long.toString(document.getLength()));
                                file.setEntity(new DocumentOutput(document));
                                MultiPart response = new MultiPart()
                                    .bodyPart(metadata)
                                    .bodyPart(file);                                   
                                return LOG.logResponse("get", Response.ok(response, MultiPartMediaTypes.MULTIPART_MIXED_TYPE).build());                                	
                            } else if (requestedMediaType == MediaType.APPLICATION_XHTML_XML_TYPE) {
                                Document document = (Document)result;
                                return LOG.logResponse("get", Response.ok()
                                    .type(MediaType.APPLICATION_XHTML_XML_TYPE)
                                    .entity(new XMLOutput(document)).build());                                	
                            } else if (requestedMediaType == MediaType.APPLICATION_JSON_TYPE) {
                                return LOG.logResponse("get", Response.ok().type(MediaType.APPLICATION_JSON).entity(result.toJson(service, navigator, 1, 0)).build());
                            } else {
                                Document document = (Document)result;
                                return LOG.logResponse("get", Response.ok()
                                    .header("content-disposition", "attachment; filename=" + URLEncoder.encode(documentName, StandardCharsets.UTF_8.name()))
                                    .type(document.getMediaType())
                                    .entity(new DocumentOutput(document)).build());
                            }
                        default:
                            throw new RuntimeException("Unknown result type:" + result.getType());
                    	}
                } else {
                    return LOG.logResponse("get", 
                        Response.status(Status.NOT_FOUND).type(MediaType.APPLICATION_JSON).entity(Error.objectNotFound(repository, workspacePath)).build());
                }
            }

        } catch (InvalidWorkspace err) {
            return LOG.logResponse("get", Error.errorResponse(Status.NOT_FOUND, Error.mapServiceError(err)));
        } catch (InvalidDocumentId err) {
            return LOG.logResponse("get", Error.errorResponse(Status.NOT_FOUND, Error.mapServiceError(err)));
        } catch (UnsupportedEncodingException err) {
            return LOG.logResponse("get", Error.errorResponse(Status.INTERNAL_SERVER_ERROR, Error.reportException(err)));
        } catch (InvalidObjectName err) {
            return LOG.logResponse("get", Error.errorResponse(Status.NOT_FOUND, Error.mapServiceError(err)));
        } catch (InvalidContentType err) {
            return LOG.logResponse("get", Error.errorResponse(Status.BAD_REQUEST, Error.mapServiceError(err)));
        } catch (DocumentFormatException err) {
            return LOG.logResponse("get", Error.errorResponse(Status.BAD_REQUEST, Error.mapServiceError(err)));
        } catch (PartNotFoundException err) {
            return LOG.logResponse("get", Error.errorResponse(Status.NOT_FOUND, Error.mapServiceError(err)));
        }catch (Throwable e) {
            LOG.log.severe(e.getMessage());
            e.printStackTrace(System.err);
            return LOG.logResponse("get", Error.errorResponse(Status.INTERNAL_SERVER_ERROR, Error.reportException(e)));
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
        LOG.logEntering("getWorkspaces", repository);
        try {
            RepositoryService service = repositoryServiceFactory.getService(repository);
            AuthorizationService authorizationService = authorizationServiceFactory.getService(repository);
            JsonObject userMetadata = (JsonObject)requestContext.getProperty("userMetadata");

            if (service == null || authorizationService == null) 
                return LOG.logResponse("getWorkspaces", Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build());

            Query accessConstraint = authorizationService.getAccessConstraint(userMetadata, null, QualifiedName.ROOT);

            JsonArrayBuilder result = Json.createArrayBuilder();
            service.listWorkspaces(documentId, null, accessConstraint)
                .map(DocumentLink::toJson)
                .forEach(value -> result.add(value));
            
            //TODO: must be able to do this in a stream somehow.
            return LOG.logResponse("getWorkspaces", Response.ok().type(MediaType.APPLICATION_JSON).entity(result.build()).build());
        } catch (Throwable e) {
            LOG.log.severe(e.getMessage());
            e.printStackTrace(System.err);
            return LOG.logResponse("getWorkspaces", Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build());
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
            @PathParam("workspace") String objectName,
            @QueryParam("createWorkspace") @DefaultValue("false") boolean createWorkspace,
            @QueryParam("updateType") @DefaultValue("CREATE_OR_UPDATE") UpdateType updateType,
            @Context ContainerRequestContext requestContext,
            JsonObject object) {
        LOG.logEntering("put", repository, objectName, createWorkspace, updateType, object);
        try {
            RepositoryService service = repositoryServiceFactory.getService(repository);
            AuthorizationService authorizationService = authorizationServiceFactory.getService(repository);
            JsonObject userMetadata = (JsonObject)requestContext.getProperty("userMetadata");

            if (service == null || authorizationService == null) 
                return LOG.logResponse("put", Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build());

            if (objectName == null || objectName.isEmpty())
                return LOG.logResponse("put", Response.status(Status.BAD_REQUEST).entity(Error.missingResourcePath()).build());


            QualifiedName wsName = QualifiedName.parse(objectName, "/");
            String rootId = ROOT_ID;
            String firstPart = wsName.get(0);

            // If the qualified name starts with a '~', the next element is an Id which we will use for the root repositor
            if (firstPart.startsWith("~")) {
                rootId = firstPart.substring(1);
                wsName = wsName.rightFromStart(1);
            } 

            RepositoryObject.Type type = RepositoryObject.Type.valueOf(object.getString("type", RepositoryObject.Type.WORKSPACE.name()));

            Query acl = authorizationService.getObjectACL(rootId, wsName, getRequiredRole(updateType));
                
            if (!acl.containsItem(userMetadata)) {
                return LOG.logResponse("put", Error.errorResponse(Status.FORBIDDEN, Error.unauthorized(acl, wsName)));
            }
            
            if (type == RepositoryObject.Type.WORKSPACE) {

                String updateName = object.getString("name",null);
                QualifiedName updateQName = updateName == null ? null : QualifiedName.parse(updateName, "/");
                String stateString = object.getString("state", null);
                Workspace.State state = stateString == null ? null : Workspace.State.valueOf(stateString);
                JsonObject metadata = object.getJsonObject("metadata");
    
                String wsId;

                if (updateType == UpdateType.CREATE) {
                    wsId = service.createWorkspaceByName(rootId, wsName, state, metadata);
                } else {
                    wsId = service.updateWorkspaceByName(rootId, wsName, updateQName, state, metadata, updateType == UpdateType.CREATE_OR_UPDATE);
                }
                
                JsonObjectBuilder result = Json.createObjectBuilder();
                result.add("id", wsId);
                return LOG.logResponse("put", Response.accepted().type(MediaType.APPLICATION_JSON).entity(result.build()).build());    

            } else {

                Reference reference = Reference.fromJson(object.getJsonObject("reference"));                
                JsonObject metadata = object.getJsonObject("metadata");
                if (metadata != null && !metadata.isEmpty()) {
                    service.updateDocument(reference.id, null, null, metadata, null, false);
                }
                
                if (updateType == UpdateType.CREATE)
                    service.createDocumentLinkByName(rootId, wsName, reference, createWorkspace);
                else
                    service.updateDocumentLinkByName(rootId, wsName, reference, createWorkspace, updateType == UpdateType.CREATE_OR_UPDATE);
                
                return LOG.logResponse("put", Response.accepted().build());
            }
        } catch (InvalidWorkspace err) {
            LOG.log.severe(err.getMessage());
            return LOG.logResponse("put", Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(err)).build());
        } catch (InvalidDocumentId err) {
            LOG.log.severe(err.getMessage());
            return LOG.logResponse("put", Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(err)).build());
        } catch (InvalidObjectName err) {
            LOG.log.severe(err.getMessage());
            return LOG.logResponse("put", Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(err)).build());
        } catch (InvalidReference err) {
            LOG.log.severe(err.getMessage());
            return LOG.logResponse("put", Response.status(Status.BAD_REQUEST).entity(Error.mapServiceError(err)).build());
        } catch (InvalidWorkspaceState err) {
            LOG.log.severe(err.getMessage());
            return LOG.logResponse("put", Response.status(Status.FORBIDDEN).entity(Error.mapServiceError(err)).build());
        } catch (RuntimeException e) {
            LOG.log.severe(e.getMessage());
            return LOG.logResponse("put", Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build());
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
     * @param objectName string identifier of a workspace or document
     * @param createWorkspace if true, proxy will create any parent workspaces specified in the path
     * @param updateType controls update behavior on actual object referenced by objectName
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
            @PathParam("workspace") String objectName,
            @QueryParam("createWorkspace") @DefaultValue("false") boolean createWorkspace,
            @QueryParam("updateType") @DefaultValue("CREATE_OR_UPDATE") UpdateType updateType,
            @Context UriInfo uriInfo,
            @Context ContainerRequestContext requestContext,
            JsonObject object) {
        LOG.logEntering("post", repository, objectName, createWorkspace, updateType, object);
        try {
            RepositoryService service = repositoryServiceFactory.getService(repository);
            AuthorizationService authorizationService = authorizationServiceFactory.getService(repository);
            JsonObject userMetadata = (JsonObject)requestContext.getProperty("userMetadata");

            if (service == null || authorizationService == null) 
                return LOG.logResponse("post", Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build());

            if (objectName == null || objectName.isEmpty())
                return LOG.logResponse("post", Response.status(Status.BAD_REQUEST).entity(Error.missingResourcePath()).build());


            QualifiedName wsName = QualifiedName.parse(objectName, "/");
            String rootId = ROOT_ID;
            String firstPart = wsName.get(0);

            // If the qualified name starts with a '~', the next element is an Id which we will use for the root repositor
            if (firstPart.startsWith("~")) {
                rootId = firstPart.substring(1);
                wsName = wsName.rightFromStart(1);
            } 
            
            Query acl = authorizationService.getObjectACL(rootId, wsName, ObjectAccessRole.CREATE);
            if (!acl.containsItem(userMetadata)) {
                return LOG.logResponse("post", Error.errorResponse(Status.FORBIDDEN, Error.unauthorized(acl, wsName)));
            }

            RepositoryObject.Type type = RepositoryObject.Type.valueOf(object.getString("type", RepositoryObject.Type.WORKSPACE.name()));
            
            if (type == RepositoryObject.Type.WORKSPACE) {
                return LOG.logResponse("post", Response.status(Status.BAD_REQUEST).entity(Error.badOperation("Can't post a new workspace - use put")).build());
            } else {
                Reference reference = Reference.fromJson(object.getJsonObject("reference"));
                JsonObject metadata = object.getJsonObject("metadata");
                if (metadata != null && !metadata.isEmpty()) {
                    service.updateDocument(reference.id, null, null, metadata, null, false);
                }
                DocumentLink link = service.createDocumentLink(rootId, wsName, reference, createWorkspace, updateType == UpdateType.CREATE_OR_UPDATE);
                URI created = uriInfo.getAbsolutePathBuilder().path(link.getName().join("/")).build();
                return LOG.logResponse("post", Response.created(created).entity(link.toJson(service, navigator, 1, 0)).build());
            }
        } catch (InvalidWorkspace err) {
            LOG.log.severe(err.getMessage());
            return LOG.logResponse("put", Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(err)).build());
        } catch (InvalidObjectName err) {
            LOG.log.severe(err.getMessage());
            return LOG.logResponse("put", Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(err)).build());
        } catch (InvalidDocumentId err) {
            LOG.log.severe(err.getMessage());
            return LOG.logResponse("put", Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(err)).build());
        }catch (InvalidReference err) {
            LOG.log.severe(err.getMessage());
            return LOG.logResponse("put", Response.status(Status.BAD_REQUEST).entity(Error.mapServiceError(err)).build());
        } catch (InvalidWorkspaceState err) {
            LOG.log.severe(err.getMessage());
            return LOG.logResponse("put", Response.status(Status.FORBIDDEN).entity(Error.mapServiceError(err)).build());
        } catch (RuntimeException e) {
            LOG.log.severe(e.getMessage());
            return LOG.logResponse("put", Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build());
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
        @PathParam("workspace") String path,
        @FormDataParam("metadata") FormDataBodyPart metadata_part,
        @FormDataParam("file") FormDataBodyPart file_part,
        @QueryParam("createWorkspace") @DefaultValue("true") boolean createWorkspace,
        @QueryParam("createDocument") @DefaultValue("true") boolean createDocument,
        @Context ContainerRequestContext requestContext
    ) {
        LOG.logEntering("putDocument", repository, path, metadata_part, Log.fmt(file_part), createWorkspace, createDocument);
        try {
            RepositoryService service = repositoryServiceFactory.getService(repository);
            AuthorizationService authorizationService = authorizationServiceFactory.getService(repository);
            JsonObject userMetadata = (JsonObject)requestContext.getProperty("userMetadata");

            if (service == null || authorizationService == null) 
                return LOG.logResponse("putDocument", Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build());

            QualifiedName pathName = QualifiedName.parse(path, "/");

            if (pathName == null || pathName.isEmpty())
                return LOG.logResponse("putDocument", Response.status(Status.BAD_REQUEST).entity(Error.missingResourcePath()).build());

            String wsId = null;
            String firstPart = pathName.get(0);
            // If the qualified name starts with a '~', the next element is an Id which we will use for the root repositor
            if (firstPart.startsWith("~")) {
                wsId = firstPart.substring(1);
                pathName = pathName.rightFromStart(1);
            } 

            Query acl = authorizationService.getObjectACL(wsId, pathName, ObjectAccessRole.CREATE);
            if (!acl.containsItem(userMetadata)) {
                return LOG.logResponse("post", Error.errorResponse(Status.FORBIDDEN, Error.unauthorized(acl, pathName)));
            }

            MediaType computedMediaType = MediaTypes.getComputedMediaType(file_part.getMediaType(), pathName.part);

            Reference result = service.updateDocumentByName(
                    wsId, 
                    pathName, 
                    computedMediaType,
                    ()->file_part.getEntityAs(InputStream.class),
                    metadata_part.getEntityAs(JsonObject.class), 
                    createWorkspace, 
                    createDocument
                    );

            return LOG.logResponse("putDocument", Response.accepted().type(MediaType.APPLICATION_JSON).entity(result.toJson()).build());
        } catch (InvalidWorkspace err) {
            return LOG.logResponse("putDocument", Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(err)).build());
        } catch (InvalidObjectName err) {
            return LOG.logResponse("putDocument", Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(err)).build());
        } catch (InvalidWorkspaceState err) {
            return LOG.logResponse("putDocument", Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(err)).build());
        } catch (Throwable e) {
            LOG.log.severe(e.getMessage());
            e.printStackTrace(System.err);
            return LOG.logResponse("putDocument", Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build());
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
        LOG.logEntering("deleteDocument", repository, path);
        try {
            RepositoryService service = repositoryServiceFactory.getService(repository);
            AuthorizationService authorizationService = authorizationServiceFactory.getService(repository);
            JsonObject userMetadata = (JsonObject)requestContext.getProperty("userMetadata");


            if (service == null || authorizationService == null) 
                return LOG.logResponse("deleteDocument", Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build());

            if (path.queryPath != QualifiedName.ROOT)
                return LOG.logResponse("deleteDocument", Error.errorResponse(Status.BAD_REQUEST, Error.badOperation("wildcards not permitted in deleted")));
            
            if (path.documentId != null) {
                Query acl = authorizationService.getObjectACL(path.rootId, path.staticPath, path.documentId, ObjectAccessRole.DELETE);
                if (acl.containsItem(userMetadata)) {
                    service.deleteDocument(path.rootId, path.staticPath, path.documentId);
                } else {
                    return LOG.logResponse("deleteDocument", Error.errorResponse(Status.FORBIDDEN, Error.unauthorized(acl, path.staticPath, path.documentId)));                
                }
            } else {
                Query acl = authorizationService.getObjectACL(path.rootId, path.staticPath, ObjectAccessRole.DELETE);
                if (acl.containsItem(userMetadata)) {
                    service.deleteObjectByName(path.rootId, path.staticPath);
                } else {
                    return LOG.logResponse("deleteDocument", Error.errorResponse(Status.FORBIDDEN, Error.unauthorized(acl, path.staticPath)));                                    
                }
            }

            return LOG.logResponse("deleteDocument", Response.status(Status.NO_CONTENT).build());
        } catch (InvalidWorkspace err) {
            return LOG.logResponse("deleteDocument", Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(err)).build());
        } catch (InvalidObjectName err) {
            return LOG.logResponse("deleteDocument", Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(err)).build());
        } catch (InvalidWorkspaceState err) {
            return LOG.logResponse("deleteDocument", Response.status(Status.FORBIDDEN).entity(Error.mapServiceError(err)).build());
        } catch (InvalidDocumentId err) {
            return LOG.logResponse("deleteDocument", Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(err)).build());
        } catch (Throwable e) {
            LOG.log.severe(e.getMessage());
            e.printStackTrace(System.err);
            return LOG.logResponse("deleteDocument", Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build());
        }
    }    
}