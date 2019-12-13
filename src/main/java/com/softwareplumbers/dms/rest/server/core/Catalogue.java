package com.softwareplumbers.dms.rest.server.core;

import com.softwareplumbers.common.QualifiedName;
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
import com.softwareplumbers.dms.rest.server.model.DocumentNavigatorService;
import com.softwareplumbers.dms.rest.server.model.DocumentNavigatorService.DocumentFormatException;
import com.softwareplumbers.dms.rest.server.util.Log;
import java.util.logging.Level;

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

	private static Log LOG = new Log(Catalogue.class);

	///////////---------  member variables --------////////////

	private RepositoryServiceFactory repositoryServiceFactory;
    private DocumentNavigatorService documentNavigatorService;
    
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
        this.documentNavigatorService = documentNavigatorService;
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
    				return LOG.logResponse("get", Error.errorResponse(Status.NOT_FOUND,Error.repositoryNotFound(repository)));
    		
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
    			return LOG.logResponse("get", Response.ok().type(MediaType.APPLICATION_JSON).entity(result.build()).build());
    	} catch (InvalidWorkspace err) {
    		return LOG.logResponse("get", Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(err)));
    	} catch (Throwable e) {
    		return LOG.logResponse("get", Error.errorResponse(Status.INTERNAL_SERVER_ERROR,Error.reportException(e)));
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
    				return LOG.logResponse("get", Error.errorResponse(Status.NOT_FOUND,Error.repositoryNotFound(repository)));
    		
    			JsonArrayBuilder result = Json.createArrayBuilder(); 
    			service.catalogueHistory(new Reference(id,version), query == null ? Query.UNBOUNDED : Query.urlDecode(query))
    				.map(Document::toJson)
    				.forEach(info->result.add(info));
    			
    			//TODO: must be able to do this in a stream somehow.
    			return LOG.logResponse("get", Response.ok().type(MediaType.APPLICATION_JSON).entity(result.build()).build());
    	} catch (InvalidReference err) {
    		return LOG.logResponse("get", Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(err)));
    	} catch (RuntimeException e) {
    		return LOG.logResponse("get", Error.errorResponse(Status.INTERNAL_SERVER_ERROR,Error.reportException(e)));
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
    				return LOG.logResponse("getParts", Error.errorResponse(Status.NOT_FOUND,Error.repositoryNotFound(repository)));
    		
    			JsonArrayBuilder result = Json.createArrayBuilder(); 
                Document document = service.getDocument(new Reference(id,version));
                Query filter = query == null ? Query.UNBOUNDED : Query.urlDecode(query);
                if (documentNavigatorService.canNavigate(document)) {
                    documentNavigatorService.catalogParts(document, QualifiedName.ROOT)
                        .map(DocumentPart::toJson)
                        .filter(obj->filter.containsItem(obj))
                        .forEach(obj->result.add(obj));
    			//TODO: must be able to do this in a stream somehow.
                    return LOG.logResponse("getParts", Response.ok().type(MediaType.APPLICATION_JSON).entity(result.build()).build());
                } else {
                    return LOG.logResponse("getParts", Error.errorResponse(Status.BAD_REQUEST,Error.badOperation("file cannot be split into parts")));
                }
    			
    	} catch (InvalidReference err) {
    		return LOG.logResponse("getParts", Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(err)));
    	} catch (DocumentFormatException ex) { 
            return LOG.logResponse("getParts", Error.errorResponse(Status.INTERNAL_SERVER_ERROR,Error.mapServiceError(ex)));
        } 
    }
    
}
