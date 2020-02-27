/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.tmp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import com.softwareplumbers.dms.common.test.TestModel;
import com.softwareplumbers.dms.common.test.TestModel.StringField;
import com.softwareplumbers.dms.common.test.TestModel.BooleanField;
import com.softwareplumbers.dms.common.test.TestModel.Field;

/**
 *
 * @author jonathan
 */
@Configuration
public class TmpConfig {
    
    @Bean TestModel documentMetadataModel() {
        Field uniqueField = new TestModel.IdField("idfield");
        TestModel model = new TestModel(
                new StringField("TradeDescription", "BR001", "BR002", "BR003", "BR004"),
                new StringField("DocFaceRef", "Ref01", "Ref02", "Ref03", "Ref04"),
                new BooleanField("BankDocument"),
                uniqueField
        );
        model.setUniqueField(uniqueField);
        return model;
    }

    @Bean TestModel workspaceMetadataModel() {
        return new TestModel(
                new StringField("EventDescription", "Event01", "Event02", "Event03", "Event04"),
                new StringField("Branch", "BR001", "BR002", "BR003", "BR004")
        );
    }
}
