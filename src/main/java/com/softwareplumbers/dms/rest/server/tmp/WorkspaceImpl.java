package com.softwareplumbers.dms.rest.server.tmp;

import java.util.Map;
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
import com.softwareplumbers.dms.rest.server.model.Workspace;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidDocumentId;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspace;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspaceState;

class WorkspaceImpl implements Workspace {
	
	private static class WorkspaceInfo {
		public boolean deleted;
		public Reference reference;
		
		public WorkspaceInfo(Reference reference, boolean deleted) {
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
	UUID id;
	private TreeMap<String,WorkspaceInfo> docs;
	private TreeMap<String, WorkspaceImpl> children;
	private State state;
	private JsonObject metadata;
	
	public WorkspaceImpl(TempRepositoryService service, WorkspaceImpl parent, UUID id, String name, State state, JsonObject metadata) {
		this.service = service;
		this.id = id;
		this.name = name;
		this.state = state;
		this.docs = new TreeMap<String, WorkspaceInfo>();
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
			
	public void setState(State state) {
		
		// Convert references to point to a specific version
		// of a document when a workspace is closed or finalized
		if (this.state == State.Open && state != State.Open)
			for (WorkspaceInfo info : docs.values())
				info.reference = service.store.floorKey(info.reference);

		// Convert references to point to a the most recent version
		// of a document when a workspace is opened
		if (this.state != State.Open && state == State.Open)
			for (WorkspaceInfo info : docs.values())
				info.reference = new Reference(info.reference.id);
			
				
		this.state = state;
	}
			
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
		JsonValue docName = metadata == null ? null : service.nameAttribute.apply(metadata);
		String baseName = (docName == null || docName == JsonValue.NULL) ? null : docName.toString() + "_" + docs.size();
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
		TempRepositoryService.LOG.logEntering("add", reference);
		if (state == State.Open) {
			Reference latest = new Reference(reference.id);
			this.docs.put(getContainmentName(doc), new WorkspaceInfo(latest,false));
		}
		else throw TempRepositoryService.LOG.logThrow("add", new InvalidWorkspaceState(name, state));
		TempRepositoryService.LOG.logExiting("add");
	}
	
	public void deleteByName(String docName) throws InvalidDocumentId, InvalidWorkspaceState {
		TempRepositoryService.LOG.logEntering("deleteByName", docName);
		if (state == State.Open) {
			WorkspaceInfo info = docs.get(docName);
			// TODO: It isn't the document Id that's invalid here. It's the containment name
			if (info == null) throw TempRepositoryService.LOG.logThrow("deleteByName",new InvalidDocumentId(docName));
			info.deleted = true;
		} else {
			throw TempRepositoryService.LOG.logThrow("deleteByName",new InvalidWorkspaceState(docName, state));
		}
		TempRepositoryService.LOG.logExiting("deleteByName");
	}
	
	public void deleteById(String id) throws InvalidDocumentId, InvalidWorkspaceState {
		TempRepositoryService.LOG.logEntering("deleteById", id);
		if (state == State.Open) {
			WorkspaceInfo info = docs.values()
				.stream()
				.filter(i -> i.reference.id.equals(id))
				.findFirst()
				.orElseThrow(()->TempRepositoryService.LOG.logThrow("deleteById",new InvalidDocumentId(id)));
			info.deleted = true;
		} else {
			throw TempRepositoryService.LOG.logThrow("deleteById",new InvalidWorkspaceState(name, state));
		}
		TempRepositoryService.LOG.logExiting("deleteById");
	}

	public Stream<Info> catalogue(ObjectConstraint filter, boolean searchHistory) {
		
		final Predicate<Info> filterPredicate = filter == null ? info->true : info->filter.containsItem(MapValue.from(info.metadata));
		Stream<Map.Entry<String, WorkspaceInfo>> entries = docs.entrySet().stream();

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
				.map(entry -> service.info(path.add(entry.getKey()), entry.getValue().reference))
				.filter(filterPredicate);
		}
		
		Stream<Info> folderInfo = children.entrySet().stream()
			.map(entry -> service.info(entry.getValue()))
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
					
		return (remainingName.isEmpty())
			? matchingChildren.map(workspace -> service.info(workspace))
			: matchingChildren.flatMap(workspace -> workspace.catalogue(remainingName, filter,  searchHistory));
	}
	
	public WorkspaceImpl getOrCreateWorkspace(QualifiedName name, boolean createWorkspace) throws InvalidWorkspace {
		
		String firstPart = name.get(0);
		QualifiedName remainingName = name.rightFromStart(1);

		WorkspaceImpl child = children.get(firstPart);
		if (child == null) {
			if (createWorkspace) {
				child = new WorkspaceImpl(service, this, UUID.randomUUID(), firstPart, State.Open, TempRepositoryService.EMPTY_METADATA);
				children.put(firstPart, child);
			} else 
				throw new InvalidWorkspace(name);
		}
		
		if (remainingName.isEmpty())
			return child;
		else			
			return child.getOrCreateWorkspace(remainingName, createWorkspace);
	}
	
	public WorkspaceImpl createWorkspace(UUID id, QualifiedName name, State state, JsonObject metadata) throws InvalidWorkspace {
		WorkspaceImpl parent = name.parent.isEmpty() ? this : getOrCreateWorkspace(name.parent, true);
		if (parent.children.containsKey(name.part)) throw new InvalidWorkspace(name);
		WorkspaceImpl child = new WorkspaceImpl(service, parent, id, name.part, state, metadata);
		parent.children.put(name.part, child);
		return child;
	}
	
	public WorkspaceImpl getWorkspace(QualifiedName name) {
		try {
			return getOrCreateWorkspace(name, false);
		} catch (InvalidWorkspace e) {
			return null;
		}
	}

	@Override
	public JsonObject getMetadata() {
		return metadata;
	}
}