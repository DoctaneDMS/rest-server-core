package com.softwareplumbers.dms.rest.server.tmp;

import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.json.JsonObject;
import javax.json.JsonValue;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractpattern.Pattern;
import com.softwareplumbers.common.abstractpattern.parsers.Parsers;
import com.softwareplumbers.common.abstractpattern.visitor.Builders;
import com.softwareplumbers.common.abstractpattern.visitor.Visitor;
import com.softwareplumbers.common.abstractquery.Query;
import com.softwareplumbers.dms.Document;
import com.softwareplumbers.dms.DocumentLink;
import com.softwareplumbers.dms.DocumentNavigatorService;
import com.softwareplumbers.dms.NamedRepositoryObject;
import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.RepositoryObject;
import com.softwareplumbers.dms.Workspace;
import com.softwareplumbers.dms.RepositoryService.InvalidDocumentId;
import com.softwareplumbers.dms.RepositoryService.InvalidObjectName;
import com.softwareplumbers.dms.RepositoryService.InvalidReference;
import com.softwareplumbers.dms.RepositoryService.InvalidWorkspace;
import com.softwareplumbers.dms.RepositoryService.InvalidWorkspaceState;
import com.softwareplumbers.dms.rest.server.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.json.JsonString;
import javax.ws.rs.core.MediaType;

class WorkspaceImpl implements Workspace {
	
	static Log LOG = new Log(WorkspaceImpl.class);
	
	
	/**
	 * 
	 */
	final TempRepositoryService service;
	private WorkspaceImpl parent;
	private String name;
	private String id;
	private TreeMap<String, NamedRepositoryObject> children;
	private State state;
	private JsonObject metadata;
    private DocumentNavigatorService navigator;
	
	private static String generateName() {
		return UUID.randomUUID().toString();
	}

	
	public WorkspaceImpl(TempRepositoryService service, DocumentNavigatorService navigator, WorkspaceImpl parent, String id, String name, State state, JsonObject metadata) {
		if (state == null) throw new IllegalArgumentException("state cannot be null");
		if (service == null) throw new IllegalArgumentException("service cannot be null");
		if (id == null) throw new IllegalArgumentException("Id cannot be null");
	    this.service = service;
		this.id = id;
		this.name = name == null ? generateName() : name;
		this.state = state;
		this.children = new TreeMap<>();
		this.parent = parent;
		this.metadata = metadata == null ? TempRepositoryService.EMPTY_METADATA : metadata;
	}

	@Override
	public QualifiedName getName() {
		if (this == service.root) return QualifiedName.ROOT;
		if (parent == null) return QualifiedName.of("~" + id);
		return parent.getName().add(name);
	}
	
	@Override
	public State getState() {
		return state;
	}
	
	@Override
	public String getId() {
		return id;
	}
    			
