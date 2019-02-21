package com.softwareplumbers.dms.rest.server.tmp;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.UUID;

import javax.json.Json;
import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractquery.ObjectConstraint;
import com.softwareplumbers.common.abstractquery.Value.MapValue;
import com.softwareplumbers.dms.rest.server.model.DocumentImpl;
import com.softwareplumbers.dms.rest.server.model.Info;
import com.softwareplumbers.dms.rest.server.model.Document;
import com.softwareplumbers.dms.rest.server.model.InputStreamSupplier;
import com.softwareplumbers.dms.rest.server.model.MetadataMerge;
import com.softwareplumbers.dms.rest.server.model.Reference;
import com.softwareplumbers.dms.rest.server.model.RepositoryService;
import com.softwareplumbers.dms.rest.server.model.Workspace;
import com.softwareplumbers.dms.rest.server.model.Workspace.State;
import com.softwareplumbers.dms.rest.server.util.Log;

/** In-memory repository implementation.
 * 
 * Intended as a reference implementation in terms of behavior, and for test
 * purposes. Not thread-safe, so not suitable for production use.
 * 
 * @author SWPNET\jonessex
 *
 */
public class TempRepositoryService implements RepositoryService {
	
	///////////--------- Static member variables --------////////////
	static Log LOG = new Log(TempRepositoryService.class);
	static JsonObject EMPTY_METADATA = Json.createObjectBuilder().build();
	
	
	TreeMap<Reference,DocumentImpl> store = new TreeMap<>();
	private TreeMap<UUID, WorkspaceImpl> workspacesById = new TreeMap<>();
	private TreeMap<String, Set<UUID>> workspacesByDocument = new TreeMap<>();
	private WorkspaceImpl root = new WorkspaceImpl(this, null, UUID.randomUUID(), null, State.Open, EMPTY_METADATA);
	QualifiedName nameAttribute;
	
	public TempRepositoryService(QualifiedName nameAttribute) {
		this.nameAttribute = nameAttribute;
	}
	
	public TempRepositoryService(String nameAttribute) {
		this.nameAttribute = QualifiedName.parse(nameAttribute, "/");
	}

	@Override
	public Document getDocument(Reference reference) throws InvalidReference {
		LOG.logEntering("getDocument", reference);
		Document result = null;
		if (reference.version == null) {
			Map.Entry<Reference,DocumentImpl> previous = store.floorEntry(reference);

			if (previous != null && reference.id.equals(previous.getKey().id)) 
				result = previous.getValue();  
		} else {
			result = store.get(reference);
		}
		if (result == null) throw LOG.logThrow("getDocument",new InvalidReference(reference));
		return LOG.logReturn("getDocument", result);
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
		LOG.logEntering("updateWorkspace", ref, createWorkspace);
		
		if (workspaceId == null) return null;
		
		UUID id = workspaceId == null ? null : UUID.fromString(workspaceId);
		
		WorkspaceImpl workspace = null;
		if (id != null) {
			workspace = workspacesById.get(id);
			if (workspace == null && !createWorkspace) 
				throw LOG.logThrow("updateWorkspace",new InvalidWorkspace(workspaceId));
		} 
		
		if (workspace == null) {
			if (id == null) id = UUID.randomUUID();
			workspace = new WorkspaceImpl(this, null, id, null, State.Open, EMPTY_METADATA);
			workspacesById.put(id, workspace);
		}
		
		if (workspace.getState() == State.Closed) throw LOG.logThrow("updateWorkspace", new InvalidWorkspaceState(workspaceId, State.Closed));
		
		workspace.add(ref, document);
		workspacesByDocument.computeIfAbsent(ref.id, key -> new TreeSet<UUID>()).add(id);
	
		return workspace.id.toString();
	}
	
	@Override
	public Reference createDocument(MediaType mediaType, InputStreamSupplier stream, JsonObject metadata, String workspaceId, boolean createWorkspace) throws InvalidWorkspace, InvalidWorkspaceState {
		LOG.logEntering("createDocument", mediaType, metadata, workspaceId, createWorkspace);
		Reference new_reference = new Reference(UUID.randomUUID().toString(),newVersion("0"));
		try {
			DocumentImpl new_document = new DocumentImpl(mediaType, stream, metadata);
			updateWorkspace(workspaceId, new_reference, new_document, createWorkspace);
			store.put(new_reference, new_document);
		} catch (IOException e) {
			throw new RuntimeException(LOG.logRethrow("createDocument",e));
		}
		return LOG.logReturn("createDocument",new_reference);
	}
	
