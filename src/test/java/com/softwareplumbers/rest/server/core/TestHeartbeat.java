package com.softwareplumbers.rest.server.core;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.jsonp.JsonProcessingFeature;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author jonathan
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, classes = Application.class)
@EnableConfigurationProperties
public class TestHeartbeat {

	int port = 8080;    
    
    Client client = ClientBuilder.newClient(new ClientConfig()
    		.register(MultiPartFeature.class)
            .register(JsonProcessingFeature.class));
    
    /** Test that the server responds on its heartbeat URL.
     * 
     */    
	@Test
	public void heartbeatTest() {
	   	WebTarget target = client.target("http://localhost:" + port + "/heartbeat");

    	Response response = target
    			.request(MediaType.TEXT_PLAIN)
    			.get();
		assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
	}    
}
