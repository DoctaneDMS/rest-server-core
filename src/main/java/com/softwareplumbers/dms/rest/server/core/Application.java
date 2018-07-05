package com.softwareplumbers.dms.rest.server.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ImportResource;

@SpringBootApplication
@ComponentScan({"com.softwareplumbers.dms.rest", "org.keycloak.adapters"})
@ImportResource("classpath:services.xml")
public class Application  {
	   
	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}