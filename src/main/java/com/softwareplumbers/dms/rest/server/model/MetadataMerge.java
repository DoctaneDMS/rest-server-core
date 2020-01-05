package com.softwareplumbers.dms.rest.server.model;

import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.json.JsonObjectBuilder;
import static com.softwareplumbers.dms.Constants.*;

/** Define how we merge metadata.
 * 
 * Users may sent partial metadata updates. Leaving an attribute out of the update will result in the value being unchanged.
 * Values may be deleted by being set to JsonObject.NULL
 * 
 * @author localadmin
 *
 */
public class MetadataMerge {
		
	public static JsonObject merge(JsonObject original, JsonObject update) {
		if (original == null && update == null) return EMPTY_METADATA;
		if (original == null) return update;
		if (update == null) return original;
		Map<String, JsonValue> map = new HashMap<String, JsonValue>(original);
		map.putAll(update);
		JsonObjectBuilder result = Json.createObjectBuilder();
		for (Map.Entry<String, JsonValue> entry : map.entrySet()) if (entry.getValue() != JsonObject.NULL) result.add(entry.getKey(), entry.getValue());
		return result.build();		
	}
}
