package com.softwareplumbers.dms.rest.server.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.json.JsonObject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;

import org.glassfish.jersey.media.multipart.BodyPart;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.softwareplumbers.dms.rest.server.model.Document;

@Component
@Path("/docs")
public class Documents {
	
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

    @GET
    @Path("{repository}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response put(
    	@PathParam("repository") String repository, 
    	@PathParam("id") String id,
    	@PathParam("version") Integer version) {
    	try {

    		Document document = repositoryServiceFactory.getService(repository).getDocument(new Document.Reference(id, version));
        
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
    			return Response.status(Status.NOT_FOUND).build();    			
    		}
    	} catch (Exception e) {
    		return Response.status(Status.INTERNAL_SERVER_ERROR).build();
    	}
    }
    
    @POST
    @Path("{repository}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces("mime/multipart")
    public Response post(
    	@PathParam("repository") String repository,
    	@FormDataParam("metadata") FormDataBodyPart metadata_part,
    	@FormDataParam("file") StreamDataBodyPart file_part
    	) {
    	try {
    		    		
    		Document.Reference reference = 
    			repositoryServiceFactory.getService(repository)
    				.createDocument(
    					file_part.getMediaType(),
    					()->file_part.getStreamEntity(),
						metadata_part.getEntityAs(JsonObject.class)
					);

    		if (reference != null) {
    			return Response.status(Status.CREATED).entity(reference).build();
    		} else {
    			return Response.status(Status.BAD_REQUEST).build();    			
    		}
    	} catch (Exception e) {
    		return Response.status(Status.INTERNAL_SERVER_ERROR).build();
    	}
    }
}