	public void setState(State state) {
	    if (state == null) throw new IllegalArgumentException("state cannot be null");
		
		// Convert references to point to a specific version
		// of a document when a workspace is closed or finalized
		if (this.state == State.Open && state != State.Open)
		    children.entrySet().stream()
		        .filter(entry -> entry.getValue().getType() == Type.DOCUMENT_LINK)
		        .forEach(entry -> entry.setValue(((DocumentInfo)entry.getValue()).toStatic()));

		// Convert references to point to a the most recent version
		// of a document when a workspace is opened
		if (this.state != State.Open && state == State.Open)
            children.entrySet().stream()
            .filter(entry -> entry.getValue().getType() == Type.DOCUMENT_LINK)
            .forEach(entry -> entry.setValue(((DocumentInfo)entry.getValue()).toDynamic()));
		
				
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
		JsonObject docMetadata = doc.getMetadata(); 
		JsonString docName = docMetadata == null ? null : (JsonString)service.getNameAttribute().apply(docMetadata);
		String baseName = (docName == null || docName == JsonValue.NULL) ? "Document" : docName.getString() + "_" + children.size();
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
            count++;
		}
		return containmentName;
	}
	
	
	public void add(Reference reference, Document doc) throws InvalidWorkspaceState {
		LOG.logEntering("add", reference, doc);
		add(reference, getContainmentName(doc));
		LOG.logExiting("add");
	}
	
	public DocumentInfo add(Reference reference, String docName) throws InvalidWorkspaceState {
		LOG.logEntering("add", reference, docName);
		if (state == State.Open) {
			if (!service.referenceExists(this, reference)) {
				Reference latest = new Reference(reference.id);
                DocumentInfo result = new DocumentInfo(docName,latest,false, this);
				this.children.put(docName, result);
				service.registerWorkspaceReference(this, latest);
                return LOG.logReturn("add", result);
			} else {
                return LOG.logReturn("add", (DocumentInfo)this.children.get(docName));
            }
		}
		else throw LOG.logThrow("add", new InvalidWorkspaceState(name, state));
	}
    
    public Optional<DocumentInfo> findLink(Reference ref) {
        return children.values()
                .stream()
                .filter(obj -> obj.getType() == RepositoryObject.Type.DOCUMENT_LINK)
                .map(obj -> (DocumentInfo)obj)
                .filter(link -> link.getReference().equals(ref))
                .findAny();
    }
    
    public DocumentInfo add(Reference ref, boolean returnExisting) throws InvalidWorkspaceState, InvalidReference {
		LOG.logEntering("add", ref, returnExisting);
        Optional<DocumentInfo> existing = findLink(ref);
        if (existing.isPresent()) {
            if (returnExisting) 
                return LOG.logReturn("add", existing.get());
            else
                throw LOG.logThrow("add", new InvalidReference(ref));
        } else {
            String cname = getContainmentName(service.getDocument(ref));
            return LOG.logReturn("add", add(ref, cname));
        }        
    }
	
	public DocumentInfo update(Reference reference, String docName) throws InvalidWorkspaceState, InvalidObjectName {
		LOG.logEntering("update", reference, docName);
		RepositoryObject objRef = children.get(docName);
		if (objRef== null || objRef.getType() != Type.DOCUMENT_LINK) throw LOG.logThrow("update", new InvalidObjectName(getName().add(docName)));
		DocumentInfo docRef = (DocumentInfo)objRef;
		if (state == State.Open) {
			if (!docRef.getId().equals(reference.id)) {
				service.deregisterWorkspaceReference(this, docRef.getReference());
				service.registerWorkspaceReference(this, reference);
                DocumentInfo newInfo = new DocumentInfo(docRef.name, new Reference(reference.id), false, this);
				children.put(docName, newInfo);
                return LOG.logReturn("update", newInfo);
			} else {
                return LOG.logReturn("update", docRef);
            }
		}
		else throw LOG.logThrow("update", new InvalidWorkspaceState(name, state));
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

	private Stream<DocumentInfo> getHistory(DocumentInfo doc, Query filter) {
	    try {
            return service.catalogueHistory(doc.getReference(), filter)
                .map(histDoc->new DocumentInfo(doc.name, histDoc.getReference(), false, this));
        } catch (InvalidReference e) {
            throw new RuntimeException(e);
        }
	}
	
	public Stream<NamedRepositoryObject> catalogue(Query filter, boolean searchHistory) {
		
        // IMPORTANT breaking change in support of #55 and #24.
        // The query namespace now includes all fields in the document link, including the parent folder, mediaType, etc.
        // Medatadata fields that used to be in the root of the query namespace must now be prefixed with 'metadata'.
		final Predicate<NamedRepositoryObject> filterPredicate = filter == null ? info->true : info->filter.containsItem(info.toJson());

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
	
	public Stream<NamedRepositoryObject> catalogue(QualifiedName workspaceName, Query filter, boolean searchHistory) {
		
		if (workspaceName == QualifiedName.ROOT) return catalogue(filter, searchHistory);

		Pattern pattern = Parsers.parseUnixWildcard(workspaceName.get(0));
		QualifiedName remainingName = workspaceName.rightFromStart(1);
        String lowerBound = pattern.lowerBound();

		if (pattern.isSimple()) {
			NamedRepositoryObject child = children.get(lowerBound);
			if (child == null) return Stream.empty();
            if (child.getType() == Type.WORKSPACE) return ((WorkspaceImpl)child).catalogue(workspaceName.rightFromStart(1), filter, searchHistory);
            return Stream.of(child);
		}
		
		SortedMap<String,NamedRepositoryObject> submap = children;

		if (lowerBound.length() > 0) {
            String upperBound = TempRepositoryService.nextSeq(lowerBound);
			submap = children.subMap(lowerBound, upperBound);
		}
        
        java.util.regex.Pattern regex;
        
        try {
            regex = pattern.build(Builders.toPattern());
        } catch (Visitor.PatternSyntaxException ex) {
            throw new RuntimeException(ex);
        }
			
		Stream<NamedRepositoryObject> matchingChildren = submap.entrySet()
			.stream()
			.filter(e -> regex.matcher(e.getKey()).matches())
			.map(e -> e.getValue());
		
		if (remainingName.isEmpty()) {
		    return matchingChildren.filter(item->filter.containsItem(item.toJson(service, navigator, 1, 0)));
		} else {
			return matchingChildren
			    .filter(child -> child.getType() == Type.WORKSPACE)
			    .map(child -> (WorkspaceImpl)child)
			    .flatMap(workspace -> workspace.catalogue(remainingName, filter,  searchHistory));
		}
	}
	
	public WorkspaceImpl getOrCreateWorkspace(QualifiedName name, boolean createWorkspace) throws InvalidWorkspace {
		if (name == null || name.isEmpty()) return this;
		
		String firstPart = name.get(0);
		QualifiedName remainingName = name.rightFromStart(1);

		NamedRepositoryObject child = children.get(firstPart);
		WorkspaceImpl childWorkspace = null;
		if (child == null) {
			if (createWorkspace) {
				childWorkspace = new WorkspaceImpl(service, navigator, this, UUID.randomUUID().toString(), firstPart, State.Open, TempRepositoryService.EMPTY_METADATA);
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
	
	public WorkspaceImpl createWorkspace(String id, QualifiedName name, State state, JsonObject metadata) throws InvalidWorkspace {
		String localName;
		WorkspaceImpl localParent;
		if (state == null) {
		    state = State.Open;
		}
		if (name == null || name.isEmpty()) {
			localParent = this;
			localName = generateName();
		} else {
			localName = name.part;
			localParent = name.parent.isEmpty() ? this : getOrCreateWorkspace(name.parent, true);
			if (localParent.children.containsKey(name.part)) throw new InvalidWorkspace(name);
		}

		WorkspaceImpl child = new WorkspaceImpl(service, navigator, localParent, id, localName, state, metadata);
		
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
		if (children.isEmpty()) return true;
		return children.values().stream()
		    .filter(child->child.getType() == Type.DOCUMENT_LINK)
		    .map(child->(DocumentInfo)child)
		    .anyMatch(doc -> !doc.deleted);
	}
	

	public DocumentLink getById(String documentId) throws InvalidDocumentId {
		return children.values().stream()
	            .filter(child->child.getType() == Type.DOCUMENT_LINK)
	            .map(child->(DocumentInfo)child)
				.filter(doc->doc.getId().equals(documentId))
				.findFirst()
				.orElseThrow(()->new InvalidDocumentId(documentId)); 
	}
}
