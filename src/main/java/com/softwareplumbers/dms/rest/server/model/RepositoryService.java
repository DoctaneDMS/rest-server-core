package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractquery.ObjectConstraint;
import java.util.stream.Stream;

import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;


/**  Represents a DMS repository.
 * <p>
 * A DMS repository stores documents by reference. A Reference is a pointer 
 * to a particular version of a document. In principle a given reference will
 * only ever return the same document.
 * </p><p>
 * Arbitrary meta-data can be stored alongside the document in the form of a Json
 * object; again, the DMS will only ever return the same meta-data for a given reference.
 * </p><p>
 * The DMS also implements the concept of a workspace; cataloging a workspace will
 * typically return the most recent version of each document inside it. Documents may
 * also be removed from a workspace. Thus a workspace behaves very like a directory,
 * with the notable exception that documents may exist in multiple workspaces at the 
 * same time.
 * </p>
 */
public interface RepositoryService {

	/** Exception type for an invalid document reference */
	public static class InvalidReference extends Exception {
		private static final long serialVersionUID = 4890221706381667729L;
		public final Reference reference;
		public InvalidReference(Reference reference) {
			super("Invalid reference: " + reference);
			this.reference = reference;
		}
	}

	/** Exception type for an invalid document id */
	public static class InvalidDocumentId extends Exception {
		private static final long serialVersionUID = 4890221706381667729L;
		public final String id;
		public InvalidDocumentId(String id) {
			super("Invalid document id: " + id);
			this.id = id;
		}
	}

	/** Exception type for an invalid workspace name or id */
	public static class InvalidWorkspace extends Exception {
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
	public static class InvalidObjectName extends Exception {

		private static final long serialVersionUID = 7176066099090799797L;
		public final QualifiedName name;
		public InvalidObjectName(QualifiedName name) {
			super("Invalid name: " + name);
			this.name = name;
		}
	}

	/** Exception type for an invalid workspace state */
	public static class InvalidWorkspaceState extends Exception {
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
	
	/** Generic ID for root workspace */
	public static final String ROOT_WORKSPACE_ID = null;

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
	 * @param workspaceId An optional string identifying a workspace in which to place the document
	 * @param createWorkspace if true, create a new workspace instead of throwing error if workspace does not exist
	 * @return A Reference object that can later be used to retrieve the document
	 * @throws InvalidWorkspace if workspace does not exist (and createWorkspace is false)
	 * @throws InvalidWorkspaceState if workspace is already closed
	 */
	public Reference createDocument(MediaType mediaType, 
			InputStreamSupplier stream, 
			JsonObject metadata, 
			String workspaceId,
			boolean createWorkspace) throws InvalidWorkspace, InvalidWorkspaceState;
	
	/** Create a new document in the repository.
	 * 
	 * Creates document within the given workspace. The first parts of the given name are the
	 * name of the workspace. The last part of the given name is used as the name of the document
	 * within the workspace.
	 * 
	 * @param documentName the fully qualified name of the document in a workspace
	 * @param mediaType the type of document
	 * @param stream a supplier function that produces a stream of binary data representing the document
	 * @param metadata a Json object describing the document
	 * @param createWorkspace if true, create a new workspace instead of throwing error if workspace does not exist
	 * @return A Reference object that can later be used to retrieve the document
	 * @throws InvalidWorkspace if workspace does not exist (and createWorkspace is false)
	 * @throws InvalidWorkspaceState if workspace is already closed
	 */
	public Reference createDocumentByName(
			String rootId,
			QualifiedName documentName,
			MediaType mediaType, 
			InputStreamSupplier stream, 
			JsonObject metadata, 
			boolean createWorkspace) throws InvalidWorkspace, InvalidObjectName, InvalidWorkspaceState;
	
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
	 * @return A Reference object that can later be used to retrieve the document
	 * @throws InvalidWorkspace if workspace does not exist (and createWorkspace is false)
	 * @throws InvalidWorkspaceState if workspace is already closed
	 */
	public Reference updateDocumentByName(
			String rootId,
			QualifiedName documentName,
			MediaType mediaType, 
			InputStreamSupplier stream, 
			JsonObject metadata, 
			boolean createWorkspace,
			boolean createDocument) throws InvalidWorkspace, InvalidObjectName, InvalidWorkspaceState;
	
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
	 * @param workspaceId (optional) string identifying the workspace in which to place the document
	 * @return A reference to the new version of the document.
	 * @throws InvalidWorkspace if workspace does not exist (and createWorkspace is false)
	 * @throws InvalidWorkspaceState if workspace is already closed
	 * @throws InvalidDocumentId if no document exists with the given id
	 */
	public Reference updateDocument(String id, 
		MediaType mediaType, 
		InputStreamSupplier stream, 
		JsonObject metadata, 
		String workspaceId,
		boolean createWorkspace) throws InvalidDocumentId, InvalidWorkspace, InvalidWorkspaceState;
	
	
	/** Catalog a repository.
	 * 
	 * Returns information (including reference and meta-data) about documents in the repository 

	 * Results can be filtered using a 'ObjectConstraint' object to filter for documents with metadata matching
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
	public Stream<Info> catalogue(ObjectConstraint filter, boolean searchHistory);
	
	/** Catalog a workspace.
	 * <p>
	 * Returns information (including reference and meta-data) about documents in a workspace 
	 * (or the entire repository if the workspace is set to null).
	 * </p><p>
	 * Results can be filtered using a 'ObjectConstraint' object to filter for documents with metadata matching
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
	public Stream<Info> catalogueById(String workspaceId, ObjectConstraint filter, boolean searchHistory) throws InvalidWorkspace;

	/** Catalog a workspace.
	 * <p>
	 * Returns information (including reference and meta-data) about documents in a workspace 
	 * (or the entire repository if the workspace is set to null).
	 * </p><p>
	 * Results can be filtered using a 'ObjectConstraint' object to filter for documents with metadata matching
	 * the specified query. The path provided may contain the wildcard characters * and ?. 
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
	 * @return a stream of Info objects with the results of the search
	 * @throws InvalidWorkspace if workspace does not exist (and createWorkspace is false)
	 */
	public Stream<Info> catalogueByName(String rootId, QualifiedName path, ObjectConstraint filter, boolean searchHistory) throws InvalidWorkspace;

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
	public Stream<Info> catalogueHistory(Reference ref, ObjectConstraint filter) throws InvalidReference;
	
