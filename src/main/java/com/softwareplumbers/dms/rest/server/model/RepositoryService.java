package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.common.abstractquery.Cube;
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

	/** Exception type for an invalid workspace name */
	public static class InvalidWorkspaceName extends Exception {
		private static final long serialVersionUID = 2546274609900213587L;
		public final String workspace;
		public InvalidWorkspaceName(String workspace) {
			super("Invalid workspace: " + workspace);
			this.workspace = workspace;
		}
	}

	/** Exception type for an invalid workspace state */
	public static class InvalidWorkspaceState extends Exception {
		private static final long serialVersionUID = -4516622808487331082L;
		public final String workspace;
		public final Workspace.State state;
		public InvalidWorkspaceState(String workspace, Workspace.State state) {
			super("Attempt to change workspace: " + workspace + " in state " + state);
			this.workspace = workspace;
			this.state = state;
		}
	}

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
	 * @param workspace An optional string identifying a workspace in which to place the document
	 * @param createWorkspace if true, create a new workspace instead of throwing error if workspace does not exist
	 * @return A Reference object that can later be used to retrieve the document
	 * @throws InvalidWorkspaceName if workspace does not exist (and createWorkspace is false)
	 * @throws InvalidWorkspaceState if workspace is already closed
	 */
	public Reference createDocument(MediaType mediaType, 
			InputStreamSupplier stream, 
			JsonObject metadata, 
			String workspace,
			boolean createWorkspace) throws InvalidWorkspaceName, InvalidWorkspaceState;
	
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
	 * @param workspace (optional) string identifying the workspace in which to place the document
	 * @return A reference to the new version of the document.
	 * @throws InvalidWorkspaceName if workspace does not exist (and createWorkspace is false)
	 * @throws InvalidWorkspaceState if workspace is already closed
	 * @throws InvalidDocumentId if no document exists with the given id
	 */
	public Reference updateDocument(String id, 
		MediaType mediaType, 
		InputStreamSupplier stream, 
		JsonObject metadata, 
		String workspace,
		boolean createWorkspace) throws InvalidDocumentId, InvalidWorkspaceName, InvalidWorkspaceState;
	
	/** Catalog a repository or a  workspace.
	 * <p>
	 * Returns information (including reference and meta-data) about documents in a workspace 
	 * (or the entire repository if the workspace is set to null).
	 * </p><p>
	 * Results can be filtered using a 'Cube' object to filter for documents with metadata matching
	 * the specified query.
	 * </p><p>
	 * By default catalog operation works on only the most recent version of any document in a workspace
	 * or repository. If the most recent version does not match the filter, no reference to that document
	 * will be returned. If the 'searchHistory' parameter is set to true, then all versions of a document
	 * will be searched and a reference to the most recent version of the document matching the filter 
	 * will be returned.
	 * </p>
	 * @param filter filter object to match meta-data
	 * @param workspace id of workspace to catalog 
	 * @param searchHistory indicate whether to search document history for a match
	 * @return a stream of Info objects with the results of the search
	 * @throws InvalidWorkspaceName if workspace does not exist (and createWorkspace is false)
	 */
	public Stream<Info> catalogue(String workspace, Cube filter, boolean searchHistory) throws InvalidWorkspaceName;
	
	/** Catalog history of a given document.
	 * 
	 * Get all versions of the given document earlier than the given reference,
	 * that match some criteria.
	 * 
	 * @param reference Document reference to search for history
	 * @param filter object to match meta-data
	 * @return a Stream of Info objects with the results of the search
	 * @throws InvalidDocumentId if no document exists with the given id
	 */
	public Stream<Info> catalogueHistory(Reference ref, Cube filter) throws InvalidReference;
	
	/** Catalog all the parts of a document.
	 * 
	 * The exact meaning of this call may depend on the type of document. A zip file,
	 * for example, will typically be composed of a number of separate files; this method
	 * will return a stream of Info objects containing a reference to each file in the archive,
	 * and metadata (which would include the file name).
	 * 
	 * @param ref
	 * @param filter
	 * @return
	 * @throws InvalidReference
	 */
	public Stream<Info> catalogueParts(Reference ref, Cube filter) throws InvalidReference;
	
	/** Create or update a workspace 
	 * <p>
	 * Creates a workspace with the given id if one does not exist. Otherwise,
	 * update the state of the workspace.
	 * </p><p>
	 * Workspaces may be open, finalized or closed; catalog operations on a closed or finalized
	 * workspace work on the versions of documents that were current at the time the workspace
	 * was closed. Operations that create or update documents in a finalized workspace will create
	 * new versions of the document but will not change the workspace; attempting such operations
	 * on a closed workspace will throw an error.
	 * </p>
	 * @param workspace name of workspace to create/update
	 * @param state Initial/Updated state of workspace
	 * @param createWorkspace create workspace with given name if it does not already exist
	 * @throws InvalidWorkspaceName if createWorkspace is false and workspace does not already exisit
	 */
	public void updateWorkspace(String workspace, Workspace.State state, boolean createWorkspace) throws InvalidWorkspaceName;
	
	/** Get current state of workspace 
	 * 
	 * @param workspace the name of the workspace
	 * @return a Workspace object containing current workspace state
	 * @throws InvalidWorkspaceName if workspace does not already exist
	 */
	public Workspace getWorkspace(String workspace) throws InvalidWorkspaceName;
	
	/** Remove a document from a workspace
	 *  <p> 
	 *  Removing a document from an open workspace will remove that document
	 *  from a catalog search of the workspace, unless the searchHistory option
	 *  is set.
	 *  </p>
	 * @param workspace
	 * @param id document id to remove from workspace
	 * @throws InvalidWorkspaceName if workspace does not exist
	 * @throws InvalidDocumentId if document is not in workspace
	 * @throws InvalidWorkspaceState if workspace is not open
	 */
	public void deleteDocument(String workspace, String id) throws InvalidWorkspaceName, InvalidDocumentId, InvalidWorkspaceState;
};
