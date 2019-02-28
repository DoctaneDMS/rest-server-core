package com.softwareplumbers.dms.rest.server.tmp;

import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.json.JsonObject;
import javax.json.JsonValue;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractquery.ObjectConstraint;
import com.softwareplumbers.common.abstractquery.Value.MapValue;
import com.softwareplumbers.dms.rest.server.model.Document;
import com.softwareplumbers.dms.rest.server.model.Info;
import com.softwareplumbers.dms.rest.server.model.Reference;
import com.softwareplumbers.dms.rest.server.model.RepositoryObject;
import com.softwareplumbers.dms.rest.server.model.Workspace;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidDocumentId;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidObjectName;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidReference;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspace;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspaceState;
import com.softwareplumbers.dms.rest.server.util.Log;

class WorkspaceImpl implements Workspace {
	
	static Log LOG = new Log(WorkspaceImpl.class);
	
	private static class DocumentInfo {
		public boolean deleted;
		public Reference reference;
		
		public DocumentInfo(Reference reference, boolean deleted) {
			this.deleted = deleted;
			this.reference = reference;
		}
	}
	
	/**
	 * 
	 */
	private final TempRepositoryService service;
	private WorkspaceImpl parent;
	private String name;
	private UUID id;
	private TreeMap<String,DocumentInfo> docs;
	private TreeMap<String, WorkspaceImpl> children;
	private State state;
	private JsonObject metadata;
	
	private Info info(QualifiedName name, Reference ref) {
		try {
			return new Info(name, ref, service.getDocument(ref));
		} catch (InvalidReference err) {
			throw new RuntimeException(err);
		}
	}
	
	private Info info(Workspace ws) {
		return new Info(ws);
	}
	
	
	private static String generateName() {
		return UUID.randomUUID().toString();
	}

	
	public WorkspaceImpl(TempRepositoryService service, WorkspaceImpl parent, UUID id, String name, State state, JsonObject metadata) {
		this.service = service;
		this.id = id;
		this.name = name == null ? generateName() : name;
		this.state = state;
		this.docs = new TreeMap<String, DocumentInfo>();
		this.children = new TreeMap<String, WorkspaceImpl>();
		this.parent = parent;
		this.metadata = metadata == null ? TempRepositoryService.EMPTY_METADATA : metadata;
	}

	@Override
	public QualifiedName getName() {
		if (parent == null) return QualifiedName.ROOT;
		return parent.getName().add(name);
	}
	
	@Override
	public State getState() {
		return state;
	}
	
	@Override
	public String getId() {
		return id.toString();
	}
		
	public UUID getRawId() {
		return id;
	}
	
	public void setState(State state) {
		
		// Convert references to point to a specific version
		// of a document when a workspace is closed or finalized
		if (this.state == State.Open && state != State.Open)
			for (DocumentInfo info : docs.values())
				info.reference = service.getLatestVersion(info.reference);

		// Convert references to point to a the most recent version
		// of a document when a workspace is opened
		if (this.state != State.Open && state == State.Open)
			for (DocumentInfo info : docs.values())
				info.reference = new Reference(info.reference.id);
			
				
		this.state = state;
	}
			
	/** Set the name of a workspace
	 * 
	 * Transplants this workspace within the given workspace root. The qualified name given becomes
	 * the new name of this workspace relative to the root. This workspace is removed from any workspace
	 * in which it was previously a child.
	 * 
	 * @param root
	 * @param name
	 * @param createWorkspace
	 * @throws InvalidWorkspace
	 */
	public void setName(WorkspaceImpl root, QualifiedName name, boolean createWorkspace) throws InvalidWorkspace {
		WorkspaceImpl newParent;
		if (name.parent.isEmpty())
			newParent = root;
		else
			newParent = root.getOrCreateWorkspace(name.parent, createWorkspace);

		if (newParent == null) throw new InvalidWorkspace(name);
		if (newParent.children.containsKey(name.part)) throw new InvalidWorkspace(name);
		if (this.parent != null) this.parent.children.remove(this.name);
		this.name = name.part;
		this.parent = newParent;
		newParent.children.put(this.name, this);
	}
	
	public String getContainmentName(Document doc) {
		JsonObject metadata = doc.getMetadata(); 
		JsonValue docName = metadata == null ? null : service.getNameAttribute().apply(metadata);
		String baseName = (docName == null || docName == JsonValue.NULL) ? "Document" : docName.toString() + "_" + docs.size();
		String ext = "";
		int separator = baseName.lastIndexOf('.');
		if (separator >= 0) {
			ext = baseName.substring(separator, baseName.length());
			baseName = baseName.substring(0, separator);
		}
		String containmentName = baseName + ext;
		int count = 1;
		while (docs.containsKey(containmentName)) {
			containmentName = baseName + "." + count + ext;
		}
		return containmentName;
	}
	
	
	public void add(Reference reference, Document doc) throws InvalidWorkspaceState {
		LOG.logEntering("add", reference, doc);
		add(reference, getContainmentName(doc));
		LOG.logExiting("add");
	}
	
