package com.softwareplumbers.dms.rest.server.tmp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.UUID;

import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;

import com.fasterxml.classmate.util.ResolvedTypeCache.Key;
import com.softwareplumbers.common.abstractquery.Cube;
import com.softwareplumbers.common.abstractquery.Value;
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
		
		private String name;
		private Map<Reference,WorkspaceInfo> docs;
		private State state;
		
		public WorkspaceImpl(String name, State state) {
			this.name = name;
			this.state = state;
			this.docs = new TreeMap<Reference, WorkspaceInfo>();
		}

		@Override
		public String getName() {
			return name;
		}
		
		@Override
		public State getState() {
			return state;
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
		
		public void add(Reference reference) {
			this.docs.put(reference, new WorkspaceInfo(false));
		}
		
		public void delete(String id) throws InvalidDocumentId, InvalidWorkspaceState {
			if (state == State.Open) {
				Reference ref = new Reference(id);
				WorkspaceInfo info = docs.get(ref);
				if (info == null) throw new InvalidDocumentId(id);
				info.deleted = true;
			} else {
				throw new InvalidWorkspaceState(name, state);
			}
		}

		public Stream<Info> catalogue(Cube filter, boolean searchHistory) {
			
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
	}
	
	private TreeMap<Reference,DocumentImpl> store = new TreeMap<Reference,DocumentImpl>();
	private TreeMap<String, WorkspaceImpl> workspaces = new TreeMap<String, WorkspaceImpl>();

	@Override
	public Document getDocument(Reference reference) throws InvalidReference {
		Document result = null;
		if (reference.version == null) {
			Map.Entry<Reference,DocumentImpl> previous = store.floorEntry(reference);

			if (previous != null && reference.id.equals(previous.getKey().id)) 
				result = previous.getValue();  
		} else {
			result = store.get(reference);
		}
		if (result == null) throw new InvalidReference(reference);
		return result;
	}

	/** Update a workspace with new or updated document
	 * 
	 * @param workspaceName
	 * @param ref
	 * @param createWorkspace
	 * @throws InvalidWorkspaceName
	 * @throws InvalidWorkspaceState 
	 */
	private void updateWorkspace(String workspaceName, Reference ref, boolean createWorkspace) throws InvalidWorkspaceName, InvalidWorkspaceState {
		if (workspaceName != null) {
			WorkspaceImpl workspace = workspaces.get(workspaceName);
			if (workspace == null && createWorkspace) {
				workspace = new WorkspaceImpl(workspaceName, State.Open);
				workspaces.put(workspaceName, workspace);
			}
			if (workspace == null) throw new InvalidWorkspaceName(workspaceName);
			if (workspace.getState() == State.Closed) throw new InvalidWorkspaceState(workspaceName, State.Closed);
			workspace.add(ref);
		}
	}
	
	@Override
	public Reference createDocument(MediaType mediaType, InputStreamSupplier stream, JsonObject metadata, String workspaceName, boolean createWorkspace) throws InvalidWorkspaceName, InvalidWorkspaceState {
		Reference new_reference = new Reference(UUID.randomUUID().toString(),0);
		try {
			DocumentImpl new_document = new DocumentImpl(mediaType, stream, metadata);
			updateWorkspace(workspaceName, new_reference, createWorkspace);
			store.put(new_reference, new_document);
			return new_reference;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public Reference updateDocument(String id, 
			MediaType mediaType, 
			InputStreamSupplier stream, 
			JsonObject metadata, 
			String workspace, 
			boolean createWorkspace) throws InvalidDocumentId, InvalidWorkspaceName, InvalidWorkspaceState {
		Map.Entry<Reference,DocumentImpl> previous = store.floorEntry(new Reference(id));
		if (previous != null && previous.getKey().id.equals(id)) {
			Reference new_reference = new Reference(id,previous.getKey().version+1);
			DocumentImpl newDocument = previous.getValue();
			try {
				if (metadata != null) newDocument = newDocument.setMetadata(MetadataMerge.merge(newDocument.getMetadata(), metadata));
				if (stream != null) newDocument = newDocument.setData(stream);			
				updateWorkspace(workspace, new_reference, createWorkspace);
				store.put(new_reference, newDocument);
				return new_reference;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		} else {
			throw new InvalidDocumentId(id);
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
	
	private Stream<Info> historicalInfo(Reference ref, Cube filter) {
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
	 * Need to figure out how to work with versions.
	 * 
	 */
	@Override
	public Stream<Info> catalogue(String workspaceName, Cube filter, boolean searchHistory) throws InvalidWorkspaceName {

		final Predicate<Info> filterPredicate = filter == null ? info->true : info->filter.containsItem(MapValue.from(info.metadata));

		if (workspaceName == null) {
			Stream<Info> infos = store.entrySet()
				.stream()
				.map(entry -> info(entry));
			if (searchHistory) {
				return latestVersionsOf(infos.filter(filterPredicate));
			} else {
				return latestVersionsOf(infos).filter(filterPredicate);
			}
		} else {
			WorkspaceImpl workspace = workspaces.get(workspaceName);
			if (workspace == null) throw new InvalidWorkspaceName(workspaceName);
			return workspace.catalogue(filter, searchHistory);
		}
	}


	
	public void clear() {
		store.clear();
		workspaces.clear();
	}

	@Override
	public Stream<Info> catalogueHistory(Reference ref, Cube filter) throws InvalidReference {
		final Predicate<Info> filterPredicate = filter == null ? info->true : info->filter.containsItem(MapValue.from(info.metadata));
		Map<Reference, DocumentImpl> history = store.subMap(new Reference(ref.id,0), true, ref, true);
		if (history.isEmpty()) throw new InvalidReference(ref);
		return history
			.entrySet()
			.stream()
			.map(entry -> info(entry))
			.filter(filterPredicate);
	}

	@Override
	public void updateWorkspace(String workspaceName, State state, boolean createWorkspace) throws InvalidWorkspaceName {
		WorkspaceImpl workspace = workspaces.get(workspaceName);
		if (workspace == null && createWorkspace) {
			workspace = new WorkspaceImpl(workspaceName, state);
			workspaces.put(workspaceName, workspace);
		} else {
			if (workspace == null) throw new InvalidWorkspaceName(workspaceName);
			workspace.setState(state);
		}
	}

	@Override
	public Workspace getWorkspace(String workspace) throws InvalidWorkspaceName {
		Workspace result = workspaces.get(workspace);
		if (result == null) throw new InvalidWorkspaceName(workspace);
		return result;
	}

	@Override
	public void deleteDocument(String workspace, String id) throws InvalidWorkspaceName, InvalidDocumentId, InvalidWorkspaceState {
		WorkspaceImpl result = workspaces.get(workspace);
		if (result == null) throw new InvalidWorkspaceName(workspace);
		result.delete(id);
	}

	@Override
	public Stream<Info> catalogueParts(Reference ref, Cube filter) throws InvalidReference {
		return Stream.empty();
	}

}
