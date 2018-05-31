package com.softwareplumbers.dms.rest.server.core;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import org.springframework.stereotype.Component;

@Component
@Path("/heartbeat")
public class Heartbeat {

    @GET
    public String get() {
        return "OK!";
    }
}