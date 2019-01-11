package com.softwareplumbers.dms.rest.server.model;

import javax.json.JsonObject;
import javax.json.Json;

public interface Workspace {
	
	enum State { Open, Closed, Finalized }
	
	String getName();
	State getState();
	
	default JsonObject toJson() {
		return Json.createObjectBuilder()
			.add("name", getName())
			.add("state", getState().toString())
			.build();

	}
}
