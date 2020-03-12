/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.dms.rest.server.sql;

import com.google.common.collect.Streams;
import com.softwareplumbers.common.QualifiedName;
import java.io.Reader;
import java.io.Writer;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.sql.DataSource;

/**
 *
 * @author jonathan
 */
public abstract class FluentStatement {
    
    protected abstract String buildSQL() throws SQLException;
    protected abstract void buildStatement(PreparedStatement statement) throws SQLException;

    private static class Base extends FluentStatement {
        private final String sql;
        @Override
        protected String buildSQL() throws SQLException {
            return sql;
        }
        @Override
        protected void buildStatement(PreparedStatement statement) throws SQLException {            
        }
        public Base(String sql) { this.sql = sql; }
    }
    
    private static abstract class Param<T> extends FluentStatement {
        protected final FluentStatement base;
        protected final T value;
        protected final int index;
        @Override
        protected String buildSQL() throws SQLException {
            return base.buildSQL();
        }
        public Param(FluentStatement base, int index, T value) {
            this.base = base; this.index = index; this.value = value;
        }
    }
    
    private static class StringParam extends Param<String> {
        public StringParam(FluentStatement base, int index, String value) { super(base, index, value); }
        @Override
        protected void buildStatement(PreparedStatement statement) throws SQLException {
            base.buildStatement(statement);
            statement.setString(index, value);
        } 
    }
    
    private static class LongParam extends Param<Long> {
        public LongParam(FluentStatement base, int index, long value) { super(base, index, value); }
        @Override
        protected void buildStatement(PreparedStatement statement) throws SQLException {
            base.buildStatement(statement);
            statement.setLong(index, value);
        } 
    }
    
    private static class BooleanParam extends Param<Boolean> {
        public BooleanParam(FluentStatement base, int index, boolean value) { super(base, index, value); }
        @Override
        protected void buildStatement(PreparedStatement statement) throws SQLException {
            base.buildStatement(statement);
            statement.setBoolean(index, value);
        } 
    }
    
    private static class BinaryParam extends Param<byte[]> {
        public BinaryParam(FluentStatement base, int index, byte[] value) { super(base, index, value); }
        @Override
        protected void buildStatement(PreparedStatement statement) throws SQLException {
            base.buildStatement(statement);
            statement.setBytes(index, value);
        } 
    }

    private static class ClobParam extends Param<Consumer<Writer>> {
        public ClobParam(FluentStatement base, int index, Consumer<Writer> value) { super(base, index, value); }
        @Override
        protected void buildStatement(PreparedStatement statement) throws SQLException {
            base.buildStatement(statement);
            Clob clob = statement.getConnection().createClob();
            value.accept(clob.setCharacterStream(1));
            statement.setClob(index, clob);
        } 
    }

    public void execute(Connection con) throws SQLException {
        try (PreparedStatement statement = con.prepareStatement(buildSQL())) {
            buildStatement(statement);
            statement.execute();
        }
    }
    
    public <T> Stream<T> execute(Connection con, Mapper<T> mapper) throws SQLException {
        PreparedStatement statement = con.prepareStatement(buildSQL()); 
        buildStatement(statement);
        final ResultSetIterator<T> iterator = new ResultSetIterator(statement.executeQuery(), mapper);
        Stream<T> result = Streams.stream(iterator).onClose(()->{ 
            iterator.close();
            try { statement.close(); } catch (SQLException e) { }
        });
        return result;
    }
    
    public <T> Stream<T> execute(DataSource ds, Mapper<T> mapper) throws SQLException {
        Connection con = ds.getConnection();
        PreparedStatement statement = con.prepareStatement(buildSQL()); 
        buildStatement(statement);
        final ResultSetIterator<T> iterator = new ResultSetIterator(statement.executeQuery(), mapper);
        Stream<T> result = Streams.stream(iterator).onClose(()->{ 
            iterator.close();
            try { statement.close(); } catch (SQLException e) { }
            try { con.close(); } catch (SQLException e) { }
        });
        return result;
    }

    public static FluentStatement of(String sql) {
        return new Base(sql);
    }
    
    public FluentStatement set(int index, String value) { return new StringParam(this, index, value); }
    public FluentStatement set(int index, long value) { return new LongParam(this, index, value); }
    public FluentStatement set(int index, boolean value) { return new BooleanParam(this, index, value); }
    public FluentStatement set(int index, byte[] value) { return new BinaryParam(this, index, value); }
    public FluentStatement set(int index, Consumer<Writer> value) { return new ClobParam(this, index, value); }
    public FluentStatement set(int index, QualifiedName name) { return name.isEmpty() ? this : set(index+1, name.parent).set(index, name.part); }
    public FluentStatement set(int index, Id id) { return new BinaryParam(this, index, id.getBytes()); }
}
