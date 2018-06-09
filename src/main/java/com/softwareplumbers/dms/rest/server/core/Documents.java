package com.softwareplumbers.dms.rest.server.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.logging.Logger;

import javax.json.JsonObject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;

import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.softwareplumbers.dms.rest.server.model.Document;
import com.softwareplumbers.dms.rest.server.model.RepositoryService;

@Component
@Path("/docs")
public class Documents {
	
	Logger LOG = Logger.getLogger("docs");
	
    private static class DocumentOutput implements StreamingOutput {   	
    	private final Document document;

		@Override
		public void write(OutputStream output) throws IOException, WebApplicationException {
			document.writeDocument(output);			
		}
		
		public DocumentOutput(Document document) { this.document = document; }
    }
    
    private RepositoryServiceFactory repositoryServiceFactory;
    
    
    @Autowired
    public void setRepositoryServiceFactory(RepositoryServiceFactory serviceFactory) {
        this.repositoryServiceFactory = serviceFactory;
    }

    /**
     * 
     * retrieves a specific document by its unique identifier
     * 
     * @param repository string identifier of a document repository
     * @param id string document id
     * @param version integer version number of documet
     * @return
     */
    @GET
    @Path("{repository}/{id}")
    @Produces({ MediaType.MULTIPART_FORM_DATA, MediaType.APPLICATION_JSON })
    public Response get(
    	@PathParam("repository") String repository, 
    	@PathParam("id") String id,
    	@QueryParam("version") Integer version) {
    	try {
    		RepositoryService service = repositoryServiceFactory.getService(repository);

    		if (service == null) 
    			return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();

    		Document document = service.getDocument(new Document.Reference(id, version));
        
    		if (document != null) { 
    			MultiPart response = new MultiPart();
    			FormDataBodyPart metadata = new FormDataBodyPart();
    			metadata.setName("metadata");
    			metadata.setMediaType(MediaType.APPLICATION_JSON_TYPE);
    			metadata.setEntity(document.getMetadata());
    			FormDataBodyPart file = new FormDataBodyPart();
    			file.setName("file");
    			file.setMediaType(document.getMediaType());
    			file.getHeaders().add("Content-Length", Integer.toString(document.getLength()));
    			file.setEntity(new DocumentOutput(document));
    			response.bodyPart(metadata);
    			response.bodyPart(file);
    			return Response.status(Status.OK).entity(response).build();
    		} else {
    			return Response.status(Status.NOT_FOUND).entity(Error.documentNotFound(repository,id,version)).build();    			
    		}
    	} catch (Throwable e) {
    		LOG.severe(e.getMessage());
    		e.printStackTrace(System.err);
    		return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
    	}
    }
    
    /**
     * 
     * retrieves a specific document by its unique identifier
     * 
     * @param repository string identifier of a document repository
     * @param id string document id
     * @param version integer version number of document
     * @return
     */
    @GET
    @Path("{repository}/{id}/file")
    public Response getFile(
    	@PathParam("repository") String repository, 
    	@PathParam("id") String id,
    	@QueryParam("version") Integer version) {
    	try {
    		RepositoryService service = repositoryServiceFactory.getService(repository);

    		if (service == null) 
    			return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();

    		Document document = service.getDocument(new Document.Reference(id, version));
        
    		if (document != null) { 
    			return Response
    				.status(Status.OK)
    				.type(document.getMediaType())
    				.entity(new DocumentOutput(document))
    				.build();
    		} else {
    			return Response.status(Status.NOT_FOUND).entity(Error.documentNotFound(repository,id,version)).build();    			
    		}
    	} catch (Throwable e) {
    		LOG.severe(e.getMessage());
    		e.printStackTrace(System.err);
    		return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
    	}
    }
    
    /**
     * 
     * retrieves metadata for a specific document by its unique identifier
     * 
     * @param repository string identifier of a document repository
     * @param id string document id
     * @param version integer version number of document
     * @return Response wrapping JSON metadata
     */
    @GET
    @Path("{repository}/{id}/metadata")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMetadata(
    	@PathParam("repository") String repository, 
    	@PathParam("id") String id,
    	@QueryParam("version") Integer version) {
    	try {
    		RepositoryService service = repositoryServiceFactory.getService(repository);

    		if (service == null) 
    			return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();

    		Document document = service.getDocument(new Document.Reference(id, version));
        
    		if (document != null) { 
    			return Response
    				.status(Status.OK)
    				.entity(document.getMetadata())
    				.build();
    		} else {
    			return Response.status(Status.NOT_FOUND).entity(Error.documentNotFound(repository,id,version)).build();    			
    		}
    	} catch (Throwable e) {
    		LOG.severe(e.getMessage());
    		e.printStackTrace(System.err);
    		return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
    	}
    }
    
