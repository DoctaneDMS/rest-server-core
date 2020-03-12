/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.annotation.Resource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 *
 * @author jonathan
 */
public class Schema {
    
    private String updateScript;
    private String createScript;
    private String dropScript;
    
    public void setCreateScript(String script) { createScript=script; }
    public void setUpdateScript(String script) { updateScript=script; }
    public void setDropScript(String script) { dropScript=script; }

    @Autowired
    DataSource datasource;

    void dropSchema() throws SQLException {
        try (
            Connection con = datasource.getConnection();
            Statement stmt = con.createStatement()
        ) {
            stmt.execute(dropScript);
        }        
    }

    void createSchema() throws SQLException {
        try (
            Connection con = datasource.getConnection();
            Statement stmt = con.createStatement()
        ) {
            stmt.execute(createScript);
        }        
    }

    void updateSchema() throws SQLException {
        try (
            Connection con = datasource.getConnection();
            Statement stmt = con.createStatement()
        ) {
            stmt.execute(updateScript);
        }        
    }
    
}
