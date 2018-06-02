package com.softwareplumbers.dms.rest.server.model;

import javax.json.JsonObject;
import java.io.OutputStream;


/**  Represents a DMS repository
 */
interface Repository {
	public Document getDocument(String id, String version);
	public DocumentReference addDocument(Document document);
	public List<DocumentReference> catalogue(Filter filter);
};
