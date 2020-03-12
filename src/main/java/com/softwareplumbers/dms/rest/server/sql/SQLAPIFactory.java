/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.sql;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookup;
import org.springframework.beans.factory.annotation.Autowired;

/**
 *
 * @author jonathan
 */
public class SQLAPIFactory {
    
    private final Operations operations;
    private final Templates templates;
    private final DataSource datasource;
    
    public SQLAPIFactory(Operations operations, Templates templates, DataSource datasource) {
        this.operations = operations;
        this.templates = templates;
        this.datasource = datasource;
    }
    
    public SQLAPI getSQLAPI() throws SQLException {
        return new SQLAPI(operations, templates, datasource);
    }
    
}
