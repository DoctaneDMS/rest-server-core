package com.softwareplumbers.dms.rest.server.tmp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.UUID;

import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;

import com.fasterxml.classmate.util.ResolvedTypeCache.Key;
import com.softwareplumbers.common.abstractquery.Query;
import com.softwareplumbers.common.abstractquery.Value;
import com.softwareplumbers.dms.rest.server.model.Document;
import com.softwareplumbers.dms.rest.server.model.InputStreamSupplier;
import com.softwareplumbers.dms.rest.server.model.Reference;
import com.softwareplumbers.dms.rest.server.model.RepositoryService;

public class TempRepositoryService implements RepositoryService {
	
	private TreeMap<Reference,Document.Default> store = new TreeMap<Reference,Document.Default>();

	@Override
	public Document getDocument(Reference reference) {
		if (reference.version == null) {
			Map.Entry<Reference,Document.Default> previous = store.floorEntry(reference);

			return previous != null && reference.id.equals(previous.getKey().id) ? previous.getValue() : null;  
		} else {
			return store.get(reference);
		}
	}

	@Override
	public Reference createDocument(MediaType mediaType, InputStreamSupplier stream, JsonObject metadata) {
		Reference new_reference = new Reference(UUID.randomUUID().toString(),0);
		try {
			Document.Default new_document = new Document.Default(mediaType, stream, metadata);
			store.put(new_reference, new_document);
			return new_reference;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/** Catalog a repository.
	 * 
	 * Need to figure out how to work with versions.
	 * 
	 */
	@Override
	public List<Reference> catalogue(Query filter) {

		Comparator<Reference> COMPARE_REFS = Comparator.naturalOrder();

		// This is hideous. Better to do it manually!
		Stream<Reference> result = store.entrySet()
			.stream()
			.filter(entry -> filter.containsItem(Value.from(entry.getValue().getMetadata())))
			.map(entry -> entry.getKey())
			.collect(Collectors.groupingBy(Reference::getId, 
					Collectors.collectingAndThen(Collectors.maxBy(COMPARE_REFS), Optional::get)))
			.values()
			.stream();
		
		return result.collect(Collectors.toList());		
	}

	@Override
	public Reference updateDocument(String id, MediaType mediaType, InputStreamSupplier stream, JsonObject metadata) {
		Map.Entry<Reference,Document.Default> previous = store.floorEntry(new Reference(id));
		if (previous != null && previous.getKey().id.equals(id)) {
			Reference new_reference = new Reference(id,previous.getKey().version+1);
			Document.Default newDocument = previous.getValue();
			try {
				if (metadata != null) newDocument = newDocument.setMetadata(metadata);
				if (stream != null) newDocument = newDocument.setData(stream);			
				store.put(new_reference, newDocument);
				return new_reference;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		} else {
			return null;
		}
	}

}
