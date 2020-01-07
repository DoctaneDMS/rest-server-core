package com.softwareplumbers.dms;

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
    
    public static class BaseRuntimeException extends RuntimeException {
        public BaseRuntimeException(BaseException e) { super(e); }
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
		public final String rootId;
        public final QualifiedName workspace;
		public InvalidWorkspace(String rootId, QualifiedName workspace) {
			super("Invalid workspace name: " + rootId + ":" + workspace);
			this.workspace = workspace;
            this.rootId = rootId;
		}
        public InvalidWorkspace(String rootId) {
            this(rootId, QualifiedName.ROOT);
        }
	}
	
	/** Exception type for an invalid name (for either document or workspace) */
	public static class InvalidObjectName extends BaseException {

		private static final long serialVersionUID = 7176066099090799797L;
        public final String rootId;
		public final QualifiedName name;
		public InvalidObjectName(String rootId, QualifiedName name) {
			super("Invalid name: " + rootId + ":" + name);
			this.name = name;
            this.rootId = rootId;
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
			this.workspace = workspace;
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
    public DocumentLink getDocumentLinkByName(String rootId, QualifiedName name) throws InvalidWorkspace, InvalidObjectName;

	/** Get a document from an Id and a workspace Id.
	 * 
	 * Gets the most recent version of a document in the given workspace.
     * 
     * Defined as equivalent to getDocuemntLink(workspace.getId(), QualifiedName.ROOT, documentId)
 	 * 
	 * @param documentName the name of the requested document
     * @param workspace Workspace from which to fetch document
	 * @return the requested document
     * @throws InvalidWorkspace if the is no workspace matching the id
	 * @throws InvalidDocumentId if there is no document matching the reference in the repository 
     * @throws InvalidObjectName if path does not specify a valid workspace
	 */    
    public default DocumentLink getDocumentLinkByName(Workspace workspace, String documentName) throws InvalidWorkspace, InvalidObjectName, InvalidDocumentId {
        return getDocumentLinkByName(workspace.getId(), QualifiedName.ROOT.add(documentName));
    }
    
    /** Get a document from an Id and a workspace Id.
	 * 
	 * Gets the most recent version of a document in the given workspace.
     * 
     * Defined as equivalent to getDocuemntLink(workspace.getId(), QualifiedName.ROOT, documentId)
 	 * 
	 * @param documentId the Id of the requested document
     * @param workspace Workspace from which to fetch document
	 * @return the requested document
     * @throws InvalidWorkspace if the is no workspace matching the id
	 * @throws InvalidDocumentId if there is no document matching the reference in the repository 
     * @throws InvalidObjectName if path does not specify a valid workspace
	 */    
    public default DocumentLink getDocumentLinkById(Workspace workspace, String documentId) throws InvalidWorkspace, InvalidObjectName, InvalidDocumentId {
        return getDocumentLink(workspace.getId(), QualifiedName.ROOT, documentId);
    }
    
    
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
    
	/** Create a new document in the repository.Creates document within the given workspace.
	 * 
	 * @param workspace the workspace in which to create the document link
	 * @param documentName the name of the document in a workspace
	 * @param mediaType the type of document
	 * @param stream a supplier function that produces a stream of binary data representing the document
	 * @param metadata a Json object describing the document
	 * @return A DocumentLink object that can later be used to retrieve the document
	 * @throws InvalidWorkspace if workspace does not exist (and createWorkspace is false)
     * @throws InvalidObjectName if document with name already exists in workspace
	 * @throws InvalidWorkspaceState if workspace is already closed
	 */
    public default DocumentLink createDocumentLinkByName(Workspace workspace, String documentName, MediaType mediaType, 
			InputStreamSupplier stream, 
			JsonObject metadata) throws InvalidWorkspace, InvalidObjectName, InvalidWorkspaceState {
        return createDocumentLinkByName(workspace.getId(), QualifiedName.of(documentName), mediaType, stream, metadata, false);
    }
    
	/** Create a new document in the repository.Creates document within the given workspace.
	 * 
	 * The first parts of the given name are the
 name of the workspace. The last part of the given name is used as the name of the document
 within the workspace.
	 * 
	 * @param rootId the Id of the 'root' workspace
	 * @param documentName the fully qualified name of the document in a workspace
	 * @param mediaType the type of document
	 * @param stream a supplier function that produces a stream of binary data representing the document
	 * @param metadata a Json object describing the document
	 * @param createWorkspace if true, create a new workspace instead of throwing error if workspace does not exist
	 * @return A Reference object that can later be used to retrieve the document
	 * @throws InvalidWorkspace if workspace does not exist (and createWorkspace is false)
     * @throws InvalidObjectName if document with name already exists in workspace
	 * @throws InvalidWorkspaceState if workspace is already closed
	 */
	public DocumentLink createDocumentLinkByName(
			String rootId,
			QualifiedName documentName,
			MediaType mediaType, 
			InputStreamSupplier stream, 
			JsonObject metadata, 
			boolean createWorkspace) throws InvalidWorkspace, InvalidObjectName, InvalidWorkspaceState;

	/** Create a new document in the repository. Creates document within the given workspace.
	 * 
	 * @param workspace the workspace in which to create the document link
	 * @param mediaType the type of document
	 * @param stream a supplier function that produces a stream of binary data representing the document
	 * @param metadata a Json object describing the document
	 * @return A DocumentLink object that can later be used to retrieve the document
	 * @throws InvalidWorkspace if workspace does not exist (and createWorkspace is false)
     * @throws InvalidObjectName if document with name already exists in workspace
	 * @throws InvalidWorkspaceState if workspace is already closed
	 */
    public default DocumentLink createDocumentLinkAndName(Workspace workspace, MediaType mediaType, 
			InputStreamSupplier stream, 
			JsonObject metadata) throws InvalidWorkspace, InvalidObjectName, InvalidWorkspaceState {
        return createDocumentLinkAndName(workspace.getId(), QualifiedName.ROOT, mediaType, stream, metadata, false);
    }    
    
    /** Create a new document in the repository.
	 * 
	 * Creates document within the given workspace. The first parts of the given name are the
	 * name of the workspace. The name of the document is derived from document metadata in
     * a way that guarantees uniqueness but may be implementation-specific.
	 * 
	 * @param rootId the Id of the 'root' workspace
	 * @param documentName the fully qualified name of the document link
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
			QualifiedName documentName,
			MediaType mediaType, 
			InputStreamSupplier stream, 
			JsonObject metadata, 
			boolean createWorkspace) throws InvalidWorkspace, InvalidWorkspaceState;
    
    
    public default DocumentLink createDocumentLinkAndName(Workspace workspace, Reference reference, boolean returnExisting) throws InvalidWorkspaceState, InvalidObjectName, InvalidReference {
        try {
            return createDocumentLinkAndName(workspace.getId(), QualifiedName.ROOT, reference, false, returnExisting);
        } catch (InvalidWorkspace e) {
            throw new BaseRuntimeException(e);
        }
    }
    
    public DocumentLink createDocumentLinkAndName(
    		String rootId,
			QualifiedName workspaceName,
            Reference reference,
            boolean createWorkspace,
            boolean returnExisting
	) throws InvalidWorkspace, InvalidWorkspaceState, InvalidReference;
    
    /** Create a link to an existing document by reference.
	 * 
	 * Creates a link to an existing document with a new name inside a given workspace.
	 * 
     * @param workspace the Id of the workspace in which to create the document
     * @param documentName the name of the document in the workspace
	 * @param reference Reference of document to link to
	 * @throws InvalidObjectName name if an object with the given name already exists
     * @throws InvalidWorkspaceState if workspace is already closed
	 * @throws InvalidReference 
     * @return the new DocumentLink object
	 */
    public default DocumentLink createDocumentLinkByName(
	        Workspace workspace,
	        String documentName,
	        Reference reference) throws InvalidObjectName, InvalidWorkspaceState, InvalidReference {
        try {
            return createDocumentLinkByName(workspace.getId(), QualifiedName.of(documentName), reference, false);
        } catch (InvalidWorkspace e) {
            throw new BaseRuntimeException(e);
        }
    }
		
	/** Create a link to an existing document by reference.
     * 
     * Creates a link to an existing document with a new name inside a given workspace
	 * 
     * @param rootId the Id of the 'root' workspace
     * @param workspaceName the fully qualified name of the document in the workspace
	 * @param reference Reference of document to link to
	 * @param createWorkspace controls if parent workspace will be created (if it doesn't exist)
     * @throws com.softwareplumbers.dms.RepositoryService.InvalidWorkspace
     * @throws com.softwareplumbers.dms.RepositoryService.InvalidReference
     * @throws InvalidObjectName if name already taken
     * @throws InvalidWorkspaceState if workspace is already closed
     * @return the new DocumentLink object
	 */
	public DocumentLink createDocumentLinkByName(
	        String rootId,
	        QualifiedName workspaceName,
	        Reference reference,
	        boolean createWorkspace) throws InvalidWorkspace, InvalidReference, InvalidObjectName, InvalidWorkspaceState;
    
	/** Create a link to an existing document by reference.
     * 
     * Creates a link to an existing document with a new name inside a given workspace
	 * 
     * @param workspace the workspace in which to create the document link
	 * @param document  Document to link to
     * @param name name of document in the workspace
     * @throws InvalidWorkspaceState if workspace is already closed
     * @throws InvalidObjectName if name already taken
     * @return the new DocumentLink object
	 */
    public default DocumentLink createDocumentLinkByName(Workspace workspace, String name, Document document) throws InvalidWorkspaceState, InvalidObjectName {
        try {
            return createDocumentLinkByName(workspace, name, document.getReference());
        } catch (InvalidReference e) {
            throw new BaseRuntimeException(e);
        }
    }
	
    public default DocumentLink updateDocumentLink(DocumentLink link, MediaType mediaType, 
        InputStreamSupplier stream, 
		JsonObject metadata) throws InvalidWorkspaceState {
        try {
            return updateDocumentLinkByName(Constants.ROOT_ID, link.getName(), mediaType, stream, metadata, false, false);
        } catch (InvalidWorkspace | InvalidObjectName e) {
            throw new BaseRuntimeException(e);
        }
    }
    
	/** Update a document in the repository.
	 * 
	 * Updates document within the given workspace. The first parts of the given name are the
	 * name of the workspace. The last part of the given name is used as the name of the document
	 * within the workspace.
	 * 
	 * @param workspace The workspace in which to create the document 
	 * @param documentName the name of the document in a workspace
	 * @param mediaType the type of document, null if unchanged
	 * @param stream a supplier function that produces a stream of binary data representing the document, null if unchanged
	 * @param metadata a Json object describing the document, null if unchanged
     * @param createDocument if true, create a new document instead of throwing error if document does not exist
	 * @return A Reference object that can later be used to retrieve the document
	 * @throws InvalidWorkspace if workspace does not exist (and createWorkspace is false)
	 * @throws InvalidWorkspaceState if workspace is already closed
     * @throws InvalidObjectName if document does not exist in workspace (and createDocument is false)
	 */
    public default DocumentLink updateDocumentLinkByName(
			Workspace workspace,
            String documentName,
			MediaType mediaType, 
			InputStreamSupplier stream, 
			JsonObject metadata, 
			boolean createDocument) throws InvalidWorkspace, InvalidObjectName, InvalidWorkspaceState {
        return updateDocumentLinkByName(workspace.getId(), QualifiedName.of(documentName), mediaType, stream, metadata, false, createDocument);
    }
    
	/** Update a document in the repository.
	 * 
	 * Updates document within the given workspace. The first parts of the given name are the
	 * name of the workspace. The last part of the given name is used as the name of the document
	 * within the workspace.
	 * 
	 * @param rootId The workspace Id of the root workspace (name is interpreted relative to here). 
	 * @param documentName the fully qualified name of the document in a workspace
	 * @param mediaType the type of document, null if unchanged
	 * @param stream a supplier function that produces a stream of binary data representing the document, null if unchanged
	 * @param metadata a Json object describing the document, null if unchanged
	 * @param createWorkspace if true, create a new workspace instead of throwing error if workspace does not exist
     * @param createLink if true, create a new document instead of throwing error if document does not exist
	 * @return A Reference object that can later be used to retrieve the document
	 * @throws InvalidWorkspace if workspace does not exist (and createWorkspace is false)
	 * @throws InvalidWorkspaceState if workspace is already closed
     * @throws InvalidObjectName if document does not exist in workspace (and createDocument is false)
	 */
	public DocumentLink updateDocumentLinkByName(
			String rootId,
			QualifiedName documentName,
			MediaType mediaType, 
			InputStreamSupplier stream, 
			JsonObject metadata, 
			boolean createWorkspace,
			boolean createLink) throws InvalidWorkspace, InvalidObjectName, InvalidWorkspaceState;
	
    public default DocumentLink updateDocumentLinkByName(Workspace workspace, String name, Reference reference, boolean createLink) throws InvalidReference, InvalidWorkspaceState, InvalidObjectName {
        try {
            return updateDocumentLinkByName(workspace.getId(), QualifiedName.of(name), reference, false, createLink);
        } catch (InvalidWorkspace e) {
            throw new BaseRuntimeException(e);
        }
    }
    
    /** Update or create a link to an existing document by reference.
     * 
     * Creates a link to an existing document with a new name inside a given workspace.
     * 
     * @param rootId the Id of the 'root' workspace
     * @param documentName the name of the document relative to rootId
     * @param reference Reference of document to link to, null if unchanged
     * @param createWorkspace Allow workspace creation if workspace dos not exist
     * @param createLink if true, create a new document link instead of throwing error if document link does not exist
     * @return the updated document link
     * @throws InvalidWorkspace if document link or workspace does not exist and createWorkspace is false
     * @throws InvalidWorkspaceState if workspace is already closed
     * @throws InvalidObjectName if document does not exist in workspace (and createDocument is false)
     * @throws InvalidReference if reference does not refer to a valid document
     * 
     */
    public DocumentLink updateDocumentLinkByName(
            String rootId,
            QualifiedName documentName,
            Reference reference,
            boolean createWorkspace,
            boolean createLink) throws InvalidWorkspace, InvalidObjectName, InvalidWorkspaceState, InvalidReference;
    
    
    public default DocumentLink updateDocumentLinkByName(
            Workspace workspace,
            String name,
            Document document,
            boolean createLink) throws InvalidObjectName, InvalidWorkspaceState {
        try {   
            return updateDocumentLinkByName(workspace, name, document.getReference(), createLink);
        } catch (InvalidReference ex) {
            throw new BaseRuntimeException(ex);
        }
    }

    public default DocumentLink updateDocumentLink(DocumentLink link, Reference reference) throws InvalidWorkspaceState, InvalidReference {
        try {
            return updateDocumentLinkByName(Constants.ROOT_ID, link.getName(), reference, false, false);
        } catch (InvalidWorkspace | InvalidObjectName e) {
            throw new BaseRuntimeException(e);
        }
    }
        
    public default DocumentLink updateDocumentLink(DocumentLink link, Document document) throws InvalidWorkspaceState, InvalidReference {
        return updateDocumentLink(link, document.getReference());
    }
    
    public default NamedRepositoryObject copyObject(NamedRepositoryObject source, Workspace target, String targetName) throws InvalidWorkspaceState, InvalidObjectName {
         switch (source.getType()) {
             case DOCUMENT_LINK: return copyDocumentLink((DocumentLink)source, target, targetName);
             case WORKSPACE: return copyWorkspace((Workspace)source, target, targetName);
             default: throw new RuntimeException("Invalid type " + source.getType());
         }
    }
    
    public NamedRepositoryObject copyObject(String sourceRootId, QualifiedName sourceName, String targetRootId, QualifiedName targetName, boolean createParent) throws InvalidWorkspaceState, InvalidWorkspace, InvalidObjectName;
    
    public default DocumentLink copyDocumentLink(DocumentLink documentLink, Workspace target, String targetName) throws InvalidWorkspaceState, InvalidObjectName {
        try {
            return copyDocumentLink(Constants.ROOT_ID, documentLink.getName(), target.getId(), target.getName().add(targetName), false);
        } catch (InvalidWorkspace ex) {
            throw new BaseRuntimeException(ex);
        }
    }
    
    public DocumentLink copyDocumentLink(String sourceRootId, QualifiedName sourceName, String targetRootId, QualifiedName targetName, boolean createWorkspace) throws InvalidWorkspaceState, InvalidWorkspace, InvalidObjectName;
    
    public default DocumentLink copyDocumentLink(Workspace source, String sourceName, Workspace target, String targetName) throws InvalidWorkspaceState, InvalidObjectName {
        try {
            return copyDocumentLink(source.getId(), QualifiedName.of(sourceName), target.getId(), QualifiedName.of(targetName), false);
        } catch (InvalidWorkspace e) {
            throw new BaseRuntimeException(e);
        }
    }
    
    public default Workspace copyWorkspace(Workspace source, Workspace target, String targetName) throws InvalidWorkspaceState, InvalidObjectName {
        try {
            return copyWorkspace(Constants.ROOT_ID, source.getName(), target.getId(), target.getName().add(targetName), false);
        } catch (InvalidWorkspace ex) {
            throw new BaseRuntimeException(ex);
        }
    }
    
    public Workspace copyWorkspace(String sourceRootId, QualifiedName sourceName, String targetRootId, QualifiedName targetName, boolean createParent) throws InvalidWorkspaceState, InvalidWorkspace, InvalidObjectName;
    
    public default Workspace copyWorkspace(Workspace source, String sourceName, Workspace target, String targetName, boolean createParent) throws InvalidWorkspaceState, InvalidObjectName {
        try {
            return copyWorkspace(source.getId(), QualifiedName.of(sourceName), target.getId(), QualifiedName.of(targetName), createParent);
        } catch (InvalidWorkspace e) {
            throw new BaseRuntimeException(e);
        }
    }
    
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

    
    public default Stream<NamedRepositoryObject> catalogueByName(Workspace workspace, QualifiedName path, Query filter, boolean searchHistory) {
        try {
            return catalogueByName(workspace.getId(), path, filter, searchHistory);
        } catch(InvalidWorkspace e) {
            throw new BaseRuntimeException(e);
        }   
    }
    
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
		
    public default Workspace createWorkspaceByName(Workspace workspace, String name, Workspace.State state, JsonObject metadata) throws InvalidWorkspaceState, InvalidObjectName {
        try {
            return createWorkspaceByName(workspace.getId(), QualifiedName.of(name), state, metadata, false);
        } catch (InvalidWorkspace e) {
            throw new BaseRuntimeException(e);
        }
    }
    
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
     * @param createParent flag whether to create parents if they don't exist
	 * @return the id of the created workspace
	 * @throws InvalidWorkspace if createParent is false and parent workspace does not already exist, or workspace already exists
	 */
	public Workspace createWorkspaceByName(String rootId, QualifiedName name, Workspace.State state, JsonObject metadata, boolean createParent) throws InvalidWorkspaceState, InvalidWorkspace;

    public default Workspace createWorkspaceAndName(Workspace workspace, Workspace.State state, JsonObject metadata) throws InvalidWorkspaceState, InvalidWorkspace {
        return createWorkspaceAndName(workspace.getId(), QualifiedName.ROOT, state, metadata, false);
    }
    
    /** Create a workspace with a system-generated name
	 * 
	 * Workspaces may be open, finalized or closed; catalog operations on a closed or finalized
	 * workspace work on the versions of documents that were current at the time the workspace
	 * was closed. Operations that create or update documents in a finalized workspace will create
	 * new versions of the document but will not change the workspace; attempting such operations
	 * on a closed workspace will throw an error.
	 * 
	 * @param rootId base location id
	 * @param name name of workspace in which to create new workspace
	 * @param state Initial/Updated state of workspace (optional)
	 * @param metadata additional info describing the workspace
     * @param createParent flag whether to create parents if they don't exist
	 * @return the id of the created workspace
	 * @throws InvalidWorkspace if createParent is false and parent workspace does not already exist, or workspace 
	 */
	public Workspace createWorkspaceAndName(String rootId, QualifiedName name, Workspace.State state, JsonObject metadata, boolean createParent) throws InvalidWorkspaceState, InvalidWorkspace;
	
    public default Workspace updateWorkspace(Workspace workspace, Workspace.State state, JsonObject metadata, boolean createWorkspace) {
        try {
            return updateWorkspaceByName(workspace.getId(), QualifiedName.ROOT, state, metadata, false);
        } catch (InvalidWorkspace e) {
            throw new BaseRuntimeException(e);
        }
    }
    
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
	 * @param state Initial/Updated state of workspace
	 * @param metadata workspace metadata
	 * @param createWorkspace create workspace with given name if it does not already exist
	 * @return the id of the created/updated workspace
	 * @throws InvalidWorkspace if createWorkspace is false and workspace does not already exist
	 */
	public Workspace updateWorkspaceByName(String rootId, QualifiedName name, Workspace.State state, JsonObject metadata, boolean createWorkspace) throws InvalidWorkspace;

    public default NamedRepositoryObject getObjectByName(Workspace workspace, String name) throws InvalidObjectName {
        try {
            return getObjectByName(workspace.getId(), QualifiedName.of(name));
        } catch (InvalidWorkspace e) {
            throw new BaseRuntimeException(e);
        }
    }
    
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

    public default Workspace getWorkspaceByName(Workspace workspace, String name) throws InvalidObjectName {
        try {
            return getWorkspaceByName(workspace.getId(), QualifiedName.of(name));
        } catch (InvalidWorkspace e) {
            throw new BaseRuntimeException(e);
        }
    }

    /** Get the current state of a workspace
     * 
	 * @param rootId The workspace Id of the root workspace (name is interpreted relative to here). 
	 * @param name the name of the workspace
	 * @return a named repository object describing the object located by name
	 * @throws InvalidWorkspace if rootId is not a valid workspace
     * @throws InvalidObjectName if name is not a valid workspace name within the root workspace
     */
    public Workspace getWorkspaceByName(String rootId, QualifiedName name) throws InvalidWorkspace, InvalidObjectName;  
    
    public default void deleteDocument(Workspace workspace, String name, Document document) throws InvalidWorkspaceState, InvalidDocumentId {
        try {
            deleteDocument(workspace.getId(), QualifiedName.of(name), document.getId());
        } catch (InvalidWorkspace e) {
            throw new BaseRuntimeException(e);
        }
    }
    
    
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
	public void deleteDocument(String root_id, QualifiedName path, String doc_id) throws InvalidWorkspace, InvalidDocumentId, InvalidWorkspaceState;
	
	/** Remove a document from a workspace
	 *   
	 *  Removing a document from an open workspace will remove that document
	 *  from a catalog search of the workspace, unless the searchHistory option
	 *  is set.
	 *  
	 * @param rootId The workspace Id of the root workspace (name is interpreted relative to here). 
	 * @param objectName Object id to remove from its parent workspace
	 * @throws InvalidWorkspace if workspace does not exist
	 * @throws InvalidObjectName if document is not in workspace
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
    
    public default Workspace refresh(Workspace workspace) {
        try {
            return getWorkspaceByName(workspace.getId(), QualifiedName.ROOT);
        } catch (InvalidWorkspace | InvalidObjectName e) {
            throw new BaseRuntimeException(e);
        }
    }

    public default DocumentLink refresh(DocumentLink documentLink) {
        try {
            return getDocumentLinkByName(Constants.ROOT_ID, documentLink.getName());
        } catch (InvalidWorkspace | InvalidObjectName e) {
            throw new BaseRuntimeException(e);
        }
    }
    
    
}