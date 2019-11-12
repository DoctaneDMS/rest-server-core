package com.softwareplumbers.dms.rest.server.model;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.core.MediaType;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidObjectName;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspace;
import java.math.BigDecimal;
import java.util.Optional;
import javax.json.JsonArrayBuilder;

public interface DocumentLink extends NamedRepositoryObject, Document {
    
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
    default JsonObject toJson(RepositoryService service, DocumentNavigatorService navigator, int parentLevels, int childLevels) {
        
        String id = getId();
        String version = getVersion();
        JsonObject metadata = getMetadata();
        MediaType mediaType = getMediaType();
        QualifiedName name = getName();
        Type type = getType();
        boolean navigable = navigator != null && navigator.canNavigate(this);

        JsonObjectBuilder builder = Json.createObjectBuilder(); 
        
        if (parentLevels > 0) {
            Optional<NamedRepositoryObject> parent = getParent(service);
            if (parent.isPresent()) builder.add("parent", parent.get().toJson(service, navigator, parentLevels-1, 0));
        }
        
        if (childLevels > 0 && navigable) {
            JsonArrayBuilder childrenBuilder = Json.createArrayBuilder();
            navigator.catalogParts(this, QualifiedName.ROOT)
                .forEach(part -> childrenBuilder.add(part.toJson(service, navigator, 0, childLevels-1)));
            builder.add("parts", childrenBuilder);
        }
        
        // Base fields  
        if (id != null) builder.add("id", id);
        if (metadata != null) builder.add("metadata", metadata);
        if (type != null) builder.add("type", type.toString());
        // Named Repository Item fields
        if (name != null) builder.add("name", name.join("/"));
        // Document fields
        if (mediaType != null) builder.add("mediaType", mediaType.toString());
        if (version != null) builder.add("version", version);
        builder.add("length", getLength());
        builder.add("navigable", navigable);
        return builder.build();

    }
}
