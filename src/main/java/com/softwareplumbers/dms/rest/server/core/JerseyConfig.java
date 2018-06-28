package com.softwareplumbers.dms.rest.server.core;

import org.glassfish.jersey.server.ResourceConfig;

import org.springframework.stereotype.Component;
import org.glassfish.jersey.media.multipart.MultiPartFeature;

@Component
public class JerseyConfig extends ResourceConfig {

	public JerseyConfig() {
		register(Documents.class);
		register(Catalogue.class);
		register(Heartbeat.class);
		register(CORSFilter.class);
	    register(MultiPartFeature.class);
	}

}