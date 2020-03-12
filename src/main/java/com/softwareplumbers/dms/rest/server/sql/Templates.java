/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.sql;

import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookup;

/**
 *
 * @author jonathan
 */
public class Templates {
    
    public String fetchDocumentLinkByPath;
    public String fetchDocumentLinkByPathAndId;
    public String joinParentNode;

    public void setJoinParentNode(String template) {
        joinParentNode = template;
    }

    public void setFetchDocumentLinkByPath(String template) {
        fetchDocumentLinkByPath = template;
    }

    public void setFetchDocumentLinkByPathAndId(String template) {
        fetchDocumentLinkByPathAndId = template;
    }

    public static String substitute(String template, Object... parameters) {
        StringLookup lookup = (key) -> {
            int ix = Integer.parseInt(key);
            return parameters[ix].toString();
        };
        StringSubstitutor substitutor = new StringSubstitutor(lookup);
        return substitutor.replace(template);
    }
    
}
