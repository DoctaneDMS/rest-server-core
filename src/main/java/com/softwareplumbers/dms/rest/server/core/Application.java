package com.softwareplumbers.dms.rest.server.core;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ImportResource;

/** Spring Application Class and Servlet Initializer. 
 */
@SpringBootApplication
@ComponentScan({"com.softwareplumbers.dms.rest", "org.keycloak.adapters"})
@ImportResource("classpath:services.xml")
public class Application extends SpringBootServletInitializer {

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
		return configureApplication(builder);
	}

	public static void main(String[] args) {
		configureApplication(new SpringApplicationBuilder()).run(args);
	}

	private static SpringApplicationBuilder configureApplication(SpringApplicationBuilder builder) {
		return builder.sources(Application.class); //.bannerMode(Banner.Mode.OFF);
	}

}