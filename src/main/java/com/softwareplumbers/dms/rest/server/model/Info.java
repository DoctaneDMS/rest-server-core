package com.softwareplumbers.dms.rest.server.model;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import com.softwareplumbers.common.QualifiedName;

/** Information about a Document returned by search APIs.
 * 
 * @author SWPNET\jonessex
 *
 */
public class Info {
	
	/** The qualified name of the resource */
	public final QualifiedName name;
	/** The resource type */
	public final ResourceType type;
	/** A reference to a document */
	public final Reference reference;
	/** Metadata about the referenced document */
	public final JsonObject metadata;
	/** Mime type of the reference document */
	public final String mimeType;
	
	/** Create an Info object*/
	public Info(ResourceType type, QualifiedName name, Reference reference, String mimeType, JsonObject metadata) {
		this.reference = reference;
		this.mimeType = mimeType;
		this.metadata = metadata;
		this.type = type;
		this.name = name;
	}
	
	/** Create an Info object from a workspace */
	public Info(Workspace workspace) {
		this.type = ResourceType.FOLDER;
		this.name = workspace.getName();
		this.reference = null;
		this.mimeType = null;
		this.metadata = workspace.getMetadata();
	}
	
	/** Create an Info object from a document */
	public Info(QualifiedName name, Reference reference, Document document) {
		this.type = ResourceType.FILE;
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
				ResourceType.valueOf(obj.getString("type")),
				name == null ? null : QualifiedName.parse(name, "/"),
				reference == null ? Reference.fromJson(reference) : null,
				obj.getString("mimeType",null),
				obj.getJsonObject("metadata")
			);
	}
	
	/** Output an Info object in Json format */
	public JsonObject toJson() {
		JsonObjectBuilder builder = Json.createObjectBuilder();		
		if (reference !=null) builder.add("reference", reference.toJson());
		if (mimeType != null) builder.add("mimeType", mimeType);
		if (name != null) builder.add("name", name.toString());
		builder.add("metadata", metadata);
		builder.add("type", type.toString());
		return builder.build();
	}
}
