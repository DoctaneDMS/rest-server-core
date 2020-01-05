package com.softwareplumbers.dms.rest.server.core;

import com.softwareplumbers.dms.RepositoryService;

/** Sevice locator interface for Repositories.
 * 
 * @author Jonathan Essex
 *
 */
public interface RepositoryServiceFactory {
	
	/** Get a repository by name
	 * 
	 *  @param name Name of repository.
	 *  @return A repository service
	 */
	RepositoryService getService(String name);
}