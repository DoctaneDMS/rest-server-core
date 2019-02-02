package com.softwareplumbers.dms.rest.server.model;

import java.io.IOException;
import java.io.InputStream;

/** A functional interface for something that returns a stream. 
 * 
 * Because such operations typically throw an IOException, we can't use a regular Suppler.
 * 
 * @author SWPNET\jonessex
 *
 */
@FunctionalInterface
public interface InputStreamSupplier {
	/** Get a stream */
	InputStream get() throws IOException;
}