	public void add(Reference reference, String docName) throws InvalidWorkspaceState {
		LOG.logEntering("add", reference, docName);
		if (state == State.Open) {
			if (!service.referenceExists(this, reference)) {
				Reference latest = new Reference(reference.id);
				this.docs.put(docName, new DocumentInfo(latest,false));
				service.registerWorkspaceReference(this, latest);
			}
		}
		else throw LOG.logThrow("add", new InvalidWorkspaceState(name, state));
		LOG.logExiting("add");
	}
	
	public void update(Reference reference, String docName) throws InvalidWorkspaceState, InvalidObjectName {
		LOG.logEntering("add", reference, docName);
		DocumentInfo docRef = docs.get(docName);
		if (docRef== null) throw new InvalidObjectName(getName().add(docName));
		if (state == State.Open) {
			if (docRef.reference.id != reference.id) {
				service.deregisterWorkspaceReference(this, docRef.reference);
				service.registerWorkspaceReference(this, reference);
				docRef.reference = new Reference(reference.id);
			} 
			// else really nothing to do
		}
		else throw LOG.logThrow("add", new InvalidWorkspaceState(name, state));
		LOG.logExiting("add");		
	}
	
	public Reference deleteDocumentByName(String docName) throws InvalidWorkspaceState {
		LOG.logEntering("deleteDocumentByName", docName);
		if (state == State.Open) {
			DocumentInfo info = docs.get(docName);
			if (info == null) return LOG.logReturn("deleteDocumentByName", null);
			info.deleted = true;
			service.deregisterWorkspaceReference(this, info.reference);
			return LOG.logReturn("deleteDocumentByName", info.reference);
		} else {
			throw LOG.logThrow("deleteDocumentByName",new InvalidWorkspaceState(docName, state));
		}
	}
	
	public WorkspaceImpl deleteWorkspaceByName(String docName) throws InvalidWorkspaceState {
		LOG.logEntering("deleteWorkspaceByName", docName);
		if (state == State.Open) {
			WorkspaceImpl child = children.get(docName);
			if (child == null) return null;
			if (child.isEmpty()) {
				children.remove(docName);
				service.deregisterWorkspace(child);
				return LOG.logReturn("deleteWorkspaceByName", child);
			} else {
				throw new InvalidWorkspaceState(docName, "Not empty");
			}
		} else {
			throw LOG.logThrow("deleteWorkspaceByName",new InvalidWorkspaceState(docName, state));
		}
	}
	
	public void deleteById(String id) throws InvalidDocumentId, InvalidWorkspaceState {
		LOG.logEntering("deleteById", id);
		if (state == State.Open) {
			DocumentInfo info = docs.values()
				.stream()
				.filter(i -> i.reference.id.equals(id))
				.findFirst()
				.orElseThrow(()->LOG.logThrow("deleteById",new InvalidDocumentId(id)));
			info.deleted = true;
			service.deregisterWorkspaceReference(this, info.reference);
		} else {
			throw LOG.logThrow("deleteById",new InvalidWorkspaceState(name, state));
		}
		LOG.logExiting("deleteById");
	}

	public Stream<Info> catalogue(ObjectConstraint filter, boolean searchHistory) {
		
		final Predicate<Info> filterPredicate = filter == null ? info->true : info->filter.containsItem(MapValue.from(info.metadata));
		Stream<Map.Entry<String, DocumentInfo>> entries = docs.entrySet().stream();

		Stream<Info> docInfo = null;
		QualifiedName path = getName();
		
		if (searchHistory) {
			// Search all historical info, and return latest matching version
			// of any document.
			docInfo = TempRepositoryService.latestVersionsOf(
				entries
					.flatMap(entry -> service.historicalInfo(path, entry.getValue().reference, filter))
			);
		} else {
			docInfo = entries
				.filter(entry -> !entry.getValue().deleted)
				.map(entry -> info(path.add(entry.getKey()), entry.getValue().reference))
				.filter(filterPredicate);
		}
		
		Stream<Info> folderInfo = children.entrySet().stream()
			.map(entry -> info(entry.getValue()))
			.filter(filterPredicate);
		
		return Stream.concat(docInfo, folderInfo);
	}
	
