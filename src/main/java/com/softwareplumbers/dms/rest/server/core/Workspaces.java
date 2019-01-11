package com.softwareplumbers.dms.rest.server.core;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
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
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.softwareplumbers.dms.rest.server.model.Info;
import com.softwareplumbers.dms.rest.server.model.RepositoryService;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidDocumentId;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspaceName;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspaceState;
import com.softwareplumbers.dms.rest.server.model.Workspace;
import com.softwareplumbers.common.abstractquery.Cube;
import com.softwareplumbers.common.abstractquery.Range;
import com.softwareplumbers.common.abstractquery.Value;

/** Handle catalog operations on repositories and documents.
 * 
 * operations on a workspace all performed 
 * via this interface under the /workspace/{repository}/{workspace} path.
 * 
 * @author Jonathan Essex
 *
 */
@Component
@Path("/workspace")
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
     * Retrieves information about the given workspace. 
     * 
     * @param repository string identifier of a document repository
     * @param workspace string identifier of a workspace
     * @returns Information about the workspace in json format
     */
    @GET
    @Path("ws/{repository}/{workspace}")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response get(
    	@PathParam("repository") String repository,
    	@PathParam("workspace") String workspaceName) {
    	try {
    			RepositoryService service = repositoryServiceFactory.getService(repository);

    			if (service == null) 
    				return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();
    		
    			Workspace workspace = service.getWorkspace(workspaceName);
    			//TODO: must be able to do this in a stream somehow.
    			return Response.ok().type(MediaType.APPLICATION_JSON).entity(workspace.toJson()).build();
    	} catch (InvalidWorkspaceName err) {
    		return Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(err)).build();
    	} catch (Throwable e) {
    		LOG.severe(e.getMessage());
    		e.printStackTrace(System.err);
    		return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
    	}
    }

    /** PUT workspace state on path /ws/{repository}/{workspace}
     * 
     * Can be used to modify workspace state (e.g. Closing or Finalizing a workspace),
     * and to create a new workspace.
     * 
     * @param repository string identifier of a document repository
     * @param workspace string identifier of a workspace
     */
    @PUT
    @Path("ws/{repository}/{workspace}")
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

    			String stateString = workspace.getString("state");
    			Workspace.State state = stateString == null ? null : Workspace.State.valueOf(stateString);
    			
    			service.updateWorkspace(workspaceName, state, createWorkspace);
    			return Response.status(Status.ACCEPTED).build();
    	} catch (InvalidWorkspaceName err) {
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
     * @param workspace string identifier of a workspace
     * @param id string identifier of a document
     */
    @DELETE
    @Path("ws/{repository}/{workspace}/{id}")
    public Response deleteDocument(
    	@PathParam("repository") String repository,
    	@PathParam("workspace") String workspaceName,
    	@PathParam("id") String id) {
    	try {
    			RepositoryService service = repositoryServiceFactory.getService(repository);

    			if (service == null) 
    				return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();
    			
    			service.deleteDocument(workspaceName, id);
    			return Response.status(Status.NO_CONTENT).build();
    	} catch (InvalidWorkspaceName err) {
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