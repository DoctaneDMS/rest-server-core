package com.softwareplumbers.dms.rest.server.model;

import static org.junit.Assert.assertEquals;

import javax.json.Json;
import javax.json.JsonObject;

import org.junit.Test;

public class TestMetadataMerge {

	
	@Test public void testMerge() {
		JsonObject a = Json.createObjectBuilder().add("a", "1").add("b", "2").build();
		JsonObject b = Json.createObjectBuilder().add("b", "3").build();
		JsonObject c = Json.createObjectBuilder().add("a", "1").add("b", "3").build();
		
		assertEquals(c, MetadataMerge.merge(a, b));
	}

	@Test public void testMergeDelete() {
		JsonObject a = Json.createObjectBuilder().add("a", "1").add("b", "2").build();
		JsonObject b = Json.createObjectBuilder().add("b", JsonObject.NULL).build();
		JsonObject c = Json.createObjectBuilder().add("a", "1").build();
		
		assertEquals(c, MetadataMerge.merge(a, b));
	}

}
