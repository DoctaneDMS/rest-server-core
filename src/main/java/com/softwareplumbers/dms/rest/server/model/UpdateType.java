package com.softwareplumbers.dms.rest.server.model;

/** Controls behavior of an update operation.
 * 
 * Generally we want to avoid server round-trips, so testing to see whether something exists or not
 * before performing an operation is to be avoided. This enumeration controls whether an operation should behave
 * as a pure update, a pure create, or an update/create operation.
 * 
 * @author jonathan.local
 *
 */
public enum UpdateType {
	/** Create something, generate error if it exists already */
    CREATE,
	/** Update something, generate error if does not exists already */
    UPDATE, 
    /** Create or update, depending */
    CREATE_OR_UPDATE,
    /** Copy */
    COPY,
    /** Publish - create a new version of something which already exists */
    PUBLISH
}
