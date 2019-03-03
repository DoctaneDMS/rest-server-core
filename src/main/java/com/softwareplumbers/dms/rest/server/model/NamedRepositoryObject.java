package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.common.QualifiedName;

/** Base interface for named repository objects such as workspaces and document links.
 * 
 * @author SWPNET\jonessex
 */
public interface NamedRepositoryObject extends RepositoryObject {

    /** Get the name of a repository object. 
     * 
     * Returns the name of the object relative to the root. In the case of an anonymous workspace,
     * this should return '~/<workspace id>'. This enables the children of an anonyomous workspace to
     * be consistently referenced via the APIs.
     */
    QualifiedName getName();
}
