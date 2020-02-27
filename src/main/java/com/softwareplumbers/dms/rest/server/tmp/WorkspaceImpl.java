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
import com.softwareplumbers.dms.Constants;
import com.softwareplumbers.dms.Document;
import com.softwareplumbers.dms.DocumentLink;
import com.softwareplumbers.dms.NamedRepositoryObject;
import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.RepositoryObject;
import com.softwareplumbers.dms.Workspace;
import com.softwareplumbers.dms.Exceptions.*;
import com.softwareplumbers.dms.RepositoryBrowser;
import com.softwareplumbers.dms.common.impl.LocalData;
import org.slf4j.ext.XLogger;
import javax.json.JsonString;
import org.slf4j.ext.XLoggerFactory;

class WorkspaceImpl implements Workspace {
	
	private static XLogger LOG = XLoggerFactory.getXLogger(WorkspaceImpl.class);
    public static final java.util.regex.Pattern MATCH_ALL = java.util.regex.Pattern.compile(".*");	
	
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
	
	private static String generateName() {
		return UUID.randomUUID().toString();
	}

    public Optional<RepositoryObject> getParent(RepositoryBrowser service) {
        return Optional.ofNullable(parent);
    }
    
    public Stream<NamedRepositoryObject> getChildren(RepositoryBrowser service) {
        return children.values().stream();
    }
    
    public boolean isNavigable() {
        return children.size() > 0;
    }
	
	public WorkspaceImpl(TempRepositoryService service, WorkspaceImpl parent, String id, String name, State state, JsonObject metadata) {
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

		if (newParent == null) throw new InvalidWorkspace(root.id, name);
		if (newParent.children.containsKey(name.part)) throw new InvalidWorkspace(root.id, name);
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
		LOG.entry(reference, doc);
		add(reference, getContainmentName(doc));
		LOG.exit();
	}
	
	public DocumentInfo add(Reference reference, String docName) throws InvalidWorkspaceState {
		LOG.entry(reference, docName);
		if (state == State.Open) {
			if (!service.referenceExists(this, reference)) {
				Reference latest = new Reference(reference.id);
                DocumentInfo result = new DocumentInfo(docName,latest,false, this);
				this.children.put(docName, result);
				service.registerWorkspaceReference(this, latest);
                return LOG.exit(result);
			} else {
                return LOG.exit((DocumentInfo)this.children.get(docName));
            }
		}
		else throw LOG.throwing(new InvalidWorkspaceState(name, state));
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
		LOG.entry(ref, returnExisting);
        Optional<DocumentInfo> existing = findLink(ref);
        if (existing.isPresent()) {
            if (returnExisting) 
                return LOG.exit(existing.get());
            else
                throw LOG.throwing(new InvalidReference(ref));
        } else {
            String cname = getContainmentName(service.getDocument(ref));
            return LOG.exit(add(ref, cname));
        }        
    }
	
	public DocumentInfo update(Reference reference, String docName) throws InvalidWorkspaceState, InvalidObjectName {
		LOG.entry(reference, docName);
		RepositoryObject objRef = children.get(docName);
		if (objRef== null || objRef.getType() != Type.DOCUMENT_LINK) throw LOG.throwing(new InvalidObjectName(Constants.ROOT_ID, getName().add(docName)));
		DocumentInfo docRef = (DocumentInfo)objRef;
		if (state == State.Open) {
			if (!docRef.getId().equals(reference.id)) {
				service.deregisterWorkspaceReference(this, docRef.getReference());
				service.registerWorkspaceReference(this, reference);
                DocumentInfo newInfo = new DocumentInfo(docRef.name, new Reference(reference.id), false, this);
				children.put(docName, newInfo);
                return LOG.exit(newInfo);
			} else {
                return LOG.exit(docRef);
            }
		}
		else throw LOG.throwing(new InvalidWorkspaceState(name, state));
	}
	
	public NamedRepositoryObject deleteObjectByName(String docName) throws InvalidWorkspaceState {
		LOG.entry(docName);
		if (state == State.Open) {
			NamedRepositoryObject obj = children.get(docName);
			if (obj == null) return LOG.exit(null);
			if (obj.getType() == Type.DOCUMENT_LINK) {
			    DocumentInfo info = (DocumentInfo)obj;
			    info.deleted = true;
			    service.deregisterWorkspaceReference(this, info.getReference());
			} else {
                WorkspaceImpl child = (WorkspaceImpl)obj;
	            if (child.isEmpty()) {
	                children.remove(docName);
	                service.deregisterWorkspace(child);
	                return LOG.exit(child);
	            } else {
	                throw new InvalidWorkspaceState(docName, "Not empty");
	            }
			    
			}
			return LOG.exit(obj);
		} else {
			throw LOG.throwing(new InvalidWorkspaceState(docName, state));
		}
	}
		
	public void deleteById(String id) throws InvalidDocumentId, InvalidWorkspaceState {
		LOG.entry(id);
		if (state == State.Open) {
			DocumentInfo info = children.values()
				.stream()
				.filter(obj -> obj.getType()==Type.DOCUMENT_LINK)
				.map(obj -> (DocumentInfo)obj)
				.filter(i -> i.getId().equals(id))
				.findFirst()
				.orElseThrow(()->LOG.throwing(new InvalidDocumentId(id)));
			info.deleted = true;
			service.deregisterWorkspaceReference(this, info.getReference());
		} else {
			throw LOG.throwing(new InvalidWorkspaceState(name, state));
		}
		LOG.exit();
	}

