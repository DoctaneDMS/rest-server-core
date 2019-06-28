/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.core;

import java.util.List;
import javax.ws.rs.core.MediaType;

/** Error thrown when none of the media types preferred by an endpoint have been requested.
 *
 * @author jonathan.local
 */
public class InvalidContentType extends Exception {
    
    /** Constructor
     * 
     * @param requested Requested media types
     * @param preferred List of media types preferred by the endpoint
     */
    InvalidContentType(List<MediaType> requested, List<MediaType> preferred) {
        super(String.format("None of the preferred media types %s are compatable with the requested media types %s", preferred, requested));
    }
}
