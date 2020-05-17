package com.softwareplumbers.dms.rest.server.core;

import com.softwareplumbers.rest.server.core.Heartbeat;
import com.softwareplumbers.rest.server.core.CORSRequestFilter;
import com.softwareplumbers.rest.server.core.CORSResponseFilter;
import com.softwareplumbers.rest.server.core.Authentication;
import com.softwareplumbers.rest.server.core.AuthenticationFilter;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.springframework.stereotype.Component;

import org.glassfish.jersey.media.multipart.MultiPartFeature;

@Component
public class JerseyConfig extends ResourceConfig {

	public JerseyConfig() {
		property(ServerProperties.RESPONSE_SET_STATUS_OVER_SEND_ERROR, "true");
		register(Documents.class);
		register(Workspaces.class);
		register(Catalogue.class);
		register(Heartbeat.class);
		register(Authentication.class);
		register(CORSRequestFilter.class);
        register(CORSResponseFilter.class);
	    register(MultiPartFeature.class);
	    register(AuthenticationFilter.class);
	}

}