	/** Catalog all the parts of a document.
	 * 
	 * The exact meaning of this call may depend on the type of document. A zip file,
	 * for example, will typically be composed of a number of separate files; this method
	 * will return a stream of Info objects containing a reference to each file in the archive,
	 * and metadata (which would include the file name).
	 * 
	 * @param ref
	 * @param filter
	 * @return a stream of Info objects relating to the selected parts of the document
	 * @throws InvalidReference
	 */
	public Stream<Info> catalogueParts(Reference ref, ObjectConstraint filter) throws InvalidReference;
	
	
	/** Create a workspace 
	 * 
	 * Workspaces may be open, finalized or closed; catalog operations on a closed or finalized
	 * workspace work on the versions of documents that were current at the time the workspace
	 * was closed. Operations that create or update documents in a finalized workspace will create
	 * new versions of the document but will not change the workspace; attempting such operations
	 * on a closed workspace will throw an error.
	 * 
	 * @param name name of workspace to create (optional)
	 * @param state Initial/Updated state of workspace (optional)
	 * return the id of the created workspace
	 * @throws InvalidWorkspace if createWorkspace is false and workspace does not already exist
	 */
	public String createWorkspace(QualifiedName name, Workspace.State state, JsonObject metadata) throws InvalidWorkspace;
	
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
	 * @throws InvalidWorkspace if createWorkspace is false and workspace does not already exisit
	 */
	public String updateWorkspaceByName(String rootId, QualifiedName name, QualifiedName newName, Workspace.State state, JsonObject metadata, boolean createWorkspace) throws InvalidWorkspace;

	/** Get current state of workspace or document 
	 * 
	 * @param rootId The workspace Id of the root workspace (name is interpreted relative to here). 
	 * @param name the name of the workspace
	 * @return a Workspace object containing current workspace state
	 * @throws InvalidWorkspace if workspace does not already exist
	 */
	public RepositoryObject getObjectByName(String rootId, QualifiedName name) throws InvalidWorkspace, InvalidObjectName;
	
	/** Get current state of workspace 
	 * 
	 * @param workspace the id of the workspace
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
	 * @param workspace_id
	 * @param doc_id document id to remove from workspace
	 * @throws InvalidWorkspace if workspace does not exist
	 * @throws InvalidDocumentId if document is not in workspace
	 * @throws InvalidWorkspaceState if workspace is not open
	 */
	public void deleteDocument(String workspace_id, String doc_id) throws InvalidWorkspace, InvalidDocumentId, InvalidWorkspaceState;
	
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

	/** List all the workspaces of which the given document is a member.
	 * 
	 * Will return an empty stream if no workspace is found.
	 * 
	 * @param id Id of document
	 * @return A Stream of workspaces, all of which the given doc is in.
	 */
	public Stream<Workspace> listWorkspaces(String id);
};
