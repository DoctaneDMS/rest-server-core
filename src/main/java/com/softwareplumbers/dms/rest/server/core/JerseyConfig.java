package com.softwareplumbers.dms.rest.server.core;

import org.glassfish.jersey.server.ResourceConfig;

import org.springframework.stereotype.Component;

@Component
public class JerseyConfig extends ResourceConfig {

	public JerseyConfig() {
		register(Documents.class);
		register(Heartbeat.class);
	}

}