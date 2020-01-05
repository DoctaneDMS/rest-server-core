package com.softwareplumbers.dms.rest.server.tmp;

import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.softwareplumbers.dms.rest.server.core.RepositoryServiceFactory;
import com.softwareplumbers.dms.rest.server.core.Error;
import com.softwareplumbers.dms.RepositoryService;

/** Handle admin operations on mongodb repository.
 * 
 * 
 * @author Jonathan Essex
 *
 */
@Component
@Path("/admin/temp")
public class TempAdmin {
	///////////--------- Static member variables --------////////////

	private static Logger LOG = Logger.getLogger("adminTmp");

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
   
    /** Trigger a clear of the repository
     * 
     * @param repository string identifier of a document repository
     * @return OK
     */
    @GET
    @Path("{repository}/clear")
    @Produces({ MediaType.APPLICATION_JSON })
    public Response clear(
    	@PathParam("repository") String repository) {
    	try {
    		RepositoryService service = repositoryServiceFactory.getService(repository);
    		if (service == null) 
    			return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();
    		if (!(service instanceof TempRepositoryService))
    			return Response.status(Status.BAD_REQUEST).entity(Error.unexpectedFailure()).build();
    		((TempRepositoryService)service).clear();
    		return Response.ok().type(MediaType.APPLICATION_JSON).build();
    	} catch (Throwable e) {
    		LOG.severe(e.getMessage());
    		e.printStackTrace(System.err);
    		return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
    	}
    }

}