	@Override
	public Reference createDocumentByName(QualifiedName documentName, MediaType mediaType, InputStreamSupplier stream,
			JsonObject metadata, boolean createWorkspace) throws InvalidWorkspace, InvalidWorkspaceState {
		WorkspaceImpl workspace = root.getOrCreateWorkspace(documentName.parent, createWorkspace);
		Reference new_reference = new Reference(UUID.randomUUID().toString(),newVersion("0"));
		try {
			DocumentImpl new_document = new DocumentImpl(mediaType, stream, metadata);
			store.put(new_reference, new_document);
			workspace.add(new_reference, documentName.part);
		} catch (IOException e) {
			throw new RuntimeException(LOG.logRethrow("createDocumentByName",e));	
		}
		return LOG.logReturn("createDocumentByName",new_reference);
	}
	
	public static final String newVersion(String prev) {
		String result = Integer.toString(1 + Integer.parseInt(prev));
		return "0000000".substring(result.length()) + result;
	}
	
	@Override
	public Reference updateDocument(String id, 
			MediaType mediaType, 
			InputStreamSupplier stream, 
			JsonObject metadata, 
			String workspaceId, 
			boolean createWorkspace) throws InvalidDocumentId, InvalidWorkspace, InvalidWorkspaceState {
		LOG.logEntering("updateDocument", id, mediaType, metadata, workspaceId, createWorkspace);
		Map.Entry<Reference,DocumentImpl> previous = store.floorEntry(new Reference(id));
		if (previous != null && previous.getKey().id.equals(id)) {
			Reference new_reference = new Reference(id, newVersion(previous.getKey().version));
			DocumentImpl newDocument = previous.getValue();
			try {
				if (metadata != null) newDocument = newDocument.setMetadata(MetadataMerge.merge(newDocument.getMetadata(), metadata));
				if (stream != null) newDocument = newDocument.setData(stream);			
				updateWorkspace(workspaceId, new_reference, newDocument, createWorkspace);
				store.put(new_reference, newDocument);
				return LOG.logReturn("updateDocument",new_reference);
			} catch (IOException e) {
				throw new RuntimeException(LOG.logRethrow("updateDocument", e));
			}
		} else {
			throw LOG.logThrow("updateDocument", new InvalidDocumentId(id));
		}
	}

	Info info(QualifiedName name, Reference ref) {
		try {
			return new Info(name, ref, getDocument(ref));
		} catch (InvalidReference err) {
			throw new RuntimeException(err);
		}
	}
	
	Info info(Workspace ws) {
		return new Info(ws);
	}
	
	private Info info(QualifiedName name, Map.Entry<Reference, DocumentImpl> entry) {
		return new Info(name, entry.getKey(), entry.getValue()); 
	}
	
	Stream<Info> historicalInfo(QualifiedName path, Reference ref, ObjectConstraint filter) {
		try {
			return catalogueHistory(ref, filter);
		} catch (InvalidReference err) {
			throw new RuntimeException(err);
		}
	}
	
	static Stream<Info> latestVersionsOf(Stream<Info> infos) {
		Comparator<Info> COMPARE_REFS = Comparator.comparing(info->info.reference);
		return infos
			.collect(Collectors.groupingBy((Info info) -> info.reference.id, 
				Collectors.collectingAndThen(Collectors.maxBy(COMPARE_REFS), Optional::get)))
			.values()
			.stream();
	}
	
	/** Catalog a repository.
	 * 
	 */
	@Override
	public Stream<Info> catalogue(ObjectConstraint filter, boolean searchHistory) {
		
		LOG.logEntering("catalogue", filter, searchHistory);

		final Predicate<Info> filterPredicate = filter == null ? info->true : info->filter.containsItem(MapValue.from(info.metadata));

		Stream<Info> infos = store.entrySet()
				.stream()
				.map(entry -> info(QualifiedName.ROOT, entry));
		if (searchHistory) {
			return LOG.logReturn("catalogue", latestVersionsOf(infos.filter(filterPredicate)));
		} else {
			return LOG.logReturn("catalogue", latestVersionsOf(infos).filter(filterPredicate));
		}
	}
	
