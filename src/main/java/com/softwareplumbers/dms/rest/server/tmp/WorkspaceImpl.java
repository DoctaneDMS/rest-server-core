package com.softwareplumbers.dms.rest.server.tmp;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.ws.rs.core.MediaType;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractquery.ObjectConstraint;
import com.softwareplumbers.common.abstractquery.Value.MapValue;
import com.softwareplumbers.dms.rest.server.model.Document;
import com.softwareplumbers.dms.rest.server.model.DocumentLink;
import com.softwareplumbers.dms.rest.server.model.DocumentLinkImpl;
import com.softwareplumbers.dms.rest.server.model.NamedRepositoryObject;
import com.softwareplumbers.dms.rest.server.model.Reference;
import com.softwareplumbers.dms.rest.server.model.RepositoryObject;
import com.softwareplumbers.dms.rest.server.model.RepositoryService;
import com.softwareplumbers.dms.rest.server.model.Workspace;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidDocumentId;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidObjectName;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidReference;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspace;
import com.softwareplumbers.dms.rest.server.model.RepositoryService.InvalidWorkspaceState;
import com.softwareplumbers.dms.rest.server.util.Log;

class WorkspaceImpl implements Workspace {
	
	static Log LOG = new Log(WorkspaceImpl.class);
	
	private static class DocumentInfo extends DocumentLinkImpl {
		public boolean deleted;
		
		public DocumentInfo(RepositoryService service, QualifiedName name, Reference link, boolean deleted) {
		    super(service, name, link);
		    this.deleted = deleted;
		}	
		
		public DocumentLinkImpl toDynamic() { return new DocumentInfo(this.service, this.name, new Reference(ref.id), deleted); }
        public DocumentLinkImpl toStatic() { return new DocumentInfo(this.service, this.name, getReference(), deleted); }
	}
	
	/**
	 * 
	 */
	private final TempRepositoryService service;
	private WorkspaceImpl parent;
	private String name;
	private UUID id;
	private TreeMap<String, NamedRepositoryObject> children;
	private State state;
	private JsonObject metadata;
	
	
	
	private static String generateName() {
		return UUID.randomUUID().toString();
	}

	
	public WorkspaceImpl(TempRepositoryService service, WorkspaceImpl parent, UUID id, String name, State state, JsonObject metadata) {
		this.service = service;
		this.id = id;
		this.name = name == null ? generateName() : name;
		this.state = state;
		this.children = new TreeMap<String, NamedRepositoryObject>();
		this.parent = parent;
		this.metadata = metadata == null ? TempRepositoryService.EMPTY_METADATA : metadata;
	}

