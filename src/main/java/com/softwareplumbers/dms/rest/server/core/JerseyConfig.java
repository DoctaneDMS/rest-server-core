package com.softwareplumbers.dms.rest.server.core;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.springframework.stereotype.Component;

import com.softwareplumbers.dms.rest.server.tmp.TempAdmin;

import org.glassfish.jersey.media.multipart.MultiPartFeature;

@Component
public class JerseyConfig extends ResourceConfig {

	public JerseyConfig() {
		property(ServerProperties.RESPONSE_SET_STATUS_OVER_SEND_ERROR, "true");
		register(TempAdmin.class);
		register(Documents.class);
		register(Workspaces.class);
		register(Catalogue.class);
		register(Heartbeat.class);
		register(CORSFilter.class);
	    register(MultiPartFeature.class);
	}

}