	/** Catalog a repository.
	 * 
	 */
	@Override
	public Stream<Info> catalogueById(String workspaceId, ObjectConstraint filter, boolean searchHistory) throws InvalidWorkspace {

		LOG.logEntering("catalogueById", filter, searchHistory);
		
		if (workspaceId == null) throw LOG.logThrow("catalogueById", new InvalidWorkspace("null"));

		UUID id = UUID.fromString(workspaceId);
		WorkspaceImpl workspace = workspacesById.get(id);
		if (workspace == null) throw LOG.logThrow("catalogueById", new InvalidWorkspace(workspaceId));
		return LOG.logReturn("catalogueById", workspace.catalogue(filter, searchHistory));
	}

	static String nextSeq(String string) {
		int end = string.length() - 1;
		StringBuffer buf = new StringBuffer(string);
		buf.setCharAt(end, (char)(string.charAt(end) + 1));
		return buf.toString();
	}
	
	static String wildcardToRegex(String string) {
		StringBuffer buffer = new StringBuffer();
		for (int i = 0; i < string.length(); i++) {
			char c = string.charAt(i);
			switch (c) {
				case '.': 
					buffer.append("\\.");
					break;
				case '?': 
					buffer.append(".");
					break;
				case '*': 
					buffer.append(".*");
					break;
				default: buffer.append(c);
			}
		}
		return buffer.toString();
	}
		
	/** Catalog a repository.
	 * 
	 */
	@Override
	public Stream<Info> catalogueByName(QualifiedName workspaceName, ObjectConstraint filter, boolean searchHistory) throws InvalidWorkspace {

		LOG.logEntering("catalogueByName", filter, searchHistory);

		if (workspaceName == null) LOG.logThrow("catalogueByName", new InvalidWorkspace("null"));
		
		return LOG.logReturn("catalogueByName", root.catalogue(workspaceName, filter, searchHistory));
	}

	
	public void clear() {
		store.clear();
		root = new WorkspaceImpl(this, null, UUID.randomUUID(), null, State.Open, EMPTY_METADATA);
		workspacesById.clear();
		workspacesByDocument.clear();
	}

	private Stream<Info> catalogueHistory(QualifiedName path, Reference ref, ObjectConstraint filter) throws InvalidReference {
		final Predicate<Info> filterPredicate = filter == null ? info->true : info->filter.containsItem(MapValue.from(info.metadata));
		Map<Reference, DocumentImpl> history = store.subMap(new Reference(ref.id,newVersion("0")), true, ref, true);
		if (history.isEmpty()) throw new InvalidReference(ref);
		return history
			.entrySet()
			.stream()
			.map(entry -> info(path, entry))
			.filter(filterPredicate);
	}

	@Override
	public Stream<Info> catalogueHistory(Reference ref, ObjectConstraint filter) throws InvalidReference {
		return catalogueHistory(QualifiedName.ROOT, ref, filter);
	}

	private <T> String updateWorkspaceByIndex(Function<T,WorkspaceImpl> index, T key, UUID id, QualifiedName name, State state, JsonObject metadata, boolean createWorkspace) throws InvalidWorkspace {
		LOG.logEntering("updateWorkspaceByIndex", index, key, id, name, state, createWorkspace);
		if (key == null) throw LOG.logThrow("updateWorkspaceByIndex",new InvalidWorkspace("null"));
		WorkspaceImpl workspace = index.apply(key);
		if (workspace == null && createWorkspace) {
			if (id == null) id = UUID.randomUUID();
			if (name != null) {
				workspace = root.createWorkspace(id, name, state, metadata);
			} else {
				workspace = new WorkspaceImpl(this, null, id, null, state, metadata);
			}
			workspacesById.put(id, workspace);
		} else {
			if (workspace == null) throw LOG.logThrow("updateWorkspaceByIndex", new InvalidWorkspace(key.toString()));
			workspace.setState(state);
			workspace.setMetadata(MetadataMerge.merge(workspace.getMetadata(), metadata));
			if (name != null && !name.equals(workspace.getName())) {
				workspace.setName(root, name, createWorkspace);
			}
		}
		return LOG.logReturn("updateWorkspaceByIndex", id == null ? null : id.toString());
	}
	
