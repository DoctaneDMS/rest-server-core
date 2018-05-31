package com.softwareplumbers.dms.rest.server.core;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import org.springframework.stereotype.Component;

@Component
@Path("/docs")
public class Documents {

    @GET
    public String get() {
        return "Greetings from Spring Boot!";
    }
}