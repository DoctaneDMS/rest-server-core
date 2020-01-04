package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidObjectName;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspace;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.json.JsonObject;

/** Base interface for named repository objects such as workspaces and document links.
 * 
 * @author SWPNET\jonessex
 */
public interface NamedRepositoryObject extends RepositoryObject {
    
    /** Get the object name from a standard json representation of a NamedRepositoryObject.
     * 
     * @param object a JSON representation of a document (as might be returned by this.toJson)
     * @return the value of the object's name attribute converted into a QualifiedName 
     */
    public static QualifiedName getName(JsonObject object) {
        return object.containsKey("name") ? QualifiedName.parse(object.getString("name"), "/") : null;
    }
    
    /** Get the parent object representation from a standard json representation of a NamedRepositoryObject.
     * 
     * @param object a JSON representation of a document (as might be returned by this.toJson)
     * @return the optional value of the object's name parent attribute
     */
    public static Optional<JsonObject> getParent(JsonObject object) {
        return object.containsKey("parent") ? Optional.of(object.getJsonObject("parent")) : Optional.empty();
    }

    /** Get the name of a repository object. 
     * 
     * Returns the name of the object relative to the root. In the case of an anonymous workspace,
     * this should return '~<workspace id>'. This enables the children of an anonymous workspace to
     * be consistently referenced via the APIs.
     * 
     * @return the qualified name of this repository object
     */
    QualifiedName getName();  
    
    default Optional<NamedRepositoryObject> getParent(RepositoryService service) {
        if (QualifiedName.ROOT == getName()) return null;
        try {
            return Optional.of(service.getObjectByName(null, getName().parent));
        } catch (InvalidWorkspace | InvalidObjectName err) {
            return Optional.empty();
        }
    }
}
