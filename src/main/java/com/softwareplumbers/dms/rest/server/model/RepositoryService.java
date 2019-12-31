package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractquery.Query;
import java.util.stream.Stream;

import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;


/**  Represents a DMS repository.
 * 
 * A DMS repository stores documents by reference. A Reference is a pointer 
 * to a particular version of a document. In principle a given reference will
 * only ever return the same document.
 * 
 * Arbitrary meta-data can be stored alongside the document in the form of a Json
 * object; again, the DMS will only ever return the same meta-data for a given reference.
 * 
 * The DMS also implements the concept of a workspace; cataloging a workspace will
 * typically return the most recent version of each document inside it. Documents may
 * also be removed from a workspace. Thus a workspace behaves very like a directory,
 * with the notable exception that documents may exist in multiple workspaces at the 
 * same time.
 * 
 */
public interface RepositoryService {
    
    //-------------- Exception classes --------------//
    
    public static class BaseException extends Exception {
        public BaseException(String description) { super(description); }
    }

	/** Exception type for an invalid document reference */
	public static class InvalidReference extends BaseException {
		private static final long serialVersionUID = 4890221706381667729L;
		public final Reference reference;
		public InvalidReference(Reference reference) {
			super("Invalid reference: " + reference);
			this.reference = reference;
		}
	}

	/** Exception type for an invalid document id */
	public static class InvalidDocumentId extends BaseException {
		private static final long serialVersionUID = 4890221706381667729L;
		public final String id;
		public InvalidDocumentId(String id) {
			super("Invalid document id: " + id);
			this.id = id;
		}
	}

	/** Exception type for an invalid workspace name or id */
	public static class InvalidWorkspace extends BaseException {
		private static final long serialVersionUID = 2546274609900213587L;
		public final String workspace;
		public InvalidWorkspace(String workspace) {
			super("Invalid workspace Id: " + workspace);
			this.workspace = workspace;
		}
		public InvalidWorkspace(QualifiedName workspace) {
			super("Invalid workspace name: " + workspace);
			this.workspace = workspace.toString();
		}
	}
	
	/** Exception type for an invalid name (for either document or workspace) */
	public static class InvalidObjectName extends BaseException {

		private static final long serialVersionUID = 7176066099090799797L;
		public final QualifiedName name;
		public InvalidObjectName(QualifiedName name) {
			super("Invalid name: " + name);
			this.name = name;
		}
	}

	/** Exception type for an invalid workspace state */
	public static class InvalidWorkspaceState extends BaseException {
		private static final long serialVersionUID = -4516622808487331082L;
		public final String workspace;
		public final Workspace.State state;
		public final String other;
		public InvalidWorkspaceState(QualifiedName workspace, Workspace.State state) {
			super("Attempt to change workspace: " + workspace + " in state " + state);
			this.workspace = workspace.toString();
			this.state = state;
			this.other = null;
		}
		public InvalidWorkspaceState(String workspace, Workspace.State state) {
			super("Attempt to change workspace: " + workspace + " in state " + state);
			this.workspace = workspace.toString();
			this.state = state;
			this.other = null;
		}
		public InvalidWorkspaceState(String workspace, String other) {
			super("Workspace: " + workspace + " invalid state: " + other);
			this.workspace = workspace;
			this.state = null;
			this.other = other;
			
		}
	}

    //-------------- Simple Document API ------------//
    
	/** Get a document from a Reference.
 	 * 
	 * @param reference
	 * @return the requested document
	 * @throws InvalidReference if there is no document matching the reference in the repository 
	 */
	public Document getDocument(Reference reference) throws InvalidReference;
    
	/** Create a new document in the repository
	 * 
	 * @param mediaType the type of document
	 * @param stream a supplier function that produces a stream of binary data representing the document
	 * @param metadata a Json object describing the document
	 * @return A Reference object that can later be used to retrieve the document
	 * @throws InvalidWorkspace if workspace does not exist (and createWorkspace is false)
	 * @throws InvalidWorkspaceState if workspace is already closed
	 */
	public Reference createDocument(MediaType mediaType, 
			InputStreamSupplier stream, 
			JsonObject metadata);
    