	private Stream<DocumentInfo> getHistory(DocumentInfo doc, Query filter) {
	    try {
            return service.catalogueHistory(doc.getReference(), filter)
                .map(histDoc->new DocumentInfo(doc.name, histDoc.getReference(), false, this));
        } catch (InvalidReference e) {
            throw new RuntimeException(e);
        }
	}
	
	public Stream<NamedRepositoryObject> catalogue(java.util.regex.Pattern nameTemplate, Query filter, boolean searchHistory) {

        final Predicate<DocumentInfo> filterDocs = doc -> {
            return !doc.deleted && nameTemplate.matcher(doc.name).matches() && filter.containsItem(doc.toJson(service,1,0));
        };
        
        final Predicate<WorkspaceImpl> filterWorkspace = workspace -> {
            return nameTemplate.matcher(workspace.name).matches() && filter.containsItem(workspace.toJson());
        };
        
		Stream<DocumentInfo> docInfo = children.values().stream()
		    .filter(child -> child.getType() == Type.DOCUMENT_LINK)
		    .map(child -> (DocumentInfo)child);
		
		if (searchHistory) {
			// Search all historical info, and return latest matching version
			// of any document.
		    Stream<DocumentInfo> history = docInfo
		            .flatMap(doc -> getHistory(doc, Query.UNBOUNDED))
                    .filter(filterDocs);
            
			docInfo = TempRepositoryService.latestVersionsOf(history);

		} else {
			docInfo = docInfo
				.filter(filterDocs);
		}
		
		Stream<WorkspaceImpl> folderInfo = children.values().stream()
		    .filter(item -> item.getType()==Type.WORKSPACE)
            .map(child -> (WorkspaceImpl)child)
	        .filter(filterWorkspace);
		
		return Stream.concat(docInfo, folderInfo);
	}
	
	public Stream<NamedRepositoryObject> catalogue(QualifiedName workspaceName, Query filter, boolean searchHistory) {
		
		if (workspaceName.isEmpty()) return catalogue(MATCH_ALL, filter, searchHistory);

        Pattern pattern = Parsers.parseUnixWildcard(workspaceName.get(0));
        
        java.util.regex.Pattern regex;
        
        try {
            regex = pattern.build(Builders.toPattern());
        } catch (Visitor.PatternSyntaxException ex) {
            throw new RuntimeException(ex);
        }

        if (workspaceName.parent.isEmpty()) return catalogue(regex, filter, searchHistory);      
		
		SortedMap<String,NamedRepositoryObject> submap = children;
        
		QualifiedName remainingName = workspaceName.rightFromStart(1);
        String lowerBound = pattern.lowerBound();

		if (pattern.isSimple()) {
			NamedRepositoryObject child = children.get(lowerBound);
			if (child == null || child.getType() != Type.WORKSPACE) return Stream.empty();
            return ((WorkspaceImpl)child).catalogue(workspaceName.rightFromStart(1), filter, searchHistory);
		}

		if (lowerBound.length() > 0) {
            String upperBound = TempRepositoryService.nextSeq(lowerBound);
			submap = children.subMap(lowerBound, upperBound);
		}
        			
		return submap.entrySet()
			.stream()
			.filter(e -> regex.matcher(e.getKey()).matches())
			.map(e -> e.getValue())
			.filter(child -> child.getType() == Type.WORKSPACE)
	        .map(child -> (WorkspaceImpl)child)
		    .flatMap(workspace -> workspace.catalogue(remainingName, filter,  searchHistory));
	}
	
	public WorkspaceImpl getOrCreateWorkspace(QualifiedName name, boolean createWorkspace) throws InvalidWorkspace {
		if (name == null || name.isEmpty()) return this;
		
		String firstPart = name.get(0);
		QualifiedName remainingName = name.rightFromStart(1);

		NamedRepositoryObject child = children.get(firstPart);
		WorkspaceImpl childWorkspace = null;
		if (child == null) {
			if (createWorkspace) {
				childWorkspace = new WorkspaceImpl(service, this, UUID.randomUUID().toString(), firstPart, State.Open, TempRepositoryService.EMPTY_METADATA);
				children.put(firstPart, childWorkspace);
				service.registerWorkspace(childWorkspace);
			} else 
				throw new InvalidWorkspace(this.id, name);
		} else {
		    if (child.getType() == Type.WORKSPACE) 
		        childWorkspace = (WorkspaceImpl)child;
		    else
		        throw new InvalidWorkspace(this.id, name);
		}
		
		
		if (remainingName.isEmpty())
			return childWorkspace;
		else			
			return childWorkspace.getOrCreateWorkspace(remainingName, createWorkspace);
	}
	
	public WorkspaceImpl createWorkspace(String id, QualifiedName name, State state, JsonObject metadata, boolean createParent) throws InvalidWorkspace {
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
			localParent = name.parent.isEmpty() ? this : getOrCreateWorkspace(name.parent, createParent);
			if (localParent.children.containsKey(name.part)) throw new InvalidWorkspace(this.id, name);
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
		if (result == null) throw new InvalidObjectName(this.id, name);
		return result;	
	}

	@Override
	public JsonObject getMetadata() {
		return metadata;
	}

	public void setMetadataInternal(JsonObject metadata) {
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
    
    @Override
    public Workspace setMetadata(JsonObject metadata) {
        return new com.softwareplumbers.dms.common.impl.WorkspaceImpl(getName(), getId(), getState(), metadata, isNavigable(), LocalData.NONE);
    }

    @Override
    public Workspace setNavigable(boolean navigable) {
        return new com.softwareplumbers.dms.common.impl.WorkspaceImpl(getName(), getId(), getState(), getMetadata(), navigable, LocalData.NONE);
    }
    
    @Override
    public String toString() {
        return toJson().toString();
    }
}
