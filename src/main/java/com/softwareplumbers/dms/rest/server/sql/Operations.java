/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.sql;

/**
 *
 * @author jonathan
 */
public class Operations {
    
    public String fetchLinkByName;
    public String fetchLinkById;
    public String fetchInfoByName;
    public String fetchFolderByName;
    public String fetchLatestDocument;
    public String fetchDocument;
    public String fetchChildren;
    public String createDocument;
    public String createNode;
    public String createFolder;
    public String createLink;
    public String deleteObject;
    public String deleteDocumentById;
    public String fetchPathToId;
    public String fetchLastNameLike;
    public String fetchChildByName;
    public String updateLink;
    public String updateFolder;
    public String copyFolder;
    public String copyLink;

    public void setFetchLinkByName(String sql) {
        fetchLinkByName = sql;
    }

    public void setFetchLinkById(String sql) {
        fetchLinkById = sql;
    }

    public void setFetchFolderByName(String sql) {
        fetchFolderByName = sql;
    }

    public void setFetchLatestDocument(String sql) {
        fetchLatestDocument = sql;
    }

    public void setFetchDocument(String sql) {
        fetchDocument = sql;
    }

    public void setCreateDocument(String sql) {
        createDocument = sql;
    }

    public void setCreateFolder(String sql) {
        createFolder = sql;
    }

    public void setCreateLink(String sql) {
        createLink = sql;
    }

    public void setCreateNode(String sql) {
        createNode = sql;
    }

    public void setUpdateLink(String sql) {
        updateLink = sql;
    }

    public void setUpdateFolder(String sql) {
        updateFolder = sql;
    }

    public void setFetchPathToId(String sql) {
        fetchPathToId = sql;
    }

    public void setFetchLastNameLike(String sql) {
        fetchLastNameLike = sql;
    }

    public void setFetchInfoByName(String sql) {
        fetchInfoByName = sql;
    }
    
    public void setFetchChildren(String sql) {
        fetchChildren = sql;
    }
    
    public void setCopyFolder(String sql) {
        copyFolder = sql;
    }

    public void setCopyLink(String sql) {
        copyLink = sql;
    }
    
    public void setDeleteObject(String sql) {
        deleteObject = sql;
    }

    public void setDeleteDocumentById(String sql) {
        deleteDocumentById = sql;
    }    
    
}
