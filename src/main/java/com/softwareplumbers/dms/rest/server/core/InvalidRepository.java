/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.core;

/**
 *
 * @author SWPNET\jonessex
 */
public class InvalidRepository extends Exception {
    
    public final String repository;
    
    public InvalidRepository(String repo) {
        super("Invalid repository " + repo);
        this.repository = repo;
    }
}
