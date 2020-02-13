/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.model;

import com.softwareplumbers.dms.Document;
import com.softwareplumbers.dms.DocumentPart;

/**
 *
 * @author jonathan
 */
public interface PartHandler {

    DocumentPart build(Document document);   
    boolean canHandle(Document document);
}