	public String updateWorkspaceById(String workspaceId, QualifiedName name, State state, JsonObject metadata, boolean createWorkspace) throws InvalidWorkspace {
		LOG.logEntering("updateWorkspaceById", workspaceId, name, state, createWorkspace);
		if (workspaceId == null) throw LOG.logThrow("updateWorkspaceById",new InvalidWorkspace("null"));
		UUID id = UUID.fromString(workspaceId);
		return LOG.logReturn("updateWorkspaceById", updateWorkspaceByIndex(ws->workspacesById.get(ws), id, id, name, state, metadata, createWorkspace));
	}

	public String updateWorkspaceByName(QualifiedName name, QualifiedName newName, State state, JsonObject metadata, boolean createWorkspace) throws InvalidWorkspace {
		LOG.logEntering("updateWorkspaceById", name, newName, state, createWorkspace);
		return LOG.logReturn("updateWorkspaceById",updateWorkspaceByIndex(ws->root.getWorkspace(ws), name, null, newName == null ? name : newName, state, metadata, createWorkspace));
	}

	@Override
	public Workspace getWorkspaceById(String workspaceId) throws InvalidWorkspace {
		LOG.logEntering("getWorkspaceById", workspaceId);
		if (workspaceId == null) throw LOG.logThrow("getWorkspaceById", new InvalidWorkspace("null"));
		UUID id = UUID.fromString(workspaceId);
		Workspace result = workspacesById.get(id);
		if (result == null) throw LOG.logThrow("getWorkspaceById", new InvalidWorkspace(workspaceId));
		return LOG.logReturn("getWorkspaceById",result);
	}

	@Override
	public Workspace getWorkspaceByName(QualifiedName workspaceName) throws InvalidWorkspace {
		LOG.logEntering("getWorkspaceByName", workspaceName);
		if (workspaceName == null) throw LOG.logThrow("getWorkspaceByName", new InvalidWorkspace("null"));
		Workspace result = root.getWorkspace(workspaceName);
		if (result == null) throw LOG.logThrow("getWorkspaceByName", new InvalidWorkspace(workspaceName));
		return LOG.logReturn("getWorkspaceByName",result);
	}
	
	@Override
	public void deleteDocument(String workspaceId, String docId) throws InvalidWorkspace, InvalidDocumentId, InvalidWorkspaceState {
		LOG.logEntering("deleteDocument", workspaceId, docId);
		if (workspaceId == null) throw LOG.logThrow("getWorkspaceById", new InvalidWorkspace("null"));
		if (docId == null) throw LOG.logThrow("getWorkspaceById", new InvalidDocumentId("null"));

		UUID id = UUID.fromString(workspaceId);
		WorkspaceImpl result = workspacesById.get(id);
		if (result == null) throw LOG.logThrow("deleteDocument", new InvalidWorkspace(workspaceId));

		result.deleteById(docId);
		
		Set<UUID> docWorkspaces = workspacesByDocument.getOrDefault(docId, Collections.emptySet());
		docWorkspaces.remove(id);
		
		if (docWorkspaces.isEmpty()) workspacesByDocument.remove(docId);
		LOG.logExiting("deleteDocument");
	}

	@Override
	public Stream<Info> catalogueParts(Reference ref, ObjectConstraint filter) throws InvalidReference {
		return Stream.empty();
	}

	@Override
	public Stream<Workspace> listWorkspaces(String id) {
		LOG.logEntering("listWorkspaces", id);
		Set<UUID> workspaceIds = workspacesByDocument.get(id);
		if (workspaceIds == null) return LOG.logReturn("listWorkspaces", Stream.empty());
		return LOG.logReturn("listWorkspaces", workspaceIds.stream().map(name->workspacesById.get(name)));
	}

	@Override
	public String createWorkspace(QualifiedName name, State state, JsonObject metadata) throws InvalidWorkspace {
		UUID id = UUID.randomUUID();
		return updateWorkspaceByIndex(ws->workspacesById.get(ws), id, id, name, state, metadata, true);
	}

}
