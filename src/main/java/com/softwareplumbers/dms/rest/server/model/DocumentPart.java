package com.softwareplumbers.dms.rest.server.model;
import com.softwareplumbers.common.QualifiedName;
import static com.softwareplumbers.dms.rest.server.model.RepositoryObject.Type.DOCUMENT_PART;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.core.MediaType;

public interface DocumentPart extends NamedRepositoryObject, StreamableRepositoryObject {
    	
    /** Get type of object
     * @return DOCUMENT_PART */
    @Override
    default Type getType() { return DOCUMENT_PART; }
    
    /** Output DocumentPart to JSON. 
     * 
     * JSON representation does not contain file itself but is understood to contain sufficient information 
     * to retrieve the file.
     *
     * @param service 
     * @return A JSON representation of the document link.
     */
    @Override
    default JsonObject toJson(RepositoryService service, DocumentNavigatorService navigator, int parentLevels, int childLevels) {
        
        JsonObject metadata = getMetadata();
        MediaType mediaType = getMediaType();
        QualifiedName name = getName();
        Type type = getType();

        
        JsonObjectBuilder builder = Json.createObjectBuilder(); 

        if (parentLevels > 0) {
            QualifiedName parentName = getName().parent;
            if (!parentName.isEmpty()) {
                try {
                    RepositoryObject parent = service.getObjectByName(Constants.ROOT_ID, getName().parent);
                    builder.add("parent", parent.toJson(service, navigator, parentLevels-1, 0));
                } catch (RepositoryService.InvalidObjectName | RepositoryService.InvalidWorkspace err) {
                    throw new RuntimeException(err);
                }
            }
        }
        
        if (childLevels > 0 && navigator.canNavigate(this)) {
            JsonArrayBuilder childrenBuilder = Json.createArrayBuilder();
            navigator.catalogParts(this, QualifiedName.ROOT)
                .forEach(part -> childrenBuilder.add(part.toJson(service, navigator, 0, childLevels-1)));
            builder.add("parts", childrenBuilder);
        }
        
        // Base fields  
        if (metadata != null) builder.add("metadata", metadata);
        if (type != null) builder.add("type", type.toString());
        // Named Repository Item fields
        if (name != null) builder.add("name", name.join("/"));
        // Document fields
        if (mediaType != null) builder.add("mediaType", mediaType.toString());
        builder.add("length", getLength());
        return builder.build();
    }
}
