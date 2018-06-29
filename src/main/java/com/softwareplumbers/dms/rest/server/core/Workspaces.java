package com.softwareplumbers.dms.rest.server.core;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.ws.rs.GET;
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

    /** GET a catalog on path /workspace/{repository}/{workspace}
     * 
     * Retrieves the catalog for a given workspace. Documents may be filtered
     * using a query. (See the abstract query project).
     * 
     * @param repository string identifier of a document repository
     * @param query Base64 encoded query string.
     * @returns A list of references in json format
     */
    @GET
    @Path("cat/{repository}/{workspace}")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response get(
    	@PathParam("repository") String repository,
    	@PathParam("workspace") String workspace,
    	@QueryParam("query") String query) {
    	try {
    			RepositoryService service = repositoryServiceFactory.getService(repository);

    			if (service == null) 
    				return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();
    		
    			Cube queryCube = query == null ? Cube.UNBOUNDED : Cube.urlDecode(query);
    			queryCube = queryCube.intersect(Cube.from("workspace", Range.equals(Value.from(workspace))));
    			JsonArrayBuilder result = Json.createArrayBuilder(); 
    			service.catalogue(queryCube)
    				.map(Info::toJson)
    				.forEach(info->result.add(info));
    			
    			//TODO: must be able to do this in a stream somehow.
    			return Response.ok().type(MediaType.APPLICATION_JSON).entity(result.build()).build();
    	} catch (Throwable e) {
    		LOG.severe(e.getMessage());
    		e.printStackTrace(System.err);
    		return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
    	}
    }

}