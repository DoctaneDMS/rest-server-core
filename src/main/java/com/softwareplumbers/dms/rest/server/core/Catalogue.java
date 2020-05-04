package com.softwareplumbers.dms.rest.server.core;

import com.softwareplumbers.common.immutablelist.QualifiedName;
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

import com.softwareplumbers.dms.Document;
import com.softwareplumbers.dms.DocumentPart;
import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.RepositoryObject;
import com.softwareplumbers.dms.RepositoryService;
import com.softwareplumbers.dms.Exceptions.InvalidReference;
import com.softwareplumbers.dms.Exceptions.InvalidWorkspace;
import com.softwareplumbers.common.abstractquery.Query;
import com.softwareplumbers.dms.Exceptions.InvalidObjectName;
import com.softwareplumbers.dms.NamedRepositoryObject;
import com.softwareplumbers.dms.Options;
import com.softwareplumbers.dms.RepositoryPath;
import javax.json.JsonValue;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

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

	private static XLogger LOG = XLoggerFactory.getXLogger(Catalogue.class);

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
    	@QueryParam("query") String query,
    	@QueryParam("filter") String filter
    ) {
    	try {
    		RepositoryService service = repositoryServiceFactory.getService(repository);

    			if (service == null) 
    				return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.repositoryNotFound(repository)));
                if (query != null)
                    LOG.warn("Method uses deprecated query parameter. Use filter instead");
    		
    			JsonArrayBuilder result = Json.createArrayBuilder(); 
    			Stream<? extends RepositoryObject> infos = null;
                
                if (filter == null && query != null) filter = query;
                
    			Query queryQuery = filter == null ? Query.UNBOUNDED : Query.urlDecode(filter);
    			
    			if (workspace != null)
    				infos = service.catalogueByName(RepositoryPath.ROOT.addId(workspace), queryQuery, Options.Search.EMPTY.addOptionIf(Options.SEARCH_OLD_VERSIONS, searchHistory).build());
    			else
    				infos = service.catalogue(queryQuery, searchHistory);
    			
                try {
                    infos
                        .map(RepositoryObject::toJson)
                        .forEach(info->result.add(info));
                } finally {
                    infos.close();
                }
                
    			//TODO: must be able to do this in a stream somehow.
    			return LOG.exit(Response.ok().type(MediaType.APPLICATION_JSON).entity(result.build()).build());
    	} catch (InvalidWorkspace e) {
            return LOG.exit(Error.errorResponse(Status.NOT_FOUND, Error.mapServiceError(e)));
        } catch (RuntimeException e) {
    		return LOG.exit(Error.errorResponse(Status.INTERNAL_SERVER_ERROR,Error.reportException(e)));
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
    	@QueryParam("query") String query,
    	@QueryParam("filter") String filter) {
    	try {
    		RepositoryService service = repositoryServiceFactory.getService(repository);

    			if (service == null) 
    				return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.repositoryNotFound(repository)));
                if (query != null)
                    LOG.warn("Method uses deprecated query parameter. Use filter instead");
                
                if (filter == null && query != null) filter = query;

    			JsonArrayBuilder result = Json.createArrayBuilder(); 
                
                try (Stream<Document> rs = service.catalogueHistory(new Reference(id,version), filter == null ? Query.UNBOUNDED : Query.urlDecode(filter))) {
    				rs.map(Document::toJson)
    				.forEach(info->result.add(info));                    
                }
    			
    			//TODO: must be able to do this in a stream somehow.
    			return LOG.exit(Response.ok().type(MediaType.APPLICATION_JSON).entity(result.build()).build());
    	} catch (InvalidReference err) {
    		return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(err)));
    	} catch (RuntimeException e) {
    		return LOG.exit(Error.errorResponse(Status.INTERNAL_SERVER_ERROR,Error.reportException(e)));
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
    	@QueryParam("query") String query,
    	@QueryParam("filter") String filter) {
        
    	try {
    		RepositoryService service = repositoryServiceFactory.getService(repository);

            if (service == null) 
                return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.repositoryNotFound(repository)));
            if (query != null)
                LOG.warn("Method uses deprecated query parameter. Use filter instead");
            
            if (filter == null && query != null) filter = query;

            JsonArrayBuilder result = Json.createArrayBuilder(); 
            
            Query filterQuery = filter == null ? Query.UNBOUNDED : Query.urlDecode(filter);
            
            try (Stream<DocumentPart> rs=service.catalogueParts(new Reference(id,version), RepositoryPath.PART_ROOT)) {
                rs.map(DocumentPart::toJson)
                    .filter(obj->filterQuery.containsItem(obj))
                    .forEach(obj->result.add(obj));
            }
            //TODO: must be able to do this in a stream somehow.
            return LOG.exit(Response.ok().type(MediaType.APPLICATION_JSON).entity(result.build()).build());    			
    	} catch (InvalidReference err) {
    		return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(err)));
    	} catch (InvalidObjectName err) {
    		return LOG.exit(Error.errorResponse(Status.NOT_FOUND,Error.mapServiceError(err)));
    	} 
    }
    
}
