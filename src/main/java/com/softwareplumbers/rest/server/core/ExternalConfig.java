/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.rest.server.core;

import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;

/**
 *
 * @author jonat
 */
@Configuration
@ConditionalOnResource(resources = "${spring.config.location}/services.xml")
@ImportResource("${spring.config.location}/services.xml")
public class ExternalConfig {
    
}
