/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import net.shibboleth.utilities.java.support.resource.Resource;

/** Stupid class.
 * 
 * Rarely have I been more annoyed to have to write any piece of code than this one.
 * 
 * But honestly, I know this is doing more or less what 
 * net.shibboleth.ext. spring.resource.ResourceHelper does but I don't think it does it for
 * the version of Spring I actually use. Using that package would drive me straight into 
 * dependency hell. Thus, this incredibly stupid piece of code. Another reason why uber-frameworks
 * like Spring are basically a bad idea.
 *
 * @author SWPNET\jonessex
 */
public class IdioticShibbolethSpringResourceBridge implements Resource {

    private final org.springframework.core.io.Resource springResource;
    
    public IdioticShibbolethSpringResourceBridge(org.springframework.core.io.Resource springResource) {
        this.springResource = springResource;
    }
    
    @Override
    public boolean exists() {
        return springResource.exists();
    }

    @Override
    public boolean isReadable() {
        return springResource.isReadable();
    }

    @Override
    public boolean isOpen() {
        return springResource.isOpen();
    }

    @Override
    public URL getURL() throws IOException {
        return springResource.getURL();
    }

    @Override
    public URI getURI() throws IOException {
        return springResource.getURI();
    }

    @Override
    public File getFile() throws IOException {
        return springResource.getFile();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return springResource.getInputStream();
    }

    @Override
    public long contentLength() throws IOException {
        return springResource.contentLength();
    }

    @Override
    public long lastModified() throws IOException {
        return springResource.lastModified();
    }

    @Override
    public Resource createRelativeResource(String string) throws IOException {
        return new IdioticShibbolethSpringResourceBridge(springResource.createRelative(string));
    }

    @Override
    public String getFilename() {
        return springResource.getFilename();
    }

    @Override
    public String getDescription() {
        return springResource.getDescription();
    }
    
    
    
}
