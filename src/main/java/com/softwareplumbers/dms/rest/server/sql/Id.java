/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.sql;

import com.softwareplumbers.dms.Constants;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.util.UUID;
import org.bouncycastle.util.Arrays;

/**
 *
 * @author jonathan
 */
public class Id {
    private final byte[] data;
    
    public static final Id ROOT_ID = new Id(new byte[] { 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0 });
    
    public Id(String id) {
        data = new byte[16];
        ByteBuffer bb = ByteBuffer.wrap(data);
        UUID uuid = UUID.fromString(id);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
    }
    
    public Id(byte[] data) {
        this.data = Arrays.clone(data);
    }
    
    public Id() {
        data = new byte[16];
        ByteBuffer bb = ByteBuffer.wrap(data);
        UUID uuid = UUID.randomUUID();
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());        
    }
        
    public String toString() { 
        ByteBuffer bb = ByteBuffer.wrap(data);
        long firstLong = bb.getLong();
        long secondLong = bb.getLong();
        return new UUID(firstLong, secondLong).toString();
    }
    
    public boolean equals(Id other) {
        return Arrays.areEqual(data, other.data);
    }
    
    public boolean equals(Object other) {
        return other instanceof Id && equals((Id)other);
    }
    
    public long hashValue() {
        return Arrays.hashCode(data);
    }
    
    byte[] getBytes()  {
        return data;
    }
    
    public static Id of(String string) {
        if (string == Constants.ROOT_ID) return ROOT_ID;
        else return new Id(string);
    }
    
    public static Id ofDocument(String string) {
        return string == null ? null : new Id(string);
    }
    
    public static Id ofVersion(String string) {
        return string == null ? null : new Id(string);
    }

}
