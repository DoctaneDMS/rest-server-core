package com.softwareplumbers.dms.rest.server.sql;

import com.softwareplumbers.dms.RepositoryService;
import com.softwareplumbers.dms.common.test.TestModel;
import java.nio.file.Paths;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.Scope;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author jonathan
 */
@ImportResource("classpath:com/softwareplumbers/dms/rest/server/sql/h2db.xml")
public class LocalConfig {
    
    @Autowired private ApplicationContext applicationContext;
    
    @Bean public SQLRepositoryService service() {
        return new SQLRepositoryService(api(), Paths.get("/var/tmp/doctane/filestore"));
    }
    
    @Bean public SQLAPIFactory api() {
        return new SQLAPIFactory(
            applicationContext.getBean(Operations.class), 
            applicationContext.getBean(Templates.class), 
            applicationContext.getBean(Schema.class)
        );
    }
     
    @Bean public DataSource datasource() {
        DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();
        dataSourceBuilder.driverClassName("org.h2.Driver");
        dataSourceBuilder.url("jdbc:h2:file:/var/tmp/doctane/test");
        dataSourceBuilder.username("sa");
        dataSourceBuilder.password("");
        return dataSourceBuilder.build();        
    }
    
    @Bean public TestModel documentMetadataModel() {
        TestModel.Field uniqueField = new TestModel.IdField("DocFaceRef");
        TestModel model = new TestModel(
                new TestModel.StringField("TradeDescription", "BR001", "BR002", "BR003", "BR004"),
                new TestModel.BooleanField("BankDocument"),
                new TestModel.SessionIdField("BatchID"),
                uniqueField
        );
        model.setUniqueField(uniqueField);
        return model;
    }

    @Bean public TestModel workspaceMetadataModel() {
        return new TestModel(
                new TestModel.StringField("EventDescription", "Event01", "Event02", "Event03", "Event04"),
                new TestModel.StringField("Branch", "BR001", "BR002", "BR003", "BR004"),
                new TestModel.SessionIdField("TheirReference")
        );
    }    
}
