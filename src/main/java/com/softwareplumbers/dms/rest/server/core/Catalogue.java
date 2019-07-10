package com.softwareplumbers.dms.rest.server.core;

import java.util.logging.Logger;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.ws.rs.DefaultValue;
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

import com.softwareplumbers.dms.rest.server.model.Document;
import com.softwareplumbers.dms.rest.server.model.DocumentPart;
import com.softwareplumbers.dms.rest.server.model.Reference;
import com.softwareplumbers.dms.rest.server.model.RepositoryObject;
import com.softwareplumbers.dms.rest.server.model.RepositoryService;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidReference;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspace;
import com.softwareplumbers.common.abstractquery.Query;

/** Handle catalog operations on repositories and documents.
 * 
 * Create/Read/Update/Delete operations on documents in a  repository are all performed 
 * via this interface under the /docs/{repository} path.
 * 
 * @author Jonathan Essex
 *
 */
@Component
@Authenticated
@Path("/cat")
public class Catalogue {
	///////////--------- Static member variables --------////////////

	private static Logger LOG = Logger.getLogger("cat");

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
    
    /** GET a catalog on path /cat/{repository}
     * 
     * Retrieves the catalog for a given repository or workspace. Documents may be filtered
     * using a query. (See the abstract query project).
     * 
     * @param repository string identifier of a document repository
     * @param query Base64 encoded query string.
     * @return A list of references in json format
     */
    @GET
    @Path("{repository}")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response get(
    	@PathParam("repository") String repository,
    	@QueryParam("workspace") String workspace,
    	@QueryParam("searchHistory") @DefaultValue("false") boolean searchHistory,
    	@QueryParam("query") String query
    ) {
    	try {
    		RepositoryService service = repositoryServiceFactory.getService(repository);

    			if (service == null) 
    				return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();
    		
    			JsonArrayBuilder result = Json.createArrayBuilder(); 
    			Stream<? extends RepositoryObject> infos = null;
    			Query queryQuery = query == null ? Query.UNBOUNDED : Query.urlDecode(query);
    			
    			if (workspace != null)
    				infos = service.catalogueById(workspace, queryQuery , searchHistory);
    			else
    				infos = service.catalogue(queryQuery, searchHistory);
    			
    			infos
    				.map(RepositoryObject::toJson)
    				.forEach(info->result.add(info));
    			
    			//TODO: must be able to do this in a stream somehow.
    			return Response.ok().type(MediaType.APPLICATION_JSON).entity(result.build()).build();
    	} catch (InvalidWorkspace err) {
    		return Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(err)).build();
    	} catch (Throwable e) {
    		LOG.severe(e.getMessage());
    		e.printStackTrace(System.err);
    		return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
    	}
    }

    /** GET a catalog on path /cat/{repository}/{id}
     * 
     * Retrieves the history for a given document, from inception up to
     * the given version. Results may be filtered
     * using a query. (See the abstract query project).
     * 
     * @param repository string identifier of a document repository
     * @param query Base64 encoded query string.
     * @return A list of info objects in json format
     */
    @GET
    @Path("{repository}/{id}")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response get(
    	@PathParam("repository") String repository,
    	@PathParam("id") String id,
    	@QueryParam("version") String version,
    	@QueryParam("query") String query) {
    	try {
    		RepositoryService service = repositoryServiceFactory.getService(repository);

    			if (service == null) 
    				return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();
    		
    			JsonArrayBuilder result = Json.createArrayBuilder(); 
    			service.catalogueHistory(new Reference(id,version), query == null ? Query.UNBOUNDED : Query.urlDecode(query))
    				.map(Document::toJson)
    				.forEach(info->result.add(info));
    			
    			//TODO: must be able to do this in a stream somehow.
    			return Response.ok().type(MediaType.APPLICATION_JSON).entity(result.build()).build();
    	} catch (InvalidReference err) {
    		return Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(err)).build();
    	} catch (Throwable e) {
    		LOG.severe(e.getMessage());
    		e.printStackTrace(System.err);
    		return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
    	}
    }

    /** GET a catalog on path /cat/{repository}/{id}
     * 
     * Retrieves the history for a given document, from inception up to
     * the given version. Results may be filtered
     * using a query. (See the abstract query project).
     * 
     * @param repository string identifier of a document repository
     * @param query Base64 encoded query string.
     * @return A list of info objects in json format
     */
    @GET
    @Path("{repository}/{id}/file")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response getParts(
    	@PathParam("repository") String repository,
    	@PathParam("id") String id,
    	@QueryParam("version") String version,
    	@QueryParam("query") String query) {
    	try {
    		RepositoryService service = repositoryServiceFactory.getService(repository);

    			if (service == null) 
    				return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();
    		
    			JsonArrayBuilder result = Json.createArrayBuilder(); 
    			service.catalogueParts(new Reference(id,version), query == null ? Query.UNBOUNDED : Query.urlDecode(query))
    				.map(DocumentPart::toJson)
    				.forEach(info->result.add(info));
    			
    			//TODO: must be able to do this in a stream somehow.
    			return Response.ok().type(MediaType.APPLICATION_JSON).entity(result.build()).build();
    	} catch (InvalidReference err) {
    		return Response.status(Status.NOT_FOUND).entity(Error.mapServiceError(err)).build();
    	} catch (Throwable e) {
    		LOG.severe(e.getMessage());
    		e.printStackTrace(System.err);
    		return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
    	}
    }
    
}
