package com.softwareplumbers.dms.rest.server.model;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import com.softwareplumbers.common.QualifiedName;

/** Information about a Document or Workspace returned by search APIs.
 * 
 * This object has a standard JSON representation - @see {@link Info#toJson()}
 * 
 * @author jonathan.essex@softwareplumbers.com
 *
 */
public class Info {
	
	/** The qualified name of the resource */
	public final QualifiedName name;
	/** The resource type */
	public final RepositoryObject.Type type;
	/** A reference to a document */
	public final Reference reference;
	/** Metadata about the referenced document */
	public final JsonObject metadata;
	/** Mime type of the reference document */
	public final String mimeType;
	
	/** Create an Info object*/
	public Info(RepositoryObject.Type type, QualifiedName name, Reference reference, String mimeType, JsonObject metadata) {
		this.reference = reference;
		this.mimeType = mimeType;
		this.metadata = metadata;
		this.type = type;
		this.name = name;
	}
	
	/** Create an Info object from a workspace */
	public Info(Workspace workspace) {
		this.type = RepositoryObject.Type.WORKSPACE;
		this.name = workspace.getName();
		this.reference = null;
		this.mimeType = null;
		this.metadata = workspace.getMetadata();
	}
	
	/** Create an Info object from a document */
	public Info(QualifiedName name, Reference reference, Document document) {
		this.type = RepositoryObject.Type.DOCUMENT;
		this.name = name;
		this.reference = reference;
		this.mimeType = document.getMediaType().toString();
		this.metadata = document.getMetadata();
	}
	
	/** Get a reference to a document */
	public Reference getReference() { return reference; }
	/** Get the mime type of a document */
	public String getMimeType() { return mimeType; }
	/** Get other metadata about a document */
	public JsonObject getMetadata() { return metadata; }
	
	/** Create an Info object from Json */
	public static Info fromJson(JsonObject obj) {

		String name = obj.getString("name",null);
		JsonObject reference = obj.getJsonObject("reference");
		
		return new Info(
				RepositoryObject.Type.valueOf(obj.getString("type")),
				name == null ? null : QualifiedName.parse(name, "/"),
				reference == null ? null : Reference.fromJson(reference),
				obj.getString("mimeType",null),
				obj.getJsonObject("metadata")
			);
	}
	
	/** Output an Info object in Json format 
	 * 
	 * The object has the following field names: 
	 * 
	 * | Field name | Description |
	 * |------------|-------------|
	 * | type       | type, @see {@link RepositoryObject#Type} |
	 * | reference  | reference @see {@link Reference#toJson()} |
	 * | mimeType   | mime type in standard format |
	 * | name       | qualified name of document or folder joined by '/' @see {@link QualifiedName#join(String)} |
	 * | metadata   | a json object  |
	 * 
	 * @return a Json representation of this object. 
	 */
	public JsonObject toJson() {
		JsonObjectBuilder builder = Json.createObjectBuilder();		
		if (reference !=null) builder.add("reference", reference.toJson());
		if (mimeType != null) builder.add("mimeType", mimeType);
		if (name != null) builder.add("name", name.join("/"));
		builder.add("metadata", metadata);
		builder.add("type", type.toString());
		return builder.build();
	}
}
