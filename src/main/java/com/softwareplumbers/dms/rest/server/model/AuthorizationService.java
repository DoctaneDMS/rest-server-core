/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractquery.Query;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidObjectName;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidReference;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspace;
import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;

/** Authorization service.
 * 
 * Adds application-specific authorizations to a Doctane repository.
 *
 * @author Jonathan Essex
 */
public interface AuthorizationService {
    
    public enum DocumentAccessRole { READ, UPDATE }
    public enum ObjectAccessRole { CREATE, READ, UPDATE, DELETE }
    
    public static ObjectAccessRole castRole(DocumentAccessRole dar) {
        return ObjectAccessRole.valueOf(dar.toString());
    }
    
    /** 
     *  Get the Access Control List for a document.
     * 
     * If getDocumentACL(ref).contains(Value.from(userMetadata)) returns true, 
     * the user has can perform the given role on the referenced document.
     * 
     * @param ref Reference to get ACL for
     * @param role Role to get ACL for
     * @return An access control list that can be used to determine if a user has the given role for the referenced document
     * @throws InvalidReference
     */
    Query getDocumentACL(Reference ref, DocumentAccessRole role) throws InvalidReference;
    
    /** Get the Access Control List for a document.
     * 
     * If getDocumentACL(ref).contains(Value.from(userMetadata)) returns true, the user has can perform the given role
     * on the referenced document.
     * 
     * 
     * @param doc Document to get ACL for
     * @param role Role to get ACL for
     * @return An access control list that can be used to determine if a user has the given role for the referenced document
     */
    Query getDocumentACL(Document doc, DocumentAccessRole role);
    
    Query getDocumentCreationACL(MediaType mediaType, JsonObject metadata);
    
    /** Get the Access Control List for a Repository Object (Workspace or Document).
     * 
     * If getObjectACL(rootId, path).contains(Value.from(userMetadata)) returns 
     * true, the user has can perform the given role on the referenced repository object.
     * 
     * For the 'CREATE' role, path may not reference a valid object, we are asking for ACL
     * controlling creation of an object at the specified path.
     * 
     * @param rootId Id of search root
     * @param path path from root to object
     * @param role to get ACL for
     * @return An access control list that can be used to determine if a user has the given role for the object
     * @throws InvalidObjectName
     * @throws InvalidWorkspace
     */
    Query getObjectACL(String rootId, QualifiedName path, ObjectAccessRole role) throws InvalidObjectName, InvalidWorkspace;
    
    /**  Get the Access Control List for a Repository Object (Workspace or Document).
     * 
     * If getObjectACL(rootId, path).contains(Value.from(userMetadata)) returns true, the user has can perform the given
     * role on the referenced repository object.
     * 
     * @param object object to get ACL
     * @param role to get ACL for
     * @return An access control list that can be used to determine if a user has the given role for the object
     */
    Query getObjectACL(NamedRepositoryObject object, ObjectAccessRole role);
    
    Query getObjectACL(String rootId, QualifiedName path, String documentId, ObjectAccessRole role) throws InvalidObjectName, InvalidWorkspace, InvalidReference;

    /** Get An Access Constraint for the given user searching on the given path.
     * 
     * Allows search operations to be constrained based on a user's permissions.
     * 
     * getAccessConstraint(userMetadata, rootId, pathTemplate).contains(Value.from(repositoryObject)) will return
     * false for any repositoryObject on the paths specified by (rootId, pathTemplate) if the specified user
     * does not have permission to view that repository object.
     * 
     * @param userMetadata User metadata for user performing the search
     * @param rootId Origin of search path
     * @param pathTemplate Path to search on (may contain wildcards).
     * @return An access constraint which can filter out search results for which the user has no permission to view
     */
    Query getAccessConstraint(JsonObject userMetadata, String rootId, QualifiedName pathTemplate);
    
    
    /** Get An Access Constraint for the given user searching on the given path.
     * 
     * Allows search operations to be constrained based on a user's permissions.
     * 
     * getAccessConstraint(userMetadata, rootId, pathTemplate).contains(Value.from(repositoryObject)) will return
     * false for any repositoryObject on the paths specified by (rootId, pathTemplate) if the specified user
     * does not have permission to view that repository object.
     * 
     * @param userMetadata User metadata for user performing the search
     * @param searchRoot Origin of search (search returns only children of provided object).
     * @return An access constraint which can filter out search results for which the user has no permission to view
     */
    Query getAccessConstraint(JsonObject userMetadata, NamedRepositoryObject searchRoot);
    
    /** Get metadata for a given user Id.
     * 
     * Metadata may represent application specific permissions in a way entirely defined by the application; the
     * Json format is simply convenient as as generic way to structure data suitable for transfer over the wire.
     * 
     * @param userId
     * @return application specific metadata for that user Id 
     */
    JsonObject getUserMetadata(String userId);
}
