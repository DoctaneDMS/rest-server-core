package com.softwareplumbers.dms.rest.server.core;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ImportResource;

@SpringBootApplication
@ComponentScan
@ImportResource("classpath:services.xml")
public class Application extends SpringBootServletInitializer {

	public static void main(String[] args) {
		new Application()
				.configure(new SpringApplicationBuilder(Application.class))
				.run(args);
	}

}