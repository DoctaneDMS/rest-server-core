/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.rest.server.core;

import com.softwareplumbers.rest.server.model.CoreExceptions;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

/**
 *
 * @author jonathan
 */
@Provider
public class CoreExceptionMapper implements ExceptionMapper<CoreExceptions.BaseException> {

    private static final XLogger LOG = XLoggerFactory.getXLogger(CoreExceptionMapper.class);

    @Override
    public Response toResponse(CoreExceptions.BaseException error) {
        LOG.entry(error);
        Response.ResponseBuilder builder = Response.status(Response.Status.INTERNAL_SERVER_ERROR);
        switch (error.type) {
            case INVALID_SERVICE:
                builder = Response.status(Response.Status.NOT_FOUND);
                break;
            case AUTHENTICATION_ERROR:
                builder = Response.status(Response.Status.INTERNAL_SERVER_ERROR);
                break;
            case AUTHORIZATION_ERROR:
                builder = Response.status(Response.Status.FORBIDDEN);
                break;
        }
        builder.entity(error.toJson());
        return LOG.exit(builder.build());        
    }    
}
