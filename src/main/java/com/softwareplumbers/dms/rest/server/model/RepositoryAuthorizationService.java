/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.authz.AuthorizationService;
import com.softwareplumbers.authz.AuthzExceptions.InvalidPath;
import com.softwareplumbers.dms.NamedRepositoryObject;
import com.softwareplumbers.dms.RepositoryObject;
import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.Document;
import com.softwareplumbers.common.abstractquery.Query;
import com.softwareplumbers.common.immutablelist.QualifiedName;
import com.softwareplumbers.dms.Exceptions.InvalidDocumentId;
import com.softwareplumbers.dms.Exceptions.InvalidObjectName;
import com.softwareplumbers.dms.Exceptions.InvalidReference;
import com.softwareplumbers.dms.Exceptions.InvalidWorkspace;
import com.softwareplumbers.dms.RepositoryPath;
import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;

/** Authorization service.
 * 
 * Adds application-specific authorizations to a Doctane repository.
 *
 * @author Jonathan Essex
 */
public class RepositoryAuthorizationService {
    
    public enum DocumentAccessRole { CREATE, READ, UPDATE }
    public enum DocumentTypes { DOCUMENT }
    public enum ObjectAccessRole { CREATE, READ, UPDATE, CREATE_OR_UPDATE, DELETE }
    
    private AuthorizationService<DocumentTypes, DocumentAccessRole, RepositoryPath> documentAuthService;
    private AuthorizationService<RepositoryObject.Type, ObjectAccessRole, RepositoryPath> objectAuthService;
    
    public RepositoryAuthorizationService(
        AuthorizationService<DocumentTypes, DocumentAccessRole, RepositoryPath> documentAuthService,
        AuthorizationService<RepositoryObject.Type, ObjectAccessRole, RepositoryPath> objectAuthService
    ) {
        this.documentAuthService = documentAuthService;
        this.objectAuthService = objectAuthService;
    }
    
    public RepositoryAuthorizationService() {
        this(AuthorizationService.publicAuthz(), AuthorizationService.publicAuthz());
    }
    
    public void setDocumentAuthorizationService(AuthorizationService<DocumentTypes, DocumentAccessRole, RepositoryPath> documentAuthService) {
        this.documentAuthService = documentAuthService;
    }
    
    public void setObjectAuthorizationService(AuthorizationService<RepositoryObject.Type, ObjectAccessRole, RepositoryPath> objectAuthService) {
        this.objectAuthService = objectAuthService;
    }
    
    public static ObjectAccessRole castRole(DocumentAccessRole dar) {
        return ObjectAccessRole.valueOf(dar.toString());
    }
    
    /** 
     *  Get the Access Control List for a document.
     * 
     * If getDocumentACL(ref).contains(Value.from(userMetadata)) returns true, 
     * the user has can perform the given role on the referenced document.
     * 
     * For CREATE role, all parameters are mandatory
     * For READ and UPDATE role, only ref is mandatory. However if data needed
     * for the role check is absent, this may generate an additional request to
     * the back-end repository.
     * 
     * @param ref Reference to get ACL for
     * @param mediaType Type of document
     * @param metadata Document metadata
     * @param role Role to get ACL for
     * @return An access control list that can be used to determine if a user has the given role for the referenced document
     * @throws InvalidReference
     */
    public Query getDocumentACL(Reference ref, String mediaType, JsonObject metadata, DocumentAccessRole role) throws InvalidReference {
        try {
            RepositoryPath path = RepositoryPath.ROOT;
            if (ref != null) { 
                path = path.addDocumentId(ref.id);
                if (ref.version != null) path.setVersion(ref.version);
            }
            return documentAuthService.getObjectACL(path, DocumentTypes.DOCUMENT, metadata, role);
        } catch (InvalidPath e) {
            throw new InvalidReference(ref);
        }
    }
    
