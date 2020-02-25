package com.softwareplumbers.dms.rest.server.tmp;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.UUID;

import javax.json.Json;
import javax.json.JsonObject;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractquery.Query;
import com.softwareplumbers.common.pipedstream.InputStreamSupplier;
import com.softwareplumbers.dms.DocumentLink;
import com.softwareplumbers.dms.Document;
import com.softwareplumbers.dms.DocumentPart;
import com.softwareplumbers.dms.rest.server.model.MetadataMerge;
import com.softwareplumbers.dms.NamedRepositoryObject;
import com.softwareplumbers.dms.Reference;
import com.softwareplumbers.dms.RepositoryObject;
import com.softwareplumbers.dms.RepositoryService;
import com.softwareplumbers.dms.Exceptions.*;
import com.softwareplumbers.dms.Options;
import com.softwareplumbers.dms.StreamableRepositoryObject;
import com.softwareplumbers.dms.Workspace;
import com.softwareplumbers.dms.Workspace.State;
import com.softwareplumbers.dms.common.impl.RepositoryObjectFactory;
import java.io.InputStream;
import java.io.OutputStream;
import org.slf4j.ext.XLogger;
import java.util.Iterator;
import org.slf4j.ext.XLoggerFactory;

/** In-memory repository implementation.
 * 
 * Intended as a reference implementation in terms of behavior, and for test
 * purposes. Not thread-safe, so not suitable for production use.
 * 
 * @author SWPNET\jonessex
 *
 */
public class TempRepositoryService implements RepositoryService {
	
	///////////--------- Private Static member variables --------////////////
	private static final XLogger LOG = XLoggerFactory.getXLogger(TempRepositoryService.class);

	
	///////////--------- Public Static member variables --------////////////
	
	public static final JsonObject EMPTY_METADATA = Json.createObjectBuilder().build();
	
	///////////--------- Private member variables --------////////////
	private final TreeMap<Reference,Document> store = new TreeMap<>();
	private final TreeMap<String, WorkspaceImpl> workspacesById = new TreeMap<>();
	private final TreeMap<String, Set<String>> workspacesByDocument = new TreeMap<>();
	WorkspaceImpl root;
	private final QualifiedName nameAttribute;
    RepositoryObjectFactory factory = RepositoryObjectFactory.getInstance();
	
	private WorkspaceImpl getRoot(String rootId) throws InvalidWorkspace {
		WorkspaceImpl myRoot = root;
		if (rootId != null) {
			myRoot = workspacesById.get(rootId);
			if (myRoot == null) throw new InvalidWorkspace(rootId);
		}
		return myRoot;
	}
    
    private WorkspaceImpl getOrCreateRoot(String rootId, boolean createWorkspace) throws InvalidWorkspace {
		WorkspaceImpl myRoot = root;
		if (rootId != null) {
            if (createWorkspace)
    			myRoot = workspacesById.computeIfAbsent(rootId, id -> new WorkspaceImpl(this, null, id, null, State.Open, EMPTY_METADATA));
            else {
                myRoot = workspacesById.get(rootId);
                if (myRoot == null) throw new InvalidWorkspace(rootId);
            }
		}
		return myRoot;
	}
	
	public TempRepositoryService(QualifiedName nameAttribute) {
		this.nameAttribute = nameAttribute;
        this.root = new WorkspaceImpl(this, null, UUID.randomUUID().toString(), null, State.Open, EMPTY_METADATA);

	}
	
	public TempRepositoryService(String nameAttribute) {
		this(QualifiedName.parse(nameAttribute, "/"));
	}
	
	/** Return attribute value used as default name when adding a document to a workspace
	 * 
	 * @return A qualified name which can be used to access a name within documents in this store.
	 */
	public QualifiedName getNameAttribute() {
		return nameAttribute;
	}
	
	public Reference getLatestVersion(Reference ref) {
		return store.floorKey(new Reference(ref.id));
	}

