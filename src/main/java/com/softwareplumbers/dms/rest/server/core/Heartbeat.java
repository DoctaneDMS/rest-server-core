package com.softwareplumbers.dms.rest.server.core;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.springframework.stereotype.Component;

@Component
@Path("/heartbeat")
public class Heartbeat {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String get() {
        return "OK!";
    }
}