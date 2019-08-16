package com.softwareplumbers.dms.rest.server.model;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.core.MediaType;


/**  Represents a document returned by DMS.
 * 
 * @author Jonathan Essex
 */
public interface Document extends StreamableRepositoryObject {
	
	/** get the version id for this document */
	public String getVersion();
	/** Default implementation returns Type.DOCUMENT */	
	default Type getType() { return Type.DOCUMENT; }
	/** Get a reference for this document */
	default Reference getReference() {
	    return new Reference(getId(), getVersion());
	}
	
	/** Output Document to Json.
     * 
     * Json representation does not contain file itself but is understood to contain sufficient information
     * to retrieve the file.
     * 
     */
    default JsonObject toJson() {
        
        String id = getId();
        JsonObject metadata = getMetadata();
        MediaType mediaType = getMediaType();
        Type type = getType();
        String version = getVersion();
        
        JsonObjectBuilder builder = Json.createObjectBuilder(); 
        // Base fields  
        if (id != null) builder.add("id", id);
        if (metadata != null) builder.add("metadata", metadata);
        if (type != null) builder.add("type", type.toString());
        // Document fields
        if (mediaType != null) builder.add("mediaType", mediaType.toString());
        if (version != null) builder.add("version", version);
        builder.add("length", getLength());
        return builder.build();

    }
};