    /** Get the Access Control List for a document.
     * 
     * Convenience equivalent to getDocumentACL(doc.getReference(), doc.getMediaType(), doc.getMetadata(), role)
     * 
     * @param doc Document to get ACL for
     * @param role Role to get ACL for
     * @return An access control list that can be used to determine if a user has the given role for the referenced document
     */
    public Query getDocumentACL(Document doc, DocumentAccessRole role) {
        try {
            return getDocumentACL(doc.getReference(), doc.getMediaType(), doc.getMetadata(), role);
        } catch (InvalidReference err) {
            throw new RuntimeException("Unexpected invalid reference " + doc.getReference());
        }
    }
        
    /** Get the Access Control List for a Repository Object (Workspace or Document).
     * 
     * If getObjectACL(rootId, path).contains(Value.from(userMetadata)) returns 
     * true, the user has can perform the given role on the referenced repository object.
     * 
     * For the 'CREATE' role, path may not reference a valid object, we are asking for ACL
     * controlling creation of an object at the specified path.
     * 
     * For CREATE role, all parameters are mandatory
     * For READ, UPDATE, DELETE roles, only path is mandatory. However if data needed
     * for the role check is absent, this may generate an additional request to
     * the back-end repository.
     * 
     * @param path path from root to object
     * @param type type of repository object
     * @param metadata metadata of object
     * @param role to get ACL for
     * @return An access control list that can be used to determine if a user has the given role for the object
     * @throws InvalidObjectName
     * @throws InvalidWorkspace
     */
    public Query getObjectACL(RepositoryPath path, RepositoryObject.Type type, JsonObject metadata, ObjectAccessRole role) throws InvalidObjectName {
        try {
            return objectAuthService.getObjectACL(path, type, metadata, role);
        } catch (InvalidPath e) {
            throw new InvalidObjectName(path);
        }
    }
    
    /**  Get the Access Control List for a Repository Object (Workspace or Document).
     * 
     * Convenience equivalent to getObjectACL(null, object.getName(), object.getMetadata(), role)
     * 
     * @param object object to get ACL
     * @param role to get ACL for
     * @return An access control list that can be used to determine if a user has the given role for the object
     */
    public Query getObjectACL(NamedRepositoryObject object, ObjectAccessRole role) {
        try {
            return getObjectACL(object.getName(), object.getType(), object.getMetadata(), role);
        } catch (InvalidObjectName err) {
            throw new RuntimeException("Unexpectedly invalid name " + object.getName());
        } 
    }
    
    /**  Get the Access Control List for a Repository Object (DocumentLink).
     * 
     * If getObjectACL(rootId, path).contains(Value.from(userMetadata)) returns true, the user has can perform the given
     * role on the referenced repository object.
     * 
     * @param path path to folder
     * @param documentId of document in folder
     * @param role to get ACL for
     * @return An access control list that can be used to determine if a user has the given role for the object
     * @throws InvalidObjectName if path is not valid
     * @throws InvalidWorkspace if root id is not valid
     * @throws InvalidDocumentId if documentId is not valid
     */
    public Query getObjectACLById(RepositoryPath path, String documentId, ObjectAccessRole role) throws InvalidObjectName {
        return getObjectACL(path.addDocumentId(documentId), RepositoryObject.Type.DOCUMENT_LINK, null, role);
    }

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
    public Query getAccessConstraint(JsonObject userMetadata, RepositoryPath pathTemplate) {
        return objectAuthService.getAccessConstraint(userMetadata, pathTemplate);
    }   
    
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
    public Query getAccessConstraint(JsonObject userMetadata, NamedRepositoryObject searchRoot) {
        return getAccessConstraint(userMetadata, searchRoot.getName());
    }
    
    /** Get metadata for a given user Id.
     * 
     * Metadata may represent application specific permissions in a way entirely defined by the application; the
     * Json format is simply convenient as as generic way to structure data suitable for transfer over the wire.
     * 
     * @param userId
     * @return application specific metadata for that user Id 
     */
    public JsonObject getUserMetadata(String userId) {
        return objectAuthService.getUserMetadata(userId);
    }
}