	/** Update a document in the repository.
	 * <p>
	 * Update a document in the repository. Uses an id rather than a Reference because a Reference
	 * also encompasses the version of the document. This function creates a new version of the document
	 * rather than actually updating the old document.
	 * </p><p>
	 * If any parameter is null, this implies the value will be unchanged by the update operation. Thus,
	 * it is possible to update a document's meta-data without actually updating the document itself
	 * by simply setting the stream parameter to null.
	 * </p><p>
	 * Setting the workspace in this function does not remove the document from any workspace it is already
	 * in; it merely adds it to the specified workspace.
	 * </p><p>
	 * Meta-data passed in to the update operation will be merged with the existing metadata; for example,
	 * if a property is missing in the supplied meta-data object, that property will remain unchanged. To
	 * actually remove meta-data it is necessary to explicitly pass in a JsonObject.NULL value.
	 * </p>
	 * @param id A string id for the document.
	 * @param mediaType (optional) new media type for document
	 * @param stream (optional) a supplier function that produces a stream of binary data representing the document 
	 * @param metadata (optional) a json object describing the document
	 * @return A reference to the new version of the document.
	 * @throws InvalidDocumentId if no document exists with the given id
	 */
	public Reference updateDocument(String id, 
		MediaType mediaType, 
		InputStreamSupplier stream, 
		JsonObject metadata) throws InvalidDocumentId;

	// ---------------- Workspace API -------------- //

    /** Get the current state of a document link
     * 
	 * @param rootId The workspace Id of the root workspace (name is interpreted relative to here). 
	 * @param name the name of the workspace
	 * @return a Workspace object located by name
	 * @throws InvalidWorkspace if rootId is not a valid workspace
     * @throws InvalidObjectName if name is not a valid document name within the root workspace
     */
    public DocumentLink getDocumentLink(String rootId, QualifiedName name) throws InvalidWorkspace, InvalidObjectName;
            
	/** Get a document from an Id and a workspace Id.
	 * 
	 * Gets the most recent version of a document in the given workspace.
 	 * 
	 * @param documentId the Id of the requested document
     * @param path path relative to workspaceId on which we expect to find document
	 * @param workspaceId workspace to get the document from
	 * @return the requested document
     * @throws InvalidWorkspace if the is no workspace matching the id
	 * @throws InvalidDocumentId if there is no document matching the reference in the repository 
     * @throws InvalidObjectName if path does not specify a valid workspace
	 */
	public DocumentLink getDocumentLink(String workspaceId, QualifiedName path, String documentId) throws InvalidWorkspace, InvalidObjectName, InvalidDocumentId;
    
    
	/** Create a new document in the repository.
	 * 
	 * Creates document within the given workspace. The first parts of the given name are the
	 * name of the workspace. The last part of the given name is used as the name of the document
	 * within the workspace.
	 * 
	 * @param rootId the Id of the 'root' workspace
	 * @param documentName the fully qualified name of the document in a workspace
	 * @param mediaType the type of document
	 * @param stream a supplier function that produces a stream of binary data representing the document
	 * @param metadata a Json object describing the document
	 * @param createWorkspace if true, create a new workspace instead of throwing error if workspace does not exist
	 * @return A Reference object that can later be used to retrieve the document
	 * @throws InvalidWorkspace if workspace does not exist (and createWorkspace is false)
	 * @throws InvalidWorkspaceState if workspace is already closed
	 */
	public Reference createDocumentLinkByName(
			String rootId,
			QualifiedName documentName,
			MediaType mediaType, 
			InputStreamSupplier stream, 
			JsonObject metadata, 
			boolean createWorkspace) throws InvalidWorkspace, InvalidObjectName, InvalidWorkspaceState;
    
    
    /** Create a new document in the repository.
	 * 
	 * Creates document within the given workspace. The first parts of the given name are the
	 * name of the workspace. The name of the document is derived from document metadata in
     * a way that guarantees uniqueness but may be implementation-specific.
	 * 
	 * @param rootId the Id of the 'root' workspace
	 * @param workspaceName the fully qualified name of the workspace
	 * @param mediaType the type of document
	 * @param stream a supplier function that produces a stream of binary data representing the document
	 * @param metadata a Json object describing the document
	 * @param createWorkspace if true, create a new workspace instead of throwing error if workspace does not exist
	 * @return A DocumentLink object that can later be used to retrieve the document
	 * @throws InvalidWorkspace if workspace does not exist (and createWorkspace is false)
	 * @throws InvalidWorkspaceState if workspace is already closed
	 */
	public DocumentLink createDocumentLinkAndName(
			String rootId,
			QualifiedName workspaceName,
			MediaType mediaType, 
			InputStreamSupplier stream, 
			JsonObject metadata, 
			boolean createWorkspace) throws InvalidWorkspace, InvalidWorkspaceState;
	
