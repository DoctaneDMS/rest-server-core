package com.softwareplumbers.dms.rest.server.core;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
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
import com.softwareplumbers.common.abstractquery.ObjectConstraint;
import com.softwareplumbers.common.abstractquery.Range;
import com.softwareplumbers.common.abstractquery.Value;

import static com.softwareplumbers.dms.rest.server.model.Constants.*;


/** Handle catalog operations on repositories and documents.
 * 
 * operations on a workspace all performed 
 * via this interface under the /ws/{repository}/{workspace} path.
 * 
 * @author Jonathan Essex
 *
 */
@Component
@Path("/ws")
public class Workspaces {
    ///////////--------- Static member variables --------////////////

    private static Log LOG = new Log(Workspaces.class);

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

    /** GET workspace state on path /ws/{repository}/{workspace}
     * 
     * Retrieves information about the given workspace. The workspace may be a path (i.e.
     * have several elements separated by '/'). If the first element is a '~', what follows
     * is assumed to be a workspace id. If not, we assume it is a name and query the service
     * accordingly.
     * 
     * @param repository string identifier of a document repository
     * @param workspaceName string identifier of a workspace
     * @param filter AbstractQuery filter (in Base64 encoded Json) to fine down results of wildcard searches
     * @return Information about the workspace or file in json format, or the file itself, depending on the requested content type
     */
    @GET
    @Path("/{repository}/{workspace:.+}")
    public Response get(
        @PathParam("repository") String repository,
        @PathParam("workspace") String workspaceName,
        @QueryParam("filter") String filter,
        @Context HttpHeaders headers
    ) {
        LOG.logEntering("get", repository, workspaceName, filter, headers.getAcceptableMediaTypes());

        try {
            ObjectConstraint filterConstraint = filter != null && filter.length() > 0 ? ObjectConstraint.urlDecode(filter) : ObjectConstraint.UNBOUNDED;
            RepositoryService service = repositoryServiceFactory.getService(repository);

            if (service == null) 
                return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();

            QualifiedName wsName = QualifiedName.parse(workspaceName, "/");
            String rootId = ROOT_ID;

            // If the qualified name starts with a '~', the next element is an Id which we will use for the root repository
            if (wsName.startsWith(QualifiedName.of("~"))) {
                rootId = wsName.get(1);
                wsName = wsName.rightFromStart(2);
            } 
            
            if (wsName.size() > 1 && wsName.right(2).startsWith(QualifiedName.of("!"))) {
                String documentId = wsName.part;
                wsName = wsName.parent.add("*");
                filterConstraint = ObjectConstraint.from("Id", Range.equals(Value.from(documentId)));
            }

            if (wsName.indexFromEnd(part->part.contains("*") || part.contains("?")) >= 0) {
                JsonArrayBuilder results = Json.createArrayBuilder();
                service.catalogueByName(rootId, wsName, filterConstraint, false)
                .forEach(item -> results.add(item.toJson()));;
                return Response.ok().type(MediaType.APPLICATION_JSON).entity(results.build()).build();
            } else {
                RepositoryObject result = service.getObjectByName(rootId, wsName);
                if (result != null) {    					
                    if (headers.getAcceptableMediaTypes().contains(MediaType.MULTIPART_FORM_DATA_TYPE) && result.getType() != RepositoryObject.Type.WORKSPACE) {
                        Document document = (Document)result;
                        FormDataBodyPart metadata = new FormDataBodyPart();
                        metadata.setName("metadata");
                        metadata.setMediaType(MediaType.APPLICATION_JSON_TYPE);
                        metadata.setEntity(document.toJson());
                        FormDataBodyPart file = new FormDataBodyPart();
                        file.setName("file");
                        file.setMediaType(document.getMediaType());
                        file.getHeaders().add("Content-Length", Long.toString(document.getLength()));
                        file.setEntity(new DocumentOutput(document));

                        MultiPart response = new MultiPart()
                            .bodyPart(metadata)
                            .bodyPart(file);

                        return Response.ok(response, MultiPartMediaTypes.MULTIPART_MIXED_TYPE).build();
                    } else if (headers.getAcceptableMediaTypes().contains(MediaType.APPLICATION_JSON_TYPE)) {
                        return Response.ok().type(MediaType.APPLICATION_JSON).entity(result.toJson()).build();
                    } else {
                        DocumentLink document = (DocumentLink)result;
                        return Response.ok()
                            .header("content-disposition", "attachment; filename=" + URLEncoder.encode(document.getName().part, StandardCharsets.UTF_8.name()))
                            .type(document.getMediaType())
                            .entity(new DocumentOutput(document)).build();
                    }
                } else {
                    return Response.status(Status.NOT_FOUND).entity(Error.objectNotFound(repository, wsName)).build();
                }
            }

        } catch (InvalidWorkspace err) {
            return Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(err)).build();
        } catch (Throwable e) {
            LOG.log.severe(e.getMessage());
            e.printStackTrace(System.err);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
        }
    }



    /** GET workspaces that a given document belongs to state on path /ws/{repository}
     * 
     * Retrieves information about the workspaces a document belongs to 
     * 
     * @param repository string identifier of a document repository
     * @param documentId string identifier of a document
     * @return Information about the workspaces in json format
     */
    @GET
    @Path("/{repository}")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getWorkspaces(
        @PathParam("repository") String repository,
        @QueryParam("id") String documentId
    ) {
        LOG.logEntering("get", repository);
        try {
            RepositoryService service = repositoryServiceFactory.getService(repository);

            if (service == null) 
                return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();

            JsonArrayBuilder result = Json.createArrayBuilder();
            service.listWorkspaces(documentId, null).map(DocumentLink::toJson).forEach(value -> result.add(value));
            //TODO: must be able to do this in a stream somehow.
            return Response.ok().type(MediaType.APPLICATION_JSON).entity(result.build()).build();
        } catch (Throwable e) {
            LOG.log.severe(e.getMessage());
            e.printStackTrace(System.err);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
        }
    }

    /** PUT workspace state on path /ws/{repository}/{workspace}
     * 
     * The workspace may be a path (i.e.have several elements separated by '/'). If the first
     * element is a '~', what follows is assumed to be a workspace id. If not, we assume it 
     * is a name and call the service accordingly.
     * 
     * Can be used to modify workspace state (e.g. Closing or Finalizing a workspace),
     * and to create a new workspace.
     * 
     * @param repository string identifier of a document repository
     * @param objectName string identifier of a workspace
     * @param createWorkspace string identifier of a workspace
     */
    @PUT
    @Path("/{repository}/{workspace:.+}")
    @Consumes({ MediaType.APPLICATION_JSON })
    public Response put(
            @PathParam("repository") String repository,
            @PathParam("workspace") String objectName,
            @QueryParam("createWorkspace") @DefaultValue("false") boolean createWorkspace,
            @QueryParam("updateType") @DefaultValue("CREATE_OR_UPDATE") UpdateType updateType,
            JsonObject object) {
        LOG.logEntering("put", repository, object, updateType);
        try {
            RepositoryService service = repositoryServiceFactory.getService(repository);

            if (service == null) 
                return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();

            if (objectName == null || objectName.isEmpty())
                return Response.status(Status.BAD_REQUEST).entity(Error.missingResourcePath()).build();

            QualifiedName wsName = QualifiedName.parse(objectName, "/");
            String rootId = ROOT_ID;

            if (wsName.startsWith(QualifiedName.of("~"))) {             
                rootId = wsName.get(1);
                wsName = wsName.rightFromStart(2);
            } 

            RepositoryObject.Type type = RepositoryObject.Type.valueOf(object.getString("type", RepositoryObject.Type.WORKSPACE.name()));
            
            if (type == RepositoryObject.Type.WORKSPACE) {
                String updateName = object.getString("name",null);
                QualifiedName updateQName = updateName == null ? null : QualifiedName.parse(updateName, "/");
                String stateString = object.getString("state", null);
                Workspace.State state = stateString == null ? null : Workspace.State.valueOf(stateString);
                JsonObject metadata = object.getJsonObject("metadata");
    
                String wsId;
                
                if (updateType == UpdateType.CREATE)
                    wsId = service.createWorkspaceByName(rootId, wsName, state, metadata);
                else 
                    wsId = service.updateWorkspaceByName(rootId, wsName, updateQName, state, metadata, updateType == UpdateType.CREATE_OR_UPDATE);
    
                JsonObjectBuilder result = Json.createObjectBuilder();
                result.add("id", wsId);
    
                return Response.accepted().type(MediaType.APPLICATION_JSON).entity(result.build()).build();
            } else {
                Reference reference = Reference.fromJson(object.getJsonObject("reference"));
                
                if (updateType == UpdateType.CREATE)
                    service.createDocumentLinkByName(rootId, wsName, reference, createWorkspace);
                else
                    service.updateDocumentLinkByName(rootId, wsName, reference, updateType == UpdateType.CREATE_OR_UPDATE);
                
                return Response.accepted().build();
            }
        } catch (InvalidWorkspace err) {
            LOG.log.severe(err.getMessage());
            return Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(err)).build();
        } catch (InvalidObjectName err) {
            LOG.log.severe(err.getMessage());
            return Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(err)).build();
        } catch (InvalidReference err) {
            LOG.log.severe(err.getMessage());
            return Response.status(Status.BAD_REQUEST).entity(Error.mapServiceError(err)).build();
        } catch (InvalidWorkspaceState err) {
            LOG.log.severe(err.getMessage());
            return Response.status(Status.FORBIDDEN).entity(Error.mapServiceError(err)).build();
        } catch (RuntimeException e) {
            LOG.log.severe(e.getMessage());
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
        } 
    }

    /** PUT document on path /ws/{repository}/{workspace}/{documentName}
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
     */
    @PUT
    @Path("/{repository}/{workspace:.+}")
    @Consumes({ MediaType.MULTIPART_FORM_DATA })
    public Response putDocument(
        @PathParam("repository") String repository,
        @PathParam("workspace") String path,
        @FormDataParam("metadata") FormDataBodyPart metadata_part,
        @FormDataParam("file") FormDataBodyPart file_part,
        @QueryParam("createWorkspace") @DefaultValue("true") boolean createWorkspace,
        @QueryParam("createDocument") @DefaultValue("true") boolean createDocument
    ) {
        LOG.logEntering("putDocument", repository, path, metadata_part, Log.fmt(file_part), createWorkspace, createDocument);
        try {
            RepositoryService service = repositoryServiceFactory.getService(repository);

            if (service == null) 
                return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();

            QualifiedName pathName = QualifiedName.parse(path, "/");

            if (pathName == null || pathName.isEmpty())
                return Response.status(Status.BAD_REQUEST).entity(Error.missingResourcePath()).build();

            String wsId = null;
            if (pathName.startsWith(QualifiedName.of("~"))) {  
                wsId = pathName.get(1);
                pathName = pathName.rightFromStart(2);
            }

            Reference result = service.updateDocumentByName(
                    wsId, 
                    pathName, 
                    file_part.getMediaType(),
                    ()->file_part.getEntityAs(InputStream.class),
                    metadata_part.getEntityAs(JsonObject.class), 
                    createWorkspace, 
                    createDocument
                    );

            return Response.accepted().type(MediaType.APPLICATION_JSON).entity(result.toJson()).build();
        } catch (InvalidWorkspace err) {
            return Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(err)).build();
        } catch (InvalidObjectName err) {
            return Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(err)).build();
        } catch (InvalidWorkspaceState err) {
            return Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(err)).build();
        } catch (Throwable e) {
            LOG.log.severe(e.getMessage());
            e.printStackTrace(System.err);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
        } 
    }
    
    /** Delete document from workspace on path /ws/{repository}/{workspace}/{id}
     * 
     * @param repository string identifier of a document repository
     * @param path path to document
     */
    @DELETE
    @Path("/{repository}/{path:.+}")
    public Response deleteDocument(
        @PathParam("repository") String repository,
        @PathParam("path") String path
    ) {
        LOG.logEntering("deleteDocument", repository, path);
        try {
            RepositoryService service = repositoryServiceFactory.getService(repository);

            if (service == null) 
                return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();

            QualifiedName wsName = QualifiedName.parse(path, "/");
            String rootId = ROOT_ID;

            if (wsName.startsWith(QualifiedName.of("~"))) {    			
                rootId = wsName.get(1);
                wsName = wsName.rightFromStart(2);
            }

            service.deleteObjectByName(rootId, wsName);

            return Response.status(Status.NO_CONTENT).build();
        } catch (InvalidWorkspace err) {
            return Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(err)).build();
        } catch (InvalidObjectName err) {
            return Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(err)).build();
        } catch (InvalidWorkspaceState err) {
            return Response.status(Status.FORBIDDEN).entity(Error.mapServiceError(err)).build();
        } catch (Throwable e) {
            LOG.log.severe(e.getMessage());
            e.printStackTrace(System.err);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
        }
    }    
}