	@Override
	public QualifiedName getName() {
		if (this == service.root) return QualifiedName.ROOT;
		if (parent == null) return QualifiedName.of("~", id.toString());
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
		    children.entrySet().stream()
		        .filter(entry -> entry.getValue().getType() == Type.DOCUMENT_LINK)
		        .forEach(entry -> entry.setValue(((DocumentLinkImpl)entry.getValue()).toStatic()));

		// Convert references to point to a the most recent version
		// of a document when a workspace is opened
		if (this.state != State.Open && state == State.Open)
            children.entrySet().stream()
            .filter(entry -> entry.getValue().getType() == Type.DOCUMENT_LINK)
            .forEach(entry -> entry.setValue(((DocumentLinkImpl)entry.getValue()).toDynamic()));
		
				
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
		String baseName = (docName == null || docName == JsonValue.NULL) ? "Document" : docName.toString() + "_" + children.size();
		String ext = "";
		int separator = baseName.lastIndexOf('.');
		if (separator >= 0) {
			ext = baseName.substring(separator, baseName.length());
			baseName = baseName.substring(0, separator);
		}
		String containmentName = baseName + ext;
		int count = 1;
		while (children.containsKey(containmentName)) {
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
				this.children.put(docName, new DocumentInfo(service,getName().add(docName),latest,false));
				service.registerWorkspaceReference(this, latest);
			}
		}
		else throw LOG.logThrow("add", new InvalidWorkspaceState(name, state));
		LOG.logExiting("add");
	}
	
	public void update(Reference reference, String docName) throws InvalidWorkspaceState, InvalidObjectName {
		LOG.logEntering("add", reference, docName);
		NamedRepositoryObject objRef = children.get(docName);
		if (objRef== null || objRef.getType() != Type.DOCUMENT_LINK) throw new InvalidObjectName(getName().add(docName));
		DocumentLink docRef = (DocumentLink)objRef;
		if (state == State.Open) {
			if (docRef.getId() != reference.id) {
				service.deregisterWorkspaceReference(this, docRef.getReference());
				service.registerWorkspaceReference(this, reference);
				children.put(docName, new DocumentInfo(service, docRef.getName(), new Reference(reference.id), false));
			} 
			// else really nothing to do
		}
		else throw LOG.logThrow("add", new InvalidWorkspaceState(name, state));
		LOG.logExiting("add");		
	}
	
	public NamedRepositoryObject deleteObjectByName(String docName) throws InvalidWorkspaceState {
		LOG.logEntering("deleteObjectByName", docName);
		if (state == State.Open) {
			NamedRepositoryObject obj = children.get(docName);
			if (obj == null) return LOG.logReturn("deleteDocumentByName", null);
			if (obj.getType() == Type.DOCUMENT_LINK) {
			    DocumentInfo info = (DocumentInfo)obj;
			    info.deleted = true;
			    service.deregisterWorkspaceReference(this, info.getReference());
			} else {
                WorkspaceImpl child = (WorkspaceImpl)obj;
	            if (child.isEmpty()) {
	                children.remove(docName);
	                service.deregisterWorkspace(child);
	                return LOG.logReturn("deleteWorkspaceByName", child);
	            } else {
	                throw new InvalidWorkspaceState(docName, "Not empty");
	            }
			    
			}
			return LOG.logReturn("deleteObjectByName", obj);
		} else {
			throw LOG.logThrow("deleteObjectByName",new InvalidWorkspaceState(docName, state));
		}
	}
		
	public void deleteById(String id) throws InvalidDocumentId, InvalidWorkspaceState {
		LOG.logEntering("deleteById", id);
		if (state == State.Open) {
			DocumentInfo info = children.values()
				.stream()
				.filter(obj -> obj.getType()==Type.DOCUMENT_LINK)
				.map(obj -> (DocumentInfo)obj)
				.filter(i -> i.getId().equals(id))
				.findFirst()
				.orElseThrow(()->LOG.logThrow("deleteById",new InvalidDocumentId(id)));
			info.deleted = true;
			service.deregisterWorkspaceReference(this, info.getReference());
		} else {
			throw LOG.logThrow("deleteById",new InvalidWorkspaceState(name, state));
		}
		LOG.logExiting("deleteById");
	}

	private Stream<DocumentInfo> getHistory(DocumentInfo doc, ObjectConstraint filter) {
	    try {
            return service.catalogueHistory(doc.getReference(), filter)
                .map(histDoc->new DocumentInfo(service, doc.getName(), histDoc.getReference(), false));
        } catch (InvalidReference e) {
            throw new RuntimeException(e);
        }
	}
	
	public Stream<NamedRepositoryObject> catalogue(ObjectConstraint filter, boolean searchHistory) {
		
		final Predicate<NamedRepositoryObject> filterPredicate = filter == null ? info->true : info->filter.containsItem(MapValue.from(info.getMetadata()));

		Stream<DocumentInfo> docInfo = children.values().stream()
		        .filter(child -> child.getType() == Type.DOCUMENT_LINK)
		        .map(child -> (DocumentInfo)child);
		
		if (searchHistory) {
			// Search all historical info, and return latest matching version
			// of any document.
		    Stream<DocumentInfo> history = docInfo
		            .flatMap(doc -> getHistory(doc, filter));

			docInfo = TempRepositoryService.latestVersionsOf(history);
		} else {
			docInfo = docInfo
				.filter(doc -> !doc.deleted)
				.filter(filterPredicate);
		}
		
		Stream<NamedRepositoryObject> folderInfo = children.values().stream()
		    .filter(item -> item.getType()==Type.WORKSPACE)
	        .filter(filterPredicate);
		
		return Stream.concat(docInfo, folderInfo);
	}
	
	public Stream<NamedRepositoryObject> catalogue(QualifiedName workspaceName, ObjectConstraint filter, boolean searchHistory) {
		
		if (workspaceName == QualifiedName.ROOT) return catalogue(filter, searchHistory);

		String firstPart = workspaceName.get(0);
		QualifiedName remainingName = workspaceName.rightFromStart(1);

		int star = firstPart.indexOf('*'); 
		int questionMark = firstPart.indexOf('?'); 
		int firstWildcard = star < 0 || questionMark < 0 ? Math.max(star, questionMark) : Math.min(star, questionMark);

		if (firstWildcard < 0) {
			NamedRepositoryObject child = children.get(firstPart);
			return (child == null || child.getType() != Type.WORKSPACE) 
				? Stream.empty() 
				: ((WorkspaceImpl)child).catalogue(workspaceName.rightFromStart(1), filter, searchHistory);
		}
		
		SortedMap<String,NamedRepositoryObject> submap = children;
			
		if (firstWildcard > 0) {
			String lowerBound = firstPart.substring(0, firstWildcard);
			String upperBound = TempRepositoryService.nextSeq(lowerBound);
			submap = children.subMap(lowerBound, upperBound);
		}
			
		String pattern = TempRepositoryService.wildcardToRegex(firstPart);
		
		Stream<NamedRepositoryObject> matchingChildren = submap.entrySet()
			.stream()
			.filter(e -> e.getKey().matches(pattern))
			.map(e -> e.getValue());
		
		if (remainingName.isEmpty()) {
		    return matchingChildren;
		} else {
			return matchingChildren
			    .filter(child -> child.getType() == Type.WORKSPACE)
			    .map(child -> (WorkspaceImpl)child)
			    .flatMap(workspace -> workspace.catalogue(remainingName, filter,  searchHistory));
		}
	}
	
	public WorkspaceImpl getOrCreateWorkspace(QualifiedName name, boolean createWorkspace) throws InvalidWorkspace {
		if (name.isEmpty()) return this;
		
		String firstPart = name.get(0);
		QualifiedName remainingName = name.rightFromStart(1);

		NamedRepositoryObject child = children.get(firstPart);
		WorkspaceImpl childWorkspace = null;
		if (child == null) {
			if (createWorkspace) {
				childWorkspace = new WorkspaceImpl(service, this, UUID.randomUUID(), firstPart, State.Open, TempRepositoryService.EMPTY_METADATA);
				children.put(firstPart, childWorkspace);
				service.registerWorkspace(childWorkspace);
			} else 
				throw new InvalidWorkspace(name);
		} else {
		    if (child.getType() == Type.WORKSPACE) 
		        childWorkspace = (WorkspaceImpl)child;
		    else
		        throw new InvalidWorkspace(name);
		}
		
		
		if (remainingName.isEmpty())
			return childWorkspace;
		else			
			return childWorkspace.getOrCreateWorkspace(remainingName, createWorkspace);
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
	
	public Optional<NamedRepositoryObject> getObject(String name) {
        NamedRepositoryObject obj = children.get(name);
        return Optional.ofNullable(obj);
	}
	
	public Optional<DocumentLink> getDocument(String name) {
		NamedRepositoryObject obj = children.get(name);
		if (obj == null || obj.getType() != Type.DOCUMENT_LINK) return Optional.empty();
		DocumentInfo doc = (DocumentInfo)obj;
		if (!doc.deleted) 
			return Optional.of(doc);
		else
			return Optional.empty();
	}

	public Optional<WorkspaceImpl> getWorkspace(String name) {
        NamedRepositoryObject obj = children.get(name);
        if (obj == null || obj.getType() != Type.WORKSPACE) return Optional.empty();
        return Optional.of((WorkspaceImpl)obj);
	}
	
	public NamedRepositoryObject getObject(QualifiedName name) throws InvalidWorkspace, InvalidObjectName {
		if (name.isEmpty()) return this;
		WorkspaceImpl ws = getOrCreateWorkspace(name.parent, false);
		NamedRepositoryObject result = ws.children.get(name.part);
		if (result == null) throw new InvalidObjectName(name);
		return result;	
	}

	@Override
	public JsonObject getMetadata() {
		return metadata;
	}

	public void setMetadata(JsonObject metadata) {
		this.metadata = metadata;
	}
	
	public boolean isEmpty() {
		if (children.size() == 0) return true;
		return children.values().stream()
		    .filter(child->child.getType() == Type.DOCUMENT_LINK)
		    .map(child->(DocumentInfo)child)
		    .anyMatch(doc -> !doc.deleted);
	}
	

	public DocumentLinkImpl getById(String documentId) throws InvalidDocumentId {
		return children.values().stream()
	            .filter(child->child.getType() == Type.DOCUMENT_LINK)
	            .map(child->(DocumentInfo)child)
				.filter(doc->doc.getId().equals(documentId))
				.findFirst()
				.orElseThrow(()->new InvalidDocumentId(documentId)); 
	}
}