	/** Create a link to an existing document by reference.
	 * 
	 * Creates a link to an existing document with a new name inside a given workspace.
	 * 
     * @param rootId the Id of the 'root' workspace
     * @param documentName the fully qualified name of the document in a workspace
	 * @param reference Reference of document to link to
	 * @param createWorkspace controls if parent workspace will be created (if it doesn't exist)
	 * @throws InvalidObject name if replaceExisting is false and an object with the given name already exists
     * @throws InvalidWorkspace if workspace does not exist
     * @throws InvalidWorkspaceState if workspace is already closed
	 * @throws InvalidReference 
	 */
	public DocumentLink createDocumentLinkByName(
	        String rootId,
	        QualifiedName documentName,
	        Reference reference,
	        boolean createWorkspace) throws InvalidWorkspace, InvalidObjectName, InvalidWorkspaceState, InvalidReference;
	
	/** Create a link to an existing document by reference.
	 * 
	 * Creates a link to an existing document with a new name inside a given workspace. The name is generated
     * and guaranteed unique in the workspace.
	 * 
     * @param rootId the Id of the 'root' workspace
     * @param workspaceName the fully qualified name of the workspace
	 * @param reference Reference of document to link to
	 * @param createWorkspace controls if parent workspace will be created (if it doesn't exist)
     * @param returnExisting controls if the name of an existing link to the document will be returned, or an error generated
     * @throws InvalidWorkspace if workspace does not exist
     * @throws InvalidWorkspaceState if workspace is already closed
	 * @throws InvalidReference if the supplied reference is not valid
     * @return the new DocumentLink object
	 */
	public DocumentLink createDocumentLink(
	        String rootId,
	        QualifiedName workspaceName,
	        Reference reference,
	        boolean createWorkspace, boolean returnExisting) throws InvalidWorkspace, InvalidWorkspaceState, InvalidReference;
	
	/** Update a document in the repository.
	 * 
	 * Updates document within the given workspace. The first parts of the given name are the
	 * name of the workspace. The last part of the given name is used as the name of the document
	 * within the workspace.
	 * 
	 * @param rootId The workspace Id of the root workspace (name is interpreted relative to here). 
	 * @param documentName the fully qualified name of the document in a workspace
	 * @param mediaType the type of document
	 * @param stream a supplier function that produces a stream of binary data representing the document
	 * @param metadata a Json object describing the document
	 * @param createWorkspace if true, create a new workspace instead of throwing error if workspace does not exist
     * @param createDocument if true, create a new document instead of throwing error if document does not exist
	 * @return A Reference object that can later be used to retrieve the document
	 * @throws InvalidWorkspace if workspace does not exist (and createWorkspace is false)
	 * @throws InvalidWorkspaceState if workspace is already closed
     * @throws InvalidObjectName if document does not exist in workspace (and createDocument is false)
	 */
	public Reference updateDocumentLinkByName(
			String rootId,
			QualifiedName documentName,
			MediaType mediaType, 
			InputStreamSupplier stream, 
			JsonObject metadata, 
			boolean createWorkspace,
			boolean createDocument) throws InvalidWorkspace, InvalidObjectName, InvalidWorkspaceState;
	
