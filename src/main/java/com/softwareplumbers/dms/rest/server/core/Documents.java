package com.softwareplumbers.dms.rest.server.core;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
public class Documents {

    @RequestMapping("/docs")
    public String index() {
        return "Greetings from Spring Boot!";
    }
}