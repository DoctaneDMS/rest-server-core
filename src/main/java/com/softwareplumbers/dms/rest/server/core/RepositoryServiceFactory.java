package com.softwareplumbers.dms.rest.server.core;

import com.softwareplumbers.dms.rest.server.model.RepositoryService;

public interface RepositoryServiceFactory {
	RepositoryService getService(String name);
}