    /** Update or create a link to an existing document by reference.
     * 
     * Creates a link to an existing document with a new name inside a given workspace.
     * 
     * @param rootId the Id of the 'root' workspace
     * @param documentName the fully qualified name of the document in a workspace
     * @param reference Reference of document to link to
     * @param createWorkspace Allow workspace creation if workspace dos not exist
     * @param createLink if true, create a new document link instead of throwing error if document link does not exist
     * @throws InvalidWorkspace if document link or workspace does not exist and createWorkspace is false
     * @throws InvalidWorkspaceState if workspace is already closed
     * @throws InvalidObjectName if document does not exist in workspace (and createDocument is false)
     * @throws InvalidReference if reference does not refer to a valid document
     * 
     */
    public void updateDocumentLinkByName(
            String rootId,
            QualifiedName documentName,
            Reference reference,
            boolean createWorkspace,
            boolean createLink) throws InvalidWorkspace, InvalidObjectName, InvalidWorkspaceState, InvalidReference;

    
	/** Catalog a repository.
	 * 
	 * Returns information (including reference and meta-data) about documents in the repository 

	 * Results can be filtered using a 'Query' object to filter for documents with metadata matching
	 * the specified query.
	 * 
	 * By default catalog operation works on only the most recent version of any document in a workspace
	 * or repository. If the most recent version does not match the filter, no reference to that document
	 * will be returned. If the 'searchHistory' parameter is set to true, then all versions of a document
	 * will be searched and a reference to the most recent version of the document matching the filter 
	 * will be returned.
	 * 
	 * @param filter filter object to match meta-data
	 * @param searchHistory indicate whether to search document history for a match
	 * @return a stream of Info objects with the results of the search
	 */
	public Stream<Document> catalogue(Query filter, boolean searchHistory);
	
	/** Catalog a workspace.
	 * <p>
	 * Returns information (including reference and meta-data) about documents in a workspace 
	 * (or the entire repository if the workspace is set to null).
	 * </p><p>
	 * Results can be filtered using a 'Query' object to filter for documents with metadata matching
	 * the specified query.
	 * </p><p>
	 * By default catalog operation works on only the most recent version of any document in a workspace
	 * or repository. If the most recent version does not match the filter, no reference to that document
	 * will be returned. If the 'searchHistory' parameter is set to true, then all versions of a document
	 * will be searched and a reference to the most recent version of the document matching the filter 
	 * will be returned.
	 * </p>
	 * @param filter filter object to match meta-data
	 * @param workspaceId id to catalogue
	 * @param searchHistory indicate whether to search document history for a match
	 * @return a stream of Info objects with the results of the search
	 * @throws InvalidWorkspace if workspace does not exist (and createWorkspace is false)
	 */
	public Stream<NamedRepositoryObject> catalogueById(String workspaceId, Query filter, boolean searchHistory) throws InvalidWorkspace;

	/** Catalog a workspace.
	 * <p>
	 * Returns information (including reference and meta-data) about documents in a workspace 
	 * (or the entire repository if the workspace is set to null).
	 * </p><p>
	 * Results can be filtered using a 'Query' object to filter for documents with metadata matching
	 * the specified query. The path provided may also contain the wildcard characters * and ?. In this
     * case the operation returns all objects which have a name fully matching the path. In essence, the
     * path 'abc/def' (with no wildcards) returns the exact same result as 'abc/def/*'.
	 * </p><p>
	 * By default catalog operation works on only the most recent version of any document in a workspace
	 * or repository. If the most recent version does not match the filter, no reference to that document
	 * will be returned. If the 'searchHistory' parameter is set to true, then all versions of a document
	 * will be searched and a reference to the most recent version of the document matching the filter 
	 * will be returned.
	 * </p>
	 * @param rootId The workspace Id of the root workspace (name is interpreted relative to here). 
	 * @param filter filter object to match meta-data
	 * @param path search path from root workspace id
	 * @param searchHistory indicate whether to search document history for a match
	 * @return a stream of objects with the results of the search
	 * @throws InvalidWorkspace if workspace does not exist (and createWorkspace is false)
	 */
	public Stream<NamedRepositoryObject> catalogueByName(String rootId, QualifiedName path, Query filter, boolean searchHistory) throws InvalidWorkspace;