	@Override
	public Document getDocument(Reference reference) throws InvalidReference {
		LOG.entry(reference);
		Document result = null;
		if (reference.version == null) {
			Map.Entry<Reference,Document> previous = store.floorEntry(reference);

			if (previous != null && reference.id.equals(previous.getKey().id)) 
				result = previous.getValue();  
		} else {
			result = store.get(reference);
		}
		if (result == null) throw LOG.throwing(new InvalidReference(reference));
		return LOG.exit(result);
	}
	
	void registerWorkspace(WorkspaceImpl workspace) {
		workspacesById.put(workspace.getId(), workspace);
	}

	void deregisterWorkspace(WorkspaceImpl workspace) {
		workspacesById.remove(workspace.getId());
	}

	void registerWorkspaceReference(WorkspaceImpl workspace, Reference ref) {
		workspacesByDocument.computeIfAbsent(ref.id, key -> new TreeSet<>()).add(workspace.getId());
	}
	
	public void deregisterWorkspaceReference(WorkspaceImpl workspace, Reference ref) {
		Set<String> wsids = workspacesByDocument.get(ref.id);
		wsids.remove(workspace.getId());
		if (wsids.isEmpty()) workspacesByDocument.remove(ref.id);
	}
	
	public boolean referenceExists(WorkspaceImpl workspace, Reference ref) {
		Set<String> wsids = workspacesByDocument.get(ref.id);
		if (wsids == null) return false;
		return wsids.contains(workspace.getId());
	}

	/** Update a workspace with new or updated document
	 * 
	 * @param workspaceId
	 * @param ref
	 * @param createWorkspace
	 * @throws InvalidWorkspace
	 * @throws InvalidWorkspaceState 
	 */
	private String updateWorkspace(String workspaceId, Reference ref, Document document, boolean createWorkspace) throws InvalidWorkspace, InvalidWorkspaceState {
		LOG.entry(ref, createWorkspace);
		
		if (workspaceId == null) return null;
				
		WorkspaceImpl workspace = null;
		if (workspaceId != null) {
			workspace = workspacesById.get(workspaceId);
			if (workspace == null && !createWorkspace) 
				throw LOG.throwing(new InvalidWorkspace(workspaceId));
		} 
		
		if (workspace == null) {
			if (workspaceId == null) workspaceId = UUID.randomUUID().toString();
			workspace = new WorkspaceImpl(this, null, workspaceId, null, State.Open, EMPTY_METADATA);
			workspacesById.put(workspaceId, workspace);
		}
		
		workspace.add(ref, document);
		
		return LOG.exit(workspace.getId());
	}
	
