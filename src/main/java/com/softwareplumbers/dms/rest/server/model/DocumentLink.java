package com.softwareplumbers.dms.rest.server.model;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.core.MediaType;

import com.softwareplumbers.common.QualifiedName;

public interface DocumentLink extends DocumentPart, Document {
    
    /** Default implementation returns Type.DOCUMENT_LINK */ 
    @Override
    default Type getType() { return Type.DOCUMENT_LINK; }
    
    /** Output DocumentLink to JSON.
     * 
     * JSON representation does not contain file itself but is understood to contain sufficient information 
     * to retrieve the file.
     * 
     * @return A JSON representation of the document link.
     */
    @Override
    default JsonObject toJson() {
        
        String id = getId();
        String version = getVersion();
        JsonObject metadata = getMetadata();
        MediaType mediaType = getMediaType();
        QualifiedName name = getName();
        Type type = getType();
        RepositoryObject parent = getParent();
        
        JsonObjectBuilder builder = Json.createObjectBuilder(); 
        
        // Base fields  
        if (id != null) builder.add("id", id);
        if (metadata != null) builder.add("metadata", metadata);
        if (type != null) builder.add("type", type.toString());
        // Named Repository Item fields
        if (name != null) builder.add("name", name.join("/"));
        // Document fields
        if (mediaType != null) builder.add("mediaType", mediaType.toString());
        if (version != null) builder.add("version", version);
        if (parent != null) builder.add("parent", parent.toJson());
        builder.add("length", getLength());
        return builder.build();

    }
}