	/** Catalog history of a given document.
	 * 
	 * Get all versions of the given document earlier than the given reference,
	 * that match some criteria.
	 * 
	 * @param ref Document reference to search for history
	 * @param filter object to match meta-data
	 * @return a Stream of Info objects with the results of the search
	 * @throws InvalidReference no document exists with the given id
	 */
	public Stream<Document> catalogueHistory(Reference ref, Query filter) throws InvalidReference;
		
	/** Create a workspace 
	 * 
	 * Workspaces may be open, finalized or closed; catalog operations on a closed or finalized
	 * workspace work on the versions of documents that were current at the time the workspace
	 * was closed. Operations that create or update documents in a finalized workspace will create
	 * new versions of the document but will not change the workspace; attempting such operations
	 * on a closed workspace will throw an error.
	 * 
	 * @param rootId base location id
	 * @param name name of workspace to create relative to base
	 * @param state Initial/Updated state of workspace (optional)
	 * @param metadata additional info describing the workspace
	 * @return the id of the created workspace
	 * @throws InvalidWorkspace if createWorkspace is false and workspace does not already exist
	 */
	public String createWorkspaceByName(String rootId, QualifiedName name, Workspace.State state, JsonObject metadata) throws InvalidWorkspace;
	
	/** Create a workspace 
	 * 
	 * Workspaces may be open, finalized or closed; catalog operations on a closed or finalized
	 * workspace work on the versions of documents that were current at the time the workspace
	 * was closed. Operations that create or update documents in a finalized workspace will create
	 * new versions of the document but will not change the workspace; attempting such operations
	 * on a closed workspace will throw an error.
	 * 
	 * @param id Id of workspace to create (optional - id will be generated if not supplied)
	 * @param state Initial/Updated state of workspace (optional)
	 * @param metadata additional info describing the workspace
	 * return the id of the created workspace
	 * @throws InvalidWorkspace if createWorkspace is false and workspace does not already exist
	 */
	public String createWorkspaceById(String id, Workspace.State state, JsonObject metadata) throws InvalidWorkspace;

	
	/** Create or update a workspace 
	 * 
	 * A workspace may be specified by id 
	 * 
	 * If name or state is null, the relevant attribute in the workspace is not updated.
	 * 
	 * Creates a workspace with the given id if one does not exist. Otherwise,
	 * update the state of the workspace.
	 * 
	 * Workspaces may be open, finalized or closed; catalog operations on a closed or finalized
	 * workspace work on the versions of documents that were current at the time the workspace
	 * was closed. Operations that create or update documents in a finalized workspace will create
	 * new versions of the document but will not change the workspace; attempting such operations
	 * on a closed workspace will throw an error.
	 * 
	 * @param id id of workspace to create/update
	 * @param name new name of workspace
	 * @param state Initial/Updated state of workspace
	 * @param metadata workspace metadata
	 * @param createWorkspace create workspace with given name if it does not already exist
	 * @return the id of the created/updated workspace
	 * @throws InvalidWorkspace if createWorkspace is false and workspace does not already exisit
	 */
	public String updateWorkspaceById(String id, QualifiedName name, Workspace.State state, JsonObject metadata, boolean createWorkspace) throws InvalidWorkspace;

	/** Create or update a workspace 
	 * 
	 * A workspace may be specified by id 
	 * 
	 * If name or state is null, the relevant attribute in the workspace is not updated.
	 * 
	 * Creates a workspace with the given id if one does not exist. Otherwise,
	 * update the state of the workspace.
	 * 
	 * Workspaces may be open, finalized or closed; catalog operations on a closed or finalized
	 * workspace work on the versions of documents that were current at the time the workspace
	 * was closed. Operations that create or update documents in a finalized workspace will create
	 * new versions of the document but will not change the workspace; attempting such operations
	 * on a closed workspace will throw an error.
	 * 
	 * @param rootId The workspace Id of the root workspace (name is interpreted relative to here). 
	 * @param name name of workspace to create/update
	 * @param newName new name for workspace
	 * @param state Initial/Updated state of workspace
	 * @param metadata workspace metadata
	 * @param createWorkspace create workspace with given name if it does not already exist
	 * @return the id of the created/updated workspace
	 * @throws InvalidWorkspace if createWorkspace is false and workspace does not already exist
	 */
	public String updateWorkspaceByName(String rootId, QualifiedName name, QualifiedName newName, Workspace.State state, JsonObject metadata, boolean createWorkspace) throws InvalidWorkspace;