    /** Create a new document in the repository
     * 
     * Upload a new document to the repository, together with a metadata JSON object, 
     * as a mime-encoded stream
     * 
     * @param repository string identifier of a document repository
     * @param metadata_part
     * @param file_part
     * @return A document reference (document id and version) as JSON.
     */
    @POST
    @Path("{repository}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response post(
    	@PathParam("repository") String repository,
    	@FormDataParam("metadata") FormDataBodyPart metadata_part,
    	@FormDataParam("file") FormDataBodyPart file_part
    	) {
    	try {
    		RepositoryService service = repositoryServiceFactory.getService(repository);

    		if (service == null) 
    			return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();
    					
    		if (metadata_part == null) 
    			return Response.status(Status.BAD_REQUEST).entity(Error.missingMetadata()).build();

    		if (file_part == null) 
    			return Response.status(Status.BAD_REQUEST).entity(Error.missingFile()).build();


    		Document.Reference reference = 
    			service
    				.createDocument(
    					file_part.getMediaType(),
    					()->file_part.getEntityAs(InputStream.class),
						metadata_part.getEntityAs(JsonObject.class)
					);

    		if (reference != null) {
    			return Response.status(Status.CREATED).entity(reference).build();
    		} else {
    			return Response.status(Status.BAD_REQUEST).entity(Error.unexpectedFailure()).build();    			
    		}
    	} catch (Exception e) {
    		LOG.severe(e.getMessage());
    		e.printStackTrace(System.err);
    		return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
    	}
    }
    
    /** Create a new document in the repository without metadata
     * 
     * Upload a new document to the repository, as a binary stream
     * 
     * @param repository string identifier of a document repository
     * @param request complete HTTP request
     * @return A document reference (document id and version) as JSON.
     */
    @POST
    @Path("{repository}/file")
    @Produces(MediaType.APPLICATION_JSON)
    public Response postFile(
    	@PathParam("repository") String repository,
    	@Context HttpServletRequest request
    	) {
    	try {
    		RepositoryService service = repositoryServiceFactory.getService(repository);

    		if (service == null) 
    			return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();

    		Document.Reference reference = 
    			service
    				.createDocument(
    					MediaType.valueOf(request.getContentType()),
    					() -> request.getInputStream(),
						JsonObject.EMPTY_JSON_OBJECT
					);

    		if (reference != null) {
    			return Response.status(Status.CREATED).entity(reference).build();
    		} else {
    			return Response.status(Status.BAD_REQUEST).entity(Error.unexpectedFailure()).build();    			
    		}
    	} catch (Exception e) {
    		LOG.severe(e.getMessage());
    		e.printStackTrace(System.err);
    		return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
    	}
    }
    
    @PUT
    @Path("{repository}/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response put(
    	@PathParam("repository") String repository,
    	@PathParam("id") String id,
    	@FormDataParam("metadata") FormDataBodyPart metadata_part,
    	@FormDataParam("file") FormDataBodyPart file_part
    	) {
    	try {
    		RepositoryService service = repositoryServiceFactory.getService(repository);

    		if (service == null) 
    			return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();
    					
    		if (metadata_part == null) 
    			return Response.status(Status.BAD_REQUEST).entity(Error.missingMetadata()).build();

    		if (file_part == null) 
    			return Response.status(Status.BAD_REQUEST).entity(Error.missingFile()).build();

    		Document.Reference reference = 
    			service
    				.updateDocument(
    					id,
    					file_part.getMediaType(),
    					()->file_part.getEntityAs(InputStream.class),
						metadata_part.getEntityAs(JsonObject.class)
					);

    		if (reference != null) {
    			return Response.status(Status.ACCEPTED).entity(reference).build();
    		} else {
    			return Response.status(Status.NOT_FOUND).entity(Error.documentNotFound(repository, id, null)).build();    			
    		}
    	} catch (Exception e) {
    		LOG.severe(e.getMessage());
    		e.printStackTrace(System.err);
    		return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
    	}
    }
    
    @PUT
    @Path("{repository}/{id}/file")
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateFile(
    	@PathParam("repository") String repository,
    	@PathParam("id") String id,
    	@Context HttpServletRequest request
    	) {
    	try {
    		RepositoryService service = repositoryServiceFactory.getService(repository);

    		if (service == null) 
    			return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();
    					
    		Document.Reference reference = 
    			service
    				.updateDocument(
    					id,
    					MediaType.valueOf(request.getContentType()),
    					()->request.getInputStream(),
						null
					);

    		if (reference != null) {
    			return Response.status(Status.ACCEPTED).entity(reference).build();
    		} else {
    			return Response.status(Status.NOT_FOUND).entity(Error.documentNotFound(repository, id, null)).build();    			
    		}
    	} catch (Exception e) {
    		LOG.severe(e.getMessage());
    		e.printStackTrace(System.err);
    		return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
    	}
    }
    
    @PUT
    @Path("{repository}/{id}/metadata")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateMetadata(
    	@PathParam("repository") String repository,
    	@PathParam("id") String id,
    	JsonObject metadata
    	) {
    	try {
    		RepositoryService service = repositoryServiceFactory.getService(repository);

    		if (service == null) 
    			return Response.status(Status.NOT_FOUND).entity(Error.repositoryNotFound(repository)).build();
    					
    		Document.Reference reference = 
    			service
    				.updateDocument(
    					id,
    					null,
    					null,
						metadata
					);

    		if (reference != null) {
    			return Response.status(Status.ACCEPTED).entity(reference).build();
    		} else {
    			return Response.status(Status.NOT_FOUND).entity(Error.documentNotFound(repository, id, null)).build();    			
    		}
    	} catch (Exception e) {
    		LOG.severe(e.getMessage());
    		e.printStackTrace(System.err);
    		return Response.status(Status.INTERNAL_SERVER_ERROR).entity(Error.reportException(e)).build();
    	}
    }
}
