/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.core;

import com.softwareplumbers.common.QualifiedName;
import com.softwareplumbers.common.abstractpattern.parsers.Parsers;

import com.softwareplumbers.dms.rest.server.model.Constants;
import com.softwareplumbers.dms.rest.server.util.Log;

/** Class representing workspace path.
 * 
 * Objects are addressed in a workspace via be a path typically composed of several elements separated by '/'. 
 * An element beginning with '~' is assumed to be an id. 
 * 
 * If the first element of a path is an id, it is assumed to be the id of a workspace. Thus, knowing the id 
 * of a workspace, we can use a path like ~AF2s6sv34/subworkspace/document.txt to find a document in that
 * workspace without knowing the full path to the parent workspace. 
 * 
 * An id *after* the first element in the path is assumed to be a document id. Thus, we can use a path like:
 * /abc/xyz/~Asdf32HT to retrieve the version of a document that is current in the given workspace, and a path
 * like abc/ * /~ASd3343 to list all the sub-workspace of 'abc' in which the given document reference is present.
 *
 * An path elements appearing after a document id are assumed to be part identifiers; documents such as zip
 * files may be composed of many sub-documents which can be separately accessed via the workspace
 * API. The '~' character may also be used by itself to indicate that the following elements in the path actually
 * reference such sub-documents. Thus /abc/~ASD23_4sf/myfile.doc references a document 'myfile.doc' in the compound
 * document with id ASD23_4sf which is in folder abc. /abc/zipfile.zip/~/myfile.doc references a named document inside
 * the file named 'myfile.zip' inside the same folder.
 * 
 * @author Jonathan Essex
 */
public class WorkspacePath {
    
    private static final Log LOG = new Log(WorkspacePath.class);
    
    public final String rootId;
    public final QualifiedName staticPath;
    public final QualifiedName queryPath;
    public final String documentId;
    public final QualifiedName staticPartPath;
    public final QualifiedName queryPartPath;

    public WorkspacePath(String rootId, QualifiedName staticPath, QualifiedName queryPath, String documentId, QualifiedName staticPartPath, QualifiedName queryPartPath) {
        this.rootId = rootId;
        this.staticPath = staticPath;
        this.queryPath = queryPath;
        this.documentId = documentId;
        this.staticPartPath = staticPartPath;
        this.queryPartPath = queryPartPath;
    }

    /** Detect whether a path element is an id.
     *
     * An element is an id if it is at least two characters long, starts with a ~, and the tilde is
     * not doubled (~~ is an escaped ~)
     *
     * @param element
     * @return true if element is an id
     */
    public static boolean isId(String element) {
        if (element == null) return false;
        return element.length() > 1 && element.startsWith("~") && !element.startsWith("~~");
    }

    /** Detect whether a path element is a query element
     * @param element
     * @return true if element is a query element
     */
    public static boolean isQueryElement(String element) {
        if (element == null) return false;
        return !Parsers.parseUnixWildcard(element).isSimple();
    }

    public static boolean isPartDelimeter(String element) {
        if (element == null) return false;
        return element.equals("~");
    }

    public static WorkspacePath valueOf(String path) {
        String rootId = Constants.ROOT_ID;
        QualifiedName staticPath = QualifiedName.ROOT;
        QualifiedName staticPartPath = QualifiedName.ROOT;
        QualifiedName queryPath = QualifiedName.ROOT;
        QualifiedName queryPartPath = QualifiedName.ROOT;
        String documentId = null;
        boolean seenPartDelimeter = false;
        try {
            for (String pathElement : path.split("/")) {
                if (pathElement.length() == 0) continue;
                if (isPartDelimeter(pathElement)) {
                    seenPartDelimeter = true;
                    continue;
                } 
                if (seenPartDelimeter || documentId != null) {
                    if (!queryPartPath.isEmpty() || isQueryElement(pathElement))
                        queryPartPath = queryPartPath.add(pathElement);
                    else
                        staticPartPath = staticPartPath.add(pathElement);
                    continue;
                } 
                if (isId(pathElement)) {
                    String id = pathElement.substring(1);
                    if (staticPath.isEmpty() && queryPath.isEmpty() && rootId == Constants.ROOT_ID) {
                        rootId = id;
                    } else {
                        documentId = id;
                    }
                    continue;
                } 
                if (!queryPath.isEmpty() || isQueryElement(pathElement)) {
                    queryPath = queryPath.add(pathElement);
                    continue;
                } 

                staticPath = staticPath.add(pathElement);
            }
        } catch (RuntimeException e) {
            LOG.logWarning("valueOf", "Could not parse workspace path");    
        }
        return new WorkspacePath(rootId, staticPath, queryPath, documentId, staticPartPath, queryPartPath);
    }
    
    public String toString() {
        StringBuilder result = new StringBuilder();
        if (rootId != null) result.append("/").append(rootId);
        if (staticPath != QualifiedName.ROOT) result.append("/").append(staticPath.join("/"));
        if (queryPath != QualifiedName.ROOT) result.append("/").append(queryPath.join("/"));
        if (documentId != null) result.append("/").append(documentId);
        if (staticPartPath != QualifiedName.ROOT) result.append("/").append(staticPartPath.join("/"));
        if (queryPartPath != QualifiedName.ROOT) result.append("/").append(queryPartPath.join("/"));
        return result.toString();
    }
    
    public boolean isEmpty() {
        return Constants.ROOT_ID == rootId
            && staticPath == QualifiedName.ROOT
            && staticPartPath == QualifiedName.ROOT
            && queryPath == QualifiedName.ROOT
            && queryPartPath == QualifiedName.ROOT;
    }
}