	@Override
	public Reference createDocument(String mediaType, InputStreamSupplier stream, JsonObject metadata) {
		LOG.entry(mediaType, metadata);
		Reference new_reference = new Reference(UUID.randomUUID().toString(),newVersion("0"));
        try {
			Document new_document = factory.buildDocument(new_reference, mediaType, stream, metadata, false);
			store.put(new_reference, new_document);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
		return LOG.exit(new_reference);
	}
	
	@Override
	public DocumentLink updateDocumentLink(String rootWorkspace, QualifiedName documentName, String mediaType, InputStreamSupplier stream,
			JsonObject metadata, Options.Update... options) throws InvalidWorkspace, InvalidWorkspaceState, InvalidObjectName {
		LOG.entry(rootWorkspace, documentName, mediaType, stream, metadata, Options.toString(options));
		WorkspaceImpl myRoot = root;
		if (rootWorkspace != null) {
			myRoot = workspacesById.get(rootWorkspace);
			if (myRoot == null) throw new InvalidWorkspace(rootWorkspace);
		}
		WorkspaceImpl workspace = myRoot.getOrCreateWorkspace(documentName.parent, Options.CREATE_MISSING_PARENT.isIn(options));
		Reference new_reference;
        DocumentInfo result;
        try {
			Optional<DocumentLink> doc = workspace.getDocument(documentName.part);
			if (doc.isPresent()) {
				new_reference = newVersionReference(doc.get().getId());
				result = workspace.update(new_reference, documentName.part);
				try {
					updateDocument(new_reference, mediaType, stream, metadata);
				} catch (InvalidReference e) {
					// Should only happen if new_reference isn't valid; which it shouldn't be because we just looked it up
					throw LOG.throwing(new RuntimeException(e));
				}
			} else {
				if (Options.CREATE_MISSING_ITEM.isIn(options)) {
					new_reference = new Reference(UUID.randomUUID().toString(),newVersion("0"));
					Document new_document = factory.buildDocument(new_reference, mediaType, stream, metadata, false);
					result = workspace.add(new_reference, documentName.part);
					store.put(new_reference, new_document);
				} else 
					throw new InvalidObjectName(rootWorkspace, documentName);
			}
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
		return LOG.exit(result.toStatic());
	}
	
	@Override
	public DocumentLink createDocumentLink(String rootWorkspace, QualifiedName documentName, String mediaType, InputStreamSupplier stream,
			JsonObject metadata, Options.Create... options) throws InvalidWorkspace, InvalidWorkspaceState, InvalidObjectName {
        Options.Update.Builder optionBuilder = Options.Update.EMPTY.addOptions(options).addOption(Options.CREATE_MISSING_ITEM);
		return updateDocumentLink(rootWorkspace, documentName, mediaType, stream, metadata, optionBuilder.build());
	
	}
	
	public static final String newVersion(String prev) {
		String result = Integer.toString(1 + Integer.parseInt(prev));
		return "0000000".substring(result.length()) + result;
	}
	
	private Reference newVersionReference(String id) {
		Reference old_reference = store.floorKey(new Reference(id));
		return new Reference(id, newVersion(old_reference.version));	
	}
	
	private Document updateDocument(
			Reference new_reference, 
			String mediaType, 
			InputStreamSupplier stream, 
			JsonObject metadata) throws InvalidReference {
		LOG.entry(new_reference, mediaType, metadata);
		Map.Entry<Reference,Document> previous = store.floorEntry(new Reference(new_reference.id));
		if (previous != null && previous.getKey().id.equals(new_reference.id)) {
			try {
                JsonObject previousMetadata = previous.getValue().getMetadata();
                InputStream previousStream = previous.getValue().getData(this);
                String previousType = previous.getValue().getMediaType();
				metadata = metadata == null ? previousMetadata : MetadataMerge.merge(previousMetadata, metadata);
				stream = stream == null ? ()->previousStream : stream;	
                mediaType = mediaType == null ? previousType : mediaType;
                Document newDocument = factory.buildDocument(new_reference, mediaType, stream, metadata, true);

                store.put(new_reference, newDocument);
				return LOG.exit(newDocument);
			} catch (IOException e) {
				throw new RuntimeException(LOG.throwing(e));
			}
		} else {
			throw LOG.throwing(new InvalidReference(new_reference));
		}
	}
	
	@Override
	public Reference updateDocument(String id, 
			String mediaType, 
			InputStreamSupplier stream, 
			JsonObject metadata) throws InvalidDocumentId {
		LOG.entry(id, mediaType, metadata);
		try {
			Reference new_reference = newVersionReference(id);
			Document newDocument = updateDocument(new_reference, mediaType, stream, metadata);
			return LOG.exit(new_reference);
		} catch (InvalidReference ref) {
			throw LOG.throwing(new InvalidDocumentId(id));
		}
	}
	
	
	static <T extends Document> Stream<T> latestVersionsOf(Stream<T> infos) {
		Comparator<T> COMPARE_REFS = Comparator.comparing(info->info.getReference());
		return infos
			.collect(Collectors.groupingBy((T info) -> info.getId(), 
				Collectors.collectingAndThen(Collectors.maxBy(COMPARE_REFS), Optional::get)))
			.values()
			.stream();
	}
	
	/** Catalog a repository.
	 * 
	 */
	@Override
	public Stream<Document> catalogue(Query filter, boolean searchHistory) {
		
		LOG.entry(filter, searchHistory);
        // IMPORTANT breaking change in support of #55 and #24.
        // The query namespace now includes all fields in the document link, including the parent folder, mediaType, etc.
        // Medatadata fields that used to be in the root of the query namespace must now be prefixed with 'metadata'.
		final Predicate<Document> filterPredicate = filter == null ? info -> true : info -> { 
            return filter.containsItem(info.toJson()); 
        };

		Stream<Document> infos = store.values().stream().map(doc->(Document)doc);

		if (searchHistory) {
			return LOG.exit(latestVersionsOf(infos.filter(filterPredicate)));
		} else {
			return LOG.exit(latestVersionsOf(infos).filter(filterPredicate));
		}
	}
	
	/** Catalog a repository.
	 * 
	 */
	@Override
	public Stream<NamedRepositoryObject> catalogueById(String workspaceId, Query filter, Options.Search... options) throws InvalidWorkspace {

		LOG.entry(workspaceId, filter, Options.loggable(options));
		
		if (workspaceId == null) throw LOG.throwing(new InvalidWorkspace("null"));

		WorkspaceImpl workspace = workspacesById.get(workspaceId);
		if (workspace == null) throw LOG.throwing(new InvalidWorkspace(workspaceId));
		return LOG.exit(workspace.catalogue(filter, Options.SEARCH_OLD_VERSIONS.isIn(options)));
	}

	static String nextSeq(String string) {
		int end = string.length() - 1;
		StringBuffer buf = new StringBuffer(string);
		buf.setCharAt(end, (char)(string.charAt(end) + 1));
		return buf.toString();
	}
		
	/** Catalog a repository.
	 * 
	 */
	@Override
	public Stream<NamedRepositoryObject> catalogueByName(String rootId, QualifiedName workspaceName, Query filter, Options.Search... options) throws InvalidWorkspace {

		LOG.entry(rootId, workspaceName, filter, Options.loggable(options));

		if (workspaceName == null) LOG.throwing(new InvalidWorkspace("null"));
		
		return LOG.exit(getRoot(rootId).catalogue(workspaceName, filter, Options.SEARCH_OLD_VERSIONS.isIn(options)));
	}

	
	public void clear() {
		store.clear();
		root = new WorkspaceImpl(this, null, UUID.randomUUID().toString(), null, State.Open, EMPTY_METADATA);
		workspacesById.clear();
		workspacesByDocument.clear();
	}

	@Override
	public Stream<Document> catalogueHistory(Reference ref, Query filter) throws InvalidReference {
        // IMPORTANT breaking change in support of #55 and #24.
        // The query namespace now includes all fields in the document link, including the parent folder, mediaType, etc.
        // Medatadata fields that used to be in the root of the query namespace must now be prefixed with 'metadata'.
	      final Predicate<Document> filterPredicate = filter == null ? info->true : info->filter.containsItem(info.toJson());
	        Reference first = new Reference(ref.id, newVersion("0"));
	        Collection<Document> history = store.subMap(first, true, ref, true).values();
	        if (history.isEmpty()) throw new InvalidReference(ref);
	        return history
	            .stream()
	            .filter(filterPredicate)
	            .map(item -> (Document)item);
	}
	
	public Workspace updateWorkspaceByName(String rootId, QualifiedName name, State state, JsonObject metadata, Options.Update... options) throws InvalidWorkspace {
		LOG.entry(rootId, name, state, Options.toString(options));

        if (name == null) name = QualifiedName.ROOT;

		WorkspaceImpl myRoot = root;
		
		if (rootId != null) {
		   myRoot = workspacesById.get(rootId);
		   if (myRoot == null) {
		        throw new InvalidWorkspace(rootId);
		   }
		}
		
		Optional<WorkspaceImpl> workspace = myRoot.getWorkspace(name);

		WorkspaceImpl ws;
		if (!workspace.isPresent()) {
			if (Options.CREATE_MISSING_ITEM.isIn(options)) {                
                Optional<WorkspaceImpl> parent = myRoot.getWorkspace(name.parent);
                if (parent.isPresent() || Options.CREATE_MISSING_PARENT.isIn(options))                        
                    ws = myRoot.createWorkspace(UUID.randomUUID().toString(), name, state, metadata, true);
                else
                    throw LOG.throwing(new InvalidWorkspace(rootId, name));
            } else
				throw LOG.throwing(new InvalidWorkspace(rootId, name));
		} else { 
			ws = workspace.get();
			if (state != null) ws.setState(state);
			ws.setMetadataInternal(MetadataMerge.merge(ws.getMetadata(), metadata));
		}
		return LOG.exit(ws);
	}
	
	@Override
	public NamedRepositoryObject getObjectByName(String rootId, QualifiedName objectName, Options.Get... options) throws InvalidObjectName, InvalidWorkspace {
		LOG.entry(objectName);
        WorkspaceImpl root = getRoot(rootId);
		NamedRepositoryObject result = objectName == null ? root : root.getObject(objectName);
		if (result == null) throw LOG.throwing(new InvalidWorkspace(rootId, objectName));
		return LOG.exit(result);
	}
	
	@Override
	public void deleteDocument(String workspaceId, QualifiedName path, String docId) throws InvalidWorkspace, InvalidDocumentId, InvalidWorkspaceState {
		LOG.entry(workspaceId, path, docId);
		if (docId == null) throw LOG.throwing(new InvalidDocumentId("null"));

		WorkspaceImpl root = getRoot(workspaceId);
		if (root == null) throw LOG.throwing(new InvalidWorkspace(workspaceId));
        
        WorkspaceImpl workspace = root.getWorkspace(path).orElseThrow(()->LOG.throwing(new InvalidWorkspace(workspaceId, path)));

		workspace.deleteById(docId);
		
		Set<String> docWorkspaces = workspacesByDocument.getOrDefault(docId, Collections.emptySet());
		docWorkspaces.remove(workspaceId);
		
		if (docWorkspaces.isEmpty()) workspacesByDocument.remove(docId);
		LOG.exit();
	}
	
	@Override
	public void deleteObjectByName(String rootId, QualifiedName objectName)
			throws InvalidWorkspace, InvalidObjectName, InvalidWorkspaceState {
		LOG.entry(objectName);
		if (objectName == null) throw LOG.throwing(new InvalidObjectName(rootId, objectName));

		WorkspaceImpl ws = getRoot(rootId)
			.getWorkspace(objectName.parent)
			.orElseThrow(()->LOG.throwing(new InvalidWorkspace(rootId, objectName.parent)));
		
		NamedRepositoryObject deleted = ws.deleteObjectByName(objectName.part);
		
		LOG.exit();
	}

	@Override
	public Stream<DocumentLink> listWorkspaces(String id, QualifiedName pathFilter, Query filter, Options.Search... options) throws InvalidDocumentId {
		LOG.entry(id);
        if (store.subMap(new Reference(id, ""), new Reference(id, null)).isEmpty()) throw new InvalidDocumentId(id);
		Set<String> workspaceIds = workspacesByDocument.get(id);
		if (workspaceIds == null) return LOG.exit(Stream.empty());
		// TODO: add pathFilter
		return LOG.exit( 
			workspaceIds.stream().map(wdId->{
				try {
					return workspacesById.get(wdId).getById(id);
				} catch (InvalidDocumentId e) {
					throw new RuntimeException("unexpected " + e);
				}
			}).filter(item->filter.containsItem(item.toJson(this, 1, 0)))
		);
	}

	@Override
	public Workspace createWorkspaceByName(String rootId, QualifiedName name, State state, JsonObject metadata, Options.Create... options) throws InvalidWorkspace {
		LOG.entry(rootId, name, state, Options.toString(options));

        if (name == null) name = QualifiedName.ROOT;

		WorkspaceImpl myRoot = getOrCreateRoot(rootId, Options.CREATE_MISSING_PARENT.isIn(options) || name.isEmpty());
		
		Optional<WorkspaceImpl> workspace = myRoot.getWorkspace(name);

		WorkspaceImpl ws;
        
		if (workspace.isPresent()) {
			throw LOG.throwing(new InvalidWorkspace(rootId, name));
        } else {
			ws = myRoot.createWorkspace(UUID.randomUUID().toString(), name, state, metadata, Options.CREATE_MISSING_PARENT.isIn(options));
 		} 
		return LOG.exit(ws);
	}
    
    @Override
    public Workspace createWorkspaceAndName(String rootId, QualifiedName name, State state, JsonObject metadata, Options.Create... options) throws InvalidWorkspace {
        String newName = UUID.randomUUID().toString();
        if (name == null) name = QualifiedName.ROOT;
        return createWorkspaceByName(rootId, name.add(newName), state, metadata, options);
    }

	@Override
    public DocumentLink createDocumentLink(String rootId, QualifiedName documentName, Reference reference, Options.Create... options) throws InvalidWorkspace, InvalidObjectName, InvalidWorkspaceState, InvalidReference {

        LOG.entry(rootId, documentName, reference);
        
        if (documentName == null) throw LOG.throwing(new InvalidObjectName(rootId, documentName));
        if (reference == null) throw LOG.throwing(new InvalidReference(reference));
        
        WorkspaceImpl workspace = getRoot(rootId).getOrCreateWorkspace(documentName.parent, Options.CREATE_MISSING_PARENT.isIn(options));
        
        Optional<NamedRepositoryObject> obj = workspace.getObject(documentName.part);
        
        if (obj.isPresent()) {
            throw LOG.throwing(new InvalidObjectName(rootId, documentName));
        } else {
            return LOG.exit(workspace.add(reference, documentName.part));
        }
    }
	
	@Override
	public DocumentLink updateDocumentLink(String rootId, QualifiedName documentName, Reference reference, Options.Update... options) throws InvalidWorkspace, InvalidObjectName, InvalidWorkspaceState, InvalidReference {

	    LOG.entry(rootId, documentName, reference);

	    if (documentName == null) throw LOG.throwing(new InvalidObjectName(rootId, documentName));
	    if (reference == null) throw LOG.throwing(new InvalidReference(reference));

	    WorkspaceImpl workspace = getRoot(rootId).getOrCreateWorkspace(documentName.parent, Options.CREATE_MISSING_PARENT.isIn(options));

	    Optional<NamedRepositoryObject> obj = workspace.getObject(documentName.part);

	    if (obj.isPresent()) {  
	        if (obj.get().getType() != RepositoryObject.Type.DOCUMENT_LINK)
	            throw new InvalidObjectName(rootId, documentName);
	        else
	            return LOG.exit(workspace.update(reference, documentName.part));
	    } else {
	        if (Options.CREATE_MISSING_ITEM.isIn(options))
	            return LOG.exit(workspace.add(reference, documentName.part));
	        else
	            throw LOG.throwing(new InvalidObjectName(rootId, documentName));
	    }
	}

    @Override
    public DocumentLink createDocumentLinkAndName(String rootId, QualifiedName workspaceName, Reference reference, Options.Create... options) throws InvalidWorkspace, InvalidWorkspaceState, InvalidReference {

        LOG.entry(rootId, workspaceName, reference, Options.toString(options));
        
        if (reference == null) throw LOG.throwing(new InvalidReference(reference));
        
        WorkspaceImpl workspace;
        
        boolean createParent = Options.CREATE_MISSING_PARENT.isIn(options);
        workspace = getOrCreateRoot(rootId, createParent).getOrCreateWorkspace(workspaceName, createParent);
        
        return LOG.exit(workspace.add(reference, Options.RETURN_EXISTING_LINK_TO_SAME_DOCUMENT.isIn(options)));
    }

    @Override
    public DocumentLink createDocumentLinkAndName(String rootWorkspace, QualifiedName workspaceName, String mediaType, InputStreamSupplier stream, JsonObject metadata, Options.Create... options) throws InvalidWorkspace, InvalidWorkspaceState {
		LOG.entry(rootWorkspace, workspaceName, mediaType, stream, metadata, Options.toString(options));
        boolean createWorkspace = Options.CREATE_MISSING_PARENT.isIn(options);
		WorkspaceImpl myRoot = getOrCreateRoot(rootWorkspace, createWorkspace);
		WorkspaceImpl workspace = myRoot.getOrCreateWorkspace(workspaceName, createWorkspace);
		Reference new_reference;
		try {
			new_reference = new Reference(UUID.randomUUID().toString(),newVersion("0"));
			Document new_document = factory.buildDocument(new_reference, mediaType, stream, metadata, false);
			store.put(new_reference, new_document);
			return LOG.exit(workspace.add(new_reference, false));
		} catch (InvalidReference | IOException e) {
			throw new RuntimeException(LOG.throwing(e));	
		}
	}
    
    @Override
    public Workspace getWorkspaceByName(String rootId, QualifiedName name) throws InvalidWorkspace, InvalidObjectName {
        NamedRepositoryObject object = getObjectByName(rootId, name);
        if (object.getType() == RepositoryObject.Type.WORKSPACE) return (Workspace)object;
        throw new InvalidObjectName(rootId, name);
    }

    @Override
    public DocumentLink getDocumentLink(String rootId, QualifiedName name, Options.Get... options) throws InvalidWorkspace, InvalidObjectName {
        NamedRepositoryObject object = getObjectByName(rootId, name);
        if (object.getType() == RepositoryObject.Type.DOCUMENT_LINK) return (DocumentLink)object;
        throw new InvalidObjectName(rootId, name);
    }

    @Override
    public DocumentLink getDocumentLink(String workspaceId, QualifiedName path, String documentId, Options.Get... options) throws InvalidWorkspace, InvalidObjectName, InvalidDocumentId {
 		LOG.entry(workspaceId, path, documentId);
		WorkspaceImpl workspace = getRoot(workspaceId);
		if (workspace == null) throw LOG.throwing(new InvalidWorkspace(workspaceId));
        workspace = workspace.getWorkspace(path).orElseThrow(()->new InvalidObjectName(workspaceId, path));
		DocumentLink result = workspace.getById(documentId);
		if (result == null) throw LOG.throwing(new InvalidDocumentId(documentId));
		return LOG.exit(result);    
    }
    
    @Override 
    public DocumentLink copyDocumentLink(DocumentLink srcLink, Workspace targetWorkspace, String targetName) throws InvalidWorkspaceState, InvalidObjectName {
        Reference reference = srcLink.getReference();
        try {
            DocumentLink tgtLink = createDocumentLink(targetWorkspace.getId(), QualifiedName.of(targetName), srcLink.getReference());
    		return LOG.exit(tgtLink); 
        } catch (InvalidWorkspace | InvalidReference e) {
            throw new BaseRuntimeException(e);
        }        
    }

    @Override
    public DocumentLink copyDocumentLink(String sourceRootId, QualifiedName sourceName, String targetRootId, QualifiedName targetName, boolean createWorkspace) throws InvalidWorkspaceState, InvalidWorkspace, InvalidObjectName {
  		LOG.entry(sourceRootId, sourceName, targetRootId, targetName, createWorkspace);
		DocumentLink srcLink = getDocumentLink(sourceRootId, sourceName);
        Reference reference = srcLink.getReference();
        try {
            DocumentLink tgtLink = createDocumentLink(targetRootId, targetName, srcLink.getReference(), Options.Create.EMPTY.addOptionIf(Options.CREATE_MISSING_PARENT, createWorkspace).build());
    		return LOG.exit(tgtLink); 
        } catch (InvalidReference ref) {
            throw new BaseRuntimeException(ref);
        }
    }
    
    public void copyWorkspaceContent(Workspace workspace, Workspace targetWorkspace) throws InvalidWorkspaceState, InvalidObjectName {
        Stream<NamedRepositoryObject> searchResults = catalogueByName(workspace, QualifiedName.of("*"), Query.UNBOUNDED);
        
        for (Iterator<NamedRepositoryObject> iter = searchResults.iterator(); iter.hasNext(); ) { 
            try { 
                NamedRepositoryObject object = iter.next();
                copyObject(object, targetWorkspace, object.getName().part); 
            } catch (InvalidWorkspaceState e) { 
                throw new BaseRuntimeException(e);
            } 
        };
    }
    
    public Workspace copyWorkspace(Workspace src, Workspace tgt, String targetName) throws InvalidWorkspaceState, InvalidObjectName {
        Workspace tgtWorkspace = createWorkspaceByName(tgt, targetName, Workspace.State.Open, src.getMetadata());
        copyWorkspaceContent(src, tgtWorkspace);
        return tgtWorkspace;
    }

    @Override
    public Workspace copyWorkspace(String sourceRootId, QualifiedName sourceName, String targetRootId, QualifiedName targetName, boolean createParent) throws InvalidWorkspaceState, InvalidWorkspace, InvalidObjectName {
  		LOG.entry(sourceRootId, sourceName, targetRootId, targetName, createParent);
		Workspace srcWorkspace = getWorkspaceByName(sourceRootId, sourceName);
        Workspace tgtWorkspace = createWorkspaceByName(targetRootId, targetName, Workspace.State.Open, srcWorkspace.getMetadata(), Options.Create.EMPTY.addOptionIf(Options.CREATE_MISSING_PARENT, createParent).build());
        copyWorkspaceContent(srcWorkspace, tgtWorkspace);
        return tgtWorkspace;
    }

    @Override
    public NamedRepositoryObject copyObject(String sourceRootId, QualifiedName sourceName, String targetRootId, QualifiedName targetName, boolean createParent) throws InvalidWorkspaceState, InvalidWorkspace, InvalidObjectName {
  		LOG.entry(sourceRootId, sourceName, targetRootId, targetName, createParent);
		NamedRepositoryObject srcObject = getObjectByName(sourceRootId, sourceName);
        Workspace tgtWorkspace = updateWorkspaceByName(targetRootId, targetName.parent, null, null, Options.Update.EMPTY.addOptionIf(Options.CREATE_MISSING_PARENT, createParent).build());
        
        switch (srcObject.getType()) {
             case DOCUMENT_LINK: return copyDocumentLink((DocumentLink)srcObject, tgtWorkspace, targetName.part);
             case WORKSPACE: return copyWorkspace((Workspace)srcObject, tgtWorkspace, targetName.part);
             default: throw new RuntimeException("Invalid type " + srcObject.getType());
        }
    }

    @Override
    public InputStream getData(String rootId, QualifiedName objectName, Options.Get... gets) throws InvalidObjectName, IOException {
        try {
            return ((StreamableRepositoryObject)getObjectByName(rootId, objectName)).getData(this);
        } catch (InvalidWorkspace e) {
            throw new InvalidObjectName(rootId, objectName);
        }
    }

    @Override
    public void writeData(String rootId, QualifiedName objectName, OutputStream out, Options.Get... gets) throws InvalidObjectName, IOException {
        try {
            ((StreamableRepositoryObject)getObjectByName(rootId, objectName)).writeDocument(this, out);
        } catch (InvalidWorkspace e) {
            throw new InvalidObjectName(rootId, objectName);
        }
    }

    @Override
    public DocumentPart getPart(Reference rfrnc, QualifiedName objectName) throws InvalidReference, InvalidObjectName {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public InputStream getData(Reference rfrnc, Optional<QualifiedName> partName) throws InvalidReference, InvalidObjectName, IOException {
        if (partName.isPresent()) throw new UnsupportedOperationException("Not supported yet.");
        else return getDocument(rfrnc).getData(this);
    }

    @Override
    public void writeData(Reference rfrnc, Optional<QualifiedName> partName, OutputStream out) throws InvalidReference, InvalidObjectName, IOException {
        if (partName.isPresent()) throw new UnsupportedOperationException("Not supported yet.");
        else getDocument(rfrnc).writeDocument(this, out);
    }

    @Override
    public Stream<DocumentPart> catalogueParts(Reference rfrnc, QualifiedName qn) throws InvalidReference, InvalidObjectName {
        return Collections.EMPTY_LIST.stream();
    }
    
    public <T> Optional<T> getImplementation(Class<T> clazz) {
        return clazz.isAssignableFrom(clazz) ? Optional.of((T)this) : Optional.empty();
    }
}
