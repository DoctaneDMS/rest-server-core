package com.softwareplumbers.dms.rest.server.model;
import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.dms.rest.server.model.DocumentNavigatorService.PartNotFoundException;
import static com.softwareplumbers.dms.rest.server.model.RepositoryObject.Type.*;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

/** Part of a document 
 * 
 * Can represent either an individual document element or an assembly of such elements.
 * 
 * @author jonathan.local
 */
public interface DocumentPart extends NamedRepositoryObject {
    
    StreamableRepositoryObject getDocument();
    	
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
        QualifiedName name = getName();
        Type type = getType();
        StreamableRepositoryObject document = this.getDocument();

        
        JsonObjectBuilder builder = Json.createObjectBuilder(); 

        if (navigator != null) {
            boolean navigable = navigator.canNavigate(document, name);

            if (parentLevels > 0) {
                QualifiedName parentName = name.parent;
                if (!parentName.isEmpty()) {
                    try {
                        RepositoryObject parent = navigator.getPartByName(document, name.parent);
                        builder.add("parent", parent.toJson(service, navigator, parentLevels-1, 0));
                    } catch (PartNotFoundException err) {
                        throw new RuntimeException(err);
                    }
                }
            }

            if (childLevels > 0 && navigable) {
                JsonArrayBuilder childrenBuilder = Json.createArrayBuilder();
                navigator.catalogParts(document, name)
                    .forEach(part -> childrenBuilder.add(part.toJson(service, navigator, 0, childLevels-1)));
                builder.add("parts", childrenBuilder);
            }
            builder.add("navigable", navigable);
        }
        
        switch (document.getType()) {
            case DOCUMENT_LINK: 
                builder.add("document", ((DocumentLink)document).getName().join("/"));
                break;
            case DOCUMENT:
                builder.add("document", ((Document)document).getReference().toJson());
                break;
            default:
                throw new RuntimeException("document part cannot be a part of a " + document.getType());
        }
        
        // Base fields  
        if (metadata != null) builder.add("metadata", metadata);
        if (type != null) builder.add("type", type.toString());
        // Named Repository Item fields
        if (name != null) builder.add("name", name.join("/"));
        return builder.build();
    }
}