	public Stream<Info> catalogue(QualifiedName workspaceName, ObjectConstraint filter, boolean searchHistory) {
		
		if (workspaceName == QualifiedName.ROOT) return catalogue(filter, searchHistory);

		String firstPart = workspaceName.get(0);
		QualifiedName remainingName = workspaceName.rightFromStart(1);

		int star = firstPart.indexOf('*'); 
		int questionMark = firstPart.indexOf('?'); 
		int firstWildcard = star < 0 || questionMark < 0 ? Math.max(star, questionMark) : Math.min(star, questionMark);

		if (firstWildcard < 0) {
			WorkspaceImpl child = children.get(firstPart);
			return (child == null) 
				? Stream.empty() 
				: child.catalogue(workspaceName.rightFromStart(1), filter, searchHistory);
		}
		
		SortedMap<String,WorkspaceImpl> submap = children;
			
		if (firstWildcard > 0) {
			String lowerBound = firstPart.substring(0, firstWildcard);
			String upperBound = TempRepositoryService.nextSeq(lowerBound);
			submap = children.subMap(lowerBound, upperBound);
		}
			
		String pattern = TempRepositoryService.wildcardToRegex(firstPart);
		
		Stream<WorkspaceImpl> matchingChildren = submap.entrySet()
			.stream()
			.filter(e -> e.getKey().matches(pattern))
			.map(e -> e.getValue());
		
		
		if (remainingName.isEmpty()) {
			QualifiedName fullPath = this.getName();
			Stream<Info> matchingDocuments = docs.entrySet()
				.stream()
				.filter(e -> e.getKey().matches(pattern))
				.map(e -> info(fullPath.add(e.getKey()), e.getValue().reference));
	
			return Stream.concat(
				matchingChildren.map(workspace -> info(workspace)),
				matchingDocuments
			);
			
		} else {
			return matchingChildren.flatMap(workspace -> workspace.catalogue(remainingName, filter,  searchHistory));
		}
	}
	
	public WorkspaceImpl getOrCreateWorkspace(QualifiedName name, boolean createWorkspace) throws InvalidWorkspace {
		if (name.isEmpty()) return this;
		
		String firstPart = name.get(0);
		QualifiedName remainingName = name.rightFromStart(1);

		WorkspaceImpl child = children.get(firstPart);
		if (child == null) {
			if (createWorkspace) {
				child = new WorkspaceImpl(service, this, UUID.randomUUID(), firstPart, State.Open, TempRepositoryService.EMPTY_METADATA);
				children.put(firstPart, child);
				service.registerWorkspace(child);
			} else 
				throw new InvalidWorkspace(name);
		}
		
		if (remainingName.isEmpty())
			return child;
		else			
			return child.getOrCreateWorkspace(remainingName, createWorkspace);
	}
	
	public WorkspaceImpl createWorkspace(UUID id, QualifiedName name, State state, JsonObject metadata) throws InvalidWorkspace {
		String localName;
		WorkspaceImpl localParent;
		if (name == null) {
			localParent = this;
			localName = generateName();
		} else {
			localName = name.part;
			localParent = name.parent.isEmpty() ? this : getOrCreateWorkspace(name.parent, true);
			if (name != null && localParent.children.containsKey(name.part)) throw new InvalidWorkspace(name);
		}

		WorkspaceImpl child = new WorkspaceImpl(service, localParent, id, localName, state, metadata);
		
		localParent.children.put(localName, child);
		service.registerWorkspace(child);
		return child;
	}
	
	public Optional<WorkspaceImpl> getWorkspace(QualifiedName name) {
		try {
			return Optional.of(getOrCreateWorkspace(name, false));
		} catch (InvalidWorkspace e) {
			return Optional.empty();
		}
	}
	
	public Optional<Reference> getDocument(String name) {
		DocumentInfo info = docs.get(name);
		if (info != null && !info.deleted) 
			return Optional.of(info.reference);
		else
			return Optional.empty();
	}

	public Optional<WorkspaceImpl> getWorkspace(String name) {
		return Optional.ofNullable(children.get(name));
	}
	
	public RepositoryObject getObject(QualifiedName name) throws InvalidWorkspace, InvalidObjectName {
		if (name.isEmpty()) return this;
		WorkspaceImpl ws = getOrCreateWorkspace(name.parent, false);
		RepositoryObject result = ws.children.get(name.part);
		if (result != null) return result;
		DocumentInfo info = ws.docs.get(name.part);
		if (info != null)
			try {
				return service.getDocument(info.reference);
			} catch (InvalidReference e) {
				// Shouldn't happen
				throw new RuntimeException(e);
			}
		throw new InvalidObjectName(name);
		
	}

	@Override
	public JsonObject getMetadata() {
		return metadata;
	}

	public void setMetadata(JsonObject metadata) {
		this.metadata = metadata;
	}
	
	public boolean isEmpty() {
		return children.size() == 0 && !docs.values().stream().anyMatch(wsinfo -> !wsinfo.deleted);
	}

	public Reference getById(String documentId) throws InvalidDocumentId {
		return docs.values().stream()
				.map(info->info.reference)
				.filter(reference->reference.id.equals(documentId))
				.findFirst()
				.orElseThrow(()->new InvalidDocumentId(documentId)); 
	}
}
