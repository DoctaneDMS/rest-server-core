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

import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractquery.ObjectConstraint;
import com.softwareplumbers.common.abstractquery.Range;
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
	
	private class WorkspaceInfo {
		public boolean deleted;
		
		public WorkspaceInfo(boolean deleted) {
			this.deleted = deleted;
		}
	}
	
	private class WorkspaceImpl implements Workspace {
		
		private WorkspaceImpl parent;
		private String name;
		private UUID id;
		private Map<Reference,WorkspaceInfo> docs;
		private TreeMap<String, WorkspaceImpl> children;
		private State state;
		
		public WorkspaceImpl(WorkspaceImpl parent, UUID id, String name, State state) {
			this.id = id;
			this.name = name;
			this.state = state;
			this.docs = new TreeMap<Reference, WorkspaceInfo>();
			this.children = new TreeMap<String, WorkspaceImpl>();
			this.parent = parent;
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
			
			final Function<Map.Entry<Reference, WorkspaceInfo>, Reference> MAP_SPECIFIC = entry->store.floorKey(entry.getKey());
			final Function<Map.Entry<Reference, WorkspaceInfo>, Reference> MAP_LATEST = entry->new Reference(entry.getKey().id);
			
			// Convert references to point to a specific version
			// of a document when a workspace is closed or finalized
			if (this.state == State.Open && state != State.Open)
				docs = docs.entrySet().stream()
					.collect(Collectors.toMap(MAP_SPECIFIC, entry->entry.getValue()));
	
			// Convert references to point to a the most recent version
			// of a document when a workspace is opened
			if (this.state != State.Open && state == State.Open)
				docs = docs.entrySet().stream()
					.collect(Collectors.toMap(MAP_LATEST, entry->entry.getValue()));
					
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
		
		public void add(Reference reference) throws InvalidWorkspaceState {
			LOG.logEntering("add", reference);
			if (state == State.Open)
				this.docs.put(new Reference(reference.id), new WorkspaceInfo(false));
			else throw LOG.logThrow("add", new InvalidWorkspaceState(name, state));
			LOG.logExiting("add");
		}
		
		public void delete(String id) throws InvalidDocumentId, InvalidWorkspaceState {
			LOG.logEntering("delete", id);
			if (state == State.Open) {
				Reference ref = new Reference(id);
				WorkspaceInfo info = docs.get(ref);
				if (info == null) throw LOG.logThrow("delete",new InvalidDocumentId(id));
				info.deleted = true;
			} else {
				throw LOG.logThrow("delete",new InvalidWorkspaceState(name, state));
			}
			LOG.logExiting("delete");
		}

		public Stream<Info> catalogue(ObjectConstraint filter, boolean searchHistory) {
			
			final Predicate<Info> filterPredicate = filter == null ? info->true : info->filter.containsItem(MapValue.from(info.metadata));
			Stream<Map.Entry<Reference, WorkspaceInfo>> entries = docs.entrySet().stream();

			if (searchHistory) {
				// Search all historical info, and return latest matching version
				// of any document.
				return latestVersionsOf(
					entries
						.flatMap(entry -> historicalInfo(entry.getKey(), filter))
				);
			} else {
				// Now convert refs to info and filter it
				return entries
					.filter(entry -> !entry.getValue().deleted)
					.map(entry -> info(entry.getKey()))
					.filter(filterPredicate);
			}
		}
		
		public Stream<Info> catalogue(QualifiedName workspaceName, ObjectConstraint filter, boolean searchHistory) throws InvalidWorkspace {
			
			if (workspaceName == QualifiedName.ROOT) return catalogue(filter, searchHistory);

			String firstPart = workspaceName.get(0);
			int star = firstPart.indexOf('*'); 
			int questionMark = firstPart.indexOf('?'); 
			int firstWildcard = star < 0 || questionMark < 0 ? Math.max(star, questionMark) : Math.min(star, questionMark);

			if (firstWildcard < 0) {
				WorkspaceImpl child = children.get(firstPart);
				if (child == null) throw new InvalidWorkspace(workspaceName);
				return child.catalogue(workspaceName.rightFromStart(1), filter, searchHistory);
			}
						
			String lowerBound = firstPart.substring(0, firstWildcard);
			String upperBound = nextSeq(lowerBound);
			String pattern = wildcardToRegex(firstPart);
				
			return children.subMap(lowerBound, upperBound).entrySet()
				.stream()
				.filter(e -> e.getKey().matches(pattern))
				.map(e -> e.getValue())
				.flatMap(workspace -> workspace.catalogue(filter,  searchHistory));
		}
		
		public WorkspaceImpl getOrCreateWorkspace(QualifiedName name, boolean createWorkspace) throws InvalidWorkspace {
			
			String firstPart = name.get(0);
			QualifiedName remainingName = name.rightFromStart(1);

			WorkspaceImpl child = children.get(firstPart);
			if (child == null) {
				if (createWorkspace) {
					child = new WorkspaceImpl(this, UUID.randomUUID(), firstPart, State.Open);
					children.put(firstPart, child);
				} else 
					throw new InvalidWorkspace(name);
			}
			
			if (remainingName.isEmpty())
				return child;
			else			
				return child.getOrCreateWorkspace(remainingName, createWorkspace);
		}
		
		public WorkspaceImpl createWorkspace(UUID id, QualifiedName name, State state) throws InvalidWorkspace {
			WorkspaceImpl parent = name.parent.isEmpty() ? this : getOrCreateWorkspace(name.parent, true);
			if (parent.children.containsKey(name.part)) throw new InvalidWorkspace(name);
			WorkspaceImpl child = new WorkspaceImpl(parent, id, name.part, state);
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
	}
	
	///////////--------- Static member variables --------////////////
	private static Log LOG = new Log(TempRepositoryService.class);
	
	
	private TreeMap<Reference,DocumentImpl> store = new TreeMap<>();
	private TreeMap<UUID, WorkspaceImpl> workspacesById = new TreeMap<>();
	private TreeMap<String, Set<UUID>> workspacesByDocument = new TreeMap<>();
	private WorkspaceImpl root = new WorkspaceImpl(null, UUID.randomUUID(), null, State.Open);

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
	private String updateWorkspace(String workspaceId, Reference ref, boolean createWorkspace) throws InvalidWorkspace, InvalidWorkspaceState {
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
			workspace = new WorkspaceImpl(null, id, null, State.Open);
			workspacesById.put(id, workspace);
		}
		
		if (workspace.getState() == State.Closed) throw LOG.logThrow("updateWorkspace", new InvalidWorkspaceState(workspaceId, State.Closed));
		
		workspace.add(ref);
		workspacesByDocument.computeIfAbsent(ref.id, key -> new TreeSet<UUID>()).add(id);
	
		return workspace.id.toString();
	}
	
	@Override
	public Reference createDocument(MediaType mediaType, InputStreamSupplier stream, JsonObject metadata, String workspaceId, boolean createWorkspace) throws InvalidWorkspace, InvalidWorkspaceState {
		LOG.logEntering("createDocument", mediaType, metadata, workspaceId, createWorkspace);
		Reference new_reference = new Reference(UUID.randomUUID().toString(),newVersion("0"));
		try {
			DocumentImpl new_document = new DocumentImpl(mediaType, stream, metadata);
			updateWorkspace(workspaceId, new_reference, createWorkspace);
			store.put(new_reference, new_document);
			return LOG.logReturn("createDocument",new_reference);
		} catch (IOException e) {
			throw new RuntimeException(LOG.logRethrow("createDocument",e));
		}
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
				updateWorkspace(workspaceId, new_reference, createWorkspace);
				store.put(new_reference, newDocument);
				return LOG.logReturn("updateDocument",new_reference);
			} catch (IOException e) {
				throw new RuntimeException(LOG.logRethrow("updateDocument", e));
			}
		} else {
			throw LOG.logThrow("updateDocument", new InvalidDocumentId(id));
		}
	}

	private Info info(Reference ref) {
		try {
			return new Info(ref, getDocument(ref));
		} catch (InvalidReference err) {
			throw new RuntimeException(err);
		}
	}
	
	private Info info(Map.Entry<Reference, DocumentImpl> entry) {
		return new Info(entry.getKey(), entry.getValue()); 
	}
	
	private Stream<Info> historicalInfo(Reference ref, ObjectConstraint filter) {
		try {
			return catalogueHistory(ref, filter);
		} catch (InvalidReference err) {
			throw new RuntimeException(err);
		}
	}
	
	private static Stream<Info> latestVersionsOf(Stream<Info> infos) {
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
				.map(entry -> info(entry));
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

	private static String nextSeq(String string) {
		int end = string.length() - 1;
		StringBuffer buf = new StringBuffer(string);
		buf.setCharAt(end, (char)(string.charAt(end) + 1));
		return buf.toString();
	}
	
	private static String wildcardToRegex(String string) {
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
	
	private static QualifiedName wildcardToRegex(QualifiedName name) {
		QualifiedName result = QualifiedName.ROOT;
		for (String part: name) result = result.add(wildcardToRegex(part));
		return result;
		
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
		root = new WorkspaceImpl(null, UUID.randomUUID(), null, State.Open);
		workspacesById.clear();
		workspacesByDocument.clear();
	}

	@Override
	public Stream<Info> catalogueHistory(Reference ref, ObjectConstraint filter) throws InvalidReference {
		final Predicate<Info> filterPredicate = filter == null ? info->true : info->filter.containsItem(MapValue.from(info.metadata));
		Map<Reference, DocumentImpl> history = store.subMap(new Reference(ref.id,newVersion("0")), true, ref, true);
		if (history.isEmpty()) throw new InvalidReference(ref);
		return history
			.entrySet()
			.stream()
			.map(entry -> info(entry))
			.filter(filterPredicate);
	}

	private <T> String updateWorkspaceByIndex(Function<T,WorkspaceImpl> index, T key, UUID id, QualifiedName name, State state, boolean createWorkspace) throws InvalidWorkspace {
		LOG.logEntering("updateWorkspaceByIndex", index, key, id, name, state, createWorkspace);
		if (key == null) throw LOG.logThrow("updateWorkspaceByIndex",new InvalidWorkspace("null"));
		WorkspaceImpl workspace = index.apply(key);
		if (workspace == null && createWorkspace) {
			if (id == null) id = UUID.randomUUID();
			if (name != null) {
				workspace = root.createWorkspace(id, name, state);
			} else {
				workspace = new WorkspaceImpl(null, id, null, state);
			}
			workspacesById.put(id, workspace);
		} else {
			if (workspace == null) throw LOG.logThrow("updateWorkspaceByIndex", new InvalidWorkspace(key.toString()));
			workspace.setState(state);
			if (name != null && !name.equals(workspace.getName())) {
				workspace.setName(root, name, createWorkspace);
			}
		}
		return LOG.logReturn("updateWorkspaceByIndex", id.toString());
	}
	
	public String updateWorkspaceById(String workspaceId, QualifiedName name, State state, boolean createWorkspace) throws InvalidWorkspace {
		LOG.logEntering("updateWorkspaceById", workspaceId, name, state, createWorkspace);
		if (workspaceId == null) throw LOG.logThrow("updateWorkspaceById",new InvalidWorkspace("null"));
		UUID id = UUID.fromString(workspaceId);
		return LOG.logReturn("updateWorkspaceById", updateWorkspaceByIndex(ws->workspacesById.get(ws), id, id, name, state, createWorkspace));
	}

	public String updateWorkspaceByName(QualifiedName name, QualifiedName newName, State state, boolean createWorkspace) throws InvalidWorkspace {
		LOG.logEntering("updateWorkspaceById", name, newName, state, createWorkspace);
		return LOG.logReturn("updateWorkspaceById",updateWorkspaceByIndex(ws->root.getWorkspace(ws), name, null, newName == null ? name : newName, state, createWorkspace));
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

		result.delete(docId);
		
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
	public String createWorkspace(QualifiedName name, State state) throws InvalidWorkspace {
		UUID id = UUID.randomUUID();
		return updateWorkspaceByIndex(ws->workspacesById.get(ws), id, id, name, state, true);
	}

}
