package com.softwareplumbers.dms.rest.server.core;

import java.util.logging.Logger;
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
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.softwareplumbers.dms.rest.server.model.RepositoryService;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidDocumentId;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspace;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspaceState;
import com.softwareplumbers.dms.rest.server.model.Workspace;
import com.softwareplumbers.common.QualifiedName;

/** Handle catalog operations on repositories and documents.
 * 
 * operations on a workspace all performed 
 * via this interface under the /workspace/{repository}/{workspace} path.
 * 
 * @author Jonathan Essex
 *
 */
@Component
@Path("/ws")
public class Workspaces {
	///////////--------- Static member variables --------////////////

	private static Logger LOG = Logger.getLogger("workspace");

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
     * @return Information about the workspace in json format
     */
    @GET
    @Path("/{repository}/{workspace: [^&?]+}")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response get(
    	@PathParam("repository") String repository,
    	@PathParam("workspace") String workspaceName) {
    	try {
    			RepositoryService service = repositoryServiceFactory.getService(repository);

    			if (service == null) 
    				return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();
    		
    			QualifiedName wsName = QualifiedName.ROOT.parse(workspaceName, "/").reverse();
    			
    			Workspace workspace = null;
    			if (wsName.part.equals("~")) { 
    				workspace = service.getWorkspaceById(wsName.parent.part);
    			} else {
    				workspace = service.getWorkspaceByName(workspaceName);
    			}
    			
    			//TODO: must be able to do this in a stream somehow.
    			return Response.ok().type(MediaType.APPLICATION_JSON).entity(workspace.toJson()).build();
    	} catch (InvalidWorkspace err) {
    		return Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(err)).build();
    	} catch (Throwable e) {
    		LOG.severe(e.getMessage());
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
    	@QueryParam("id") String documentId) {
    	try {
    			RepositoryService service = repositoryServiceFactory.getService(repository);

    			if (service == null) 
    				return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();
    		
    			JsonArrayBuilder result = Json.createArrayBuilder();
    			service.listWorkspaces(documentId).map(Workspace::toJson).forEach(value -> result.add(value));
    			//TODO: must be able to do this in a stream somehow.
    			return Response.ok().type(MediaType.APPLICATION_JSON).entity(result.build()).build();
    	} catch (Throwable e) {
    		LOG.severe(e.getMessage());
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
     * @param workspaceId string identifier of a workspace
     * @param workspaceName string identifier of a workspace
     * @param createWorkspace string identifier of a workspace
     */
    @PUT
    @Path("/{repository}/{workspace: [^&?]+}")
    @Consumes({ MediaType.APPLICATION_JSON })
    public Response put(
    	@PathParam("repository") String repository,
    	@PathParam("workspace") String workspaceName,
    	@QueryParam("createWorkspace") @DefaultValue("false") boolean createWorkspace,
    	JsonObject workspace) {
    	try {
    			RepositoryService service = repositoryServiceFactory.getService(repository);

    			if (service == null) 
    				return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();

    			String updateName = workspace.getString("name");
    			String stateString = workspace.getString("state");
    			Workspace.State state = stateString == null ? null : Workspace.State.valueOf(stateString);
    			
    			QualifiedName wsName = QualifiedName.ROOT.parse(workspaceName, "/").reverse();
    			
    			String wsId = null;
    			if (wsName.part.equals("~")) {    			
    				wsId = service.updateWorkspaceById(wsName.parent.part, workspaceName, state, createWorkspace);
    			} else {
    				wsId = service.updateWorkspaceByName(workspaceName, updateName, state, createWorkspace);
    			}
    			
    			JsonObjectBuilder result = Json.createObjectBuilder();
    			result.add("id", wsId);
    			
    			return Response.accepted().type(MediaType.APPLICATION_JSON).entity(result.build()).build();
    	} catch (InvalidWorkspace err) {
    		return Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(err)).build();
    	} catch (Throwable e) {
    		LOG.severe(e.getMessage());
    		e.printStackTrace(System.err);
    		return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
    	}
    }

    /** Delete document from workspace on path /ws/{repository}/{workspace}/{id}
     * 
     * @param repository string identifier of a document repository
     * @param workspaceName string identifier of a workspace
     * @param id string identifier of a document
     */
    @DELETE
    @Path("/{repository}/{workspace: [^&?]+}/{id}")
    public Response deleteDocument(
    	@PathParam("repository") String repository,
    	@PathParam("workspace") String workspaceName,
    	@PathParam("id") String id) {
    	try {
    			RepositoryService service = repositoryServiceFactory.getService(repository);

    			if (service == null) 
    				return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();
    			
    			QualifiedName wsName = QualifiedName.ROOT.parse(workspaceName, "/").reverse();
    			
    			if (wsName.part.equals("~")) {    			
        			service.deleteDocument(wsName.parent.part, id);
    			} else {
    				Workspace ws = service.getWorkspaceByName(workspaceName);
        			service.deleteDocument(ws.getId(), id);
    			}    			
    			
    			return Response.status(Status.NO_CONTENT).build();
    	} catch (InvalidWorkspace err) {
    		return Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(err)).build();
    	} catch (InvalidDocumentId err) {
    		return Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(err)).build();
    	} catch (InvalidWorkspaceState err) {
    		return Response.status(Status.FORBIDDEN).entity(Error.mapServiceError(err)).build();
    	} catch (Throwable e) {
    		LOG.severe(e.getMessage());
    		e.printStackTrace(System.err);
    		return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
    	}
    }    
}