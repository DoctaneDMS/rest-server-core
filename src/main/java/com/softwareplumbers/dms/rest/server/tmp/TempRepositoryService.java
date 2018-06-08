package com.softwareplumbers.dms.rest.server.tmp;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.UUID;

import javax.json.JsonObject;
import javax.ws.rs.core.MediaType;

import com.softwareplumbers.common.abstractquery.Query;
import com.softwareplumbers.common.abstractquery.Value;
import com.softwareplumbers.dms.rest.server.model.Document;
import com.softwareplumbers.dms.rest.server.model.Document.Reference;
import com.softwareplumbers.dms.rest.server.model.RepositoryService;

public class TempRepositoryService implements RepositoryService {
	
	private TreeMap<Reference,Document> store = new TreeMap<Reference,Document>();

	@Override
	public Document getDocument(Reference reference) {
		if (reference.version == null) {
			Map.Entry<Reference,Document> previous = store.floorEntry(reference);
			return reference.id.equals(previous.getKey().id) ? previous.getValue() : null;  
		} else {
			return store.get(reference);
		}
	}

	@Override
	public Reference createDocument(MediaType mediaType, Supplier<InputStream> stream, JsonObject metadata) {
		Reference new_reference = new Reference(UUID.randomUUID().toString(),0);
		try {
			Document new_document = new Document.Default(mediaType, stream, metadata);
			store.put(new_reference, new_document);
			return new_reference;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public List<Reference> catalogue(Query filter) {
		return store.entrySet()
			.stream()
			.filter(entry -> filter.containsItem(Value.from(entry.getValue().getMetadata())))
			.map(entry -> entry.getKey())
			.collect(Collectors.toList());
	}

}