	/** Get current state of workspace, document, or document part
     * 
     * Note: this method should not be called by preference; one of the type-specific methods getWorkspaceByName,
     * or getDocumentLinkByName, should be used where possible. This is because retrieving
     * a repository object without knowing its type may be significantly more expensive.
	 * 
	 * @param rootId The workspace Id of the root workspace (name is interpreted relative to here). 
	 * @param name the name of the workspace, document link, or document part
	 * @return a named repository object describing the object located by name
	 * @throws InvalidWorkspace if rootId is not a valid workspace
     * @throws InvalidObjectName if name is not a valid object name within the root workspace
	 */
	public NamedRepositoryObject getObjectByName(String rootId, QualifiedName name) throws InvalidWorkspace, InvalidObjectName;

    /** Get the current state of a workspace
     * 
	 * @param rootId The workspace Id of the root workspace (name is interpreted relative to here). 
	 * @param name the name of the workspace
	 * @return a named repository object describing the object located by name
	 * @throws InvalidWorkspace if rootId is not a valid workspace
     * @throws InvalidObjectName if name is not a valid workspace name within the root workspace
     */
    public Workspace getWorkspaceByName(String rootId, QualifiedName name) throws InvalidWorkspace, InvalidObjectName;  
    
	/** Get current state of workspace 
	 * 
	 * @param id the id of the requested workspace
	 * @return a Workspace object containing current workspace state
	 * @throws InvalidWorkspace if workspace does not already exist
	 */
	public Workspace getWorkspaceById(String id) throws InvalidWorkspace;

	/** Remove a document from a workspace
	 *   
	 *  Removing a document from an open workspace will remove that document
	 *  from a catalog search of the workspace, unless the searchHistory option
	 *  is set.
	 *  
	 * @param root_id Root workspace id
     * @param path Path to workspace 
	 * @param doc_id document id to remove from workspace
	 * @throws InvalidWorkspace if workspace does not exist
	 * @throws InvalidDocumentId if document is not in workspace
	 * @throws InvalidWorkspaceState if workspace is not open
	 */
	public void deleteDocument(String workspace_id, QualifiedName path, String doc_id) throws InvalidWorkspace, InvalidDocumentId, InvalidWorkspaceState;
	
	/** Remove a document from a workspace
	 *   
	 *  Removing a document from an open workspace will remove that document
	 *  from a catalog search of the workspace, unless the searchHistory option
	 *  is set.
	 *  
	 * @param rootId The workspace Id of the root workspace (name is interpreted relative to here). 
	 * @param objectName Object id to remove from its parent workspace
	 * @throws InvalidWorkspace if workspace does not exist
	 * @throws InvalidDocumentId if document is not in workspace
	 * @throws InvalidWorkspaceState if workspace is not open
	 */
	public void deleteObjectByName(String rootId, QualifiedName objectName) throws InvalidWorkspace, InvalidObjectName, InvalidWorkspaceState;

	/** *  List all the workspaces of which the given document is a member.Will return an empty stream if no workspace is found.
	 * 
	 * 
	 * @param id Id of document
	 * @param pathFilter wildcard path which can be used to cut down number of responses
     * @param filter
	 * @return A Stream of DocumentLink objects, each of which links this document to a workspace.
     * @throws InvalidDocumentId if if does not reference a valid document
	 */
	public Stream<DocumentLink> listWorkspaces(String id, QualifiedName pathFilter, Query filter) throws InvalidDocumentId;
}