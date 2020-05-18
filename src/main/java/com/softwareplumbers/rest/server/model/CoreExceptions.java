/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.softwareplumbers.rest.server.model;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

/** Exception classes for Feed services.
 *
 * @author jonathan
 */
public class CoreExceptions {
    
    public static enum Type {
        INVALID_SERVICE,
        AUTHENTICATION_ERROR
    }
    
    /** Base exception.
     * 
     * All checked Feed exceptions will be a subclass.
     * 
     */
    public static class BaseException extends Exception {
        public final Type type;
        public BaseException(Type type, String reason) {
            super(reason);
            this.type = type;
        }
        public BaseException(Type type, Exception cause) {
            super(cause);
            this.type = type;
        }
        public static Optional<Type> getType(JsonObject obj) {
            try {
                return Optional.of(Type.valueOf(obj.getString("type")));
            } catch (Exception e) {
                return Optional.empty();
            }
        }
        public JsonObjectBuilder buildJson(JsonObjectBuilder bldr) {
            return bldr.add("type", type.toString());
        }
        public JsonObject toJson() {
            return buildJson(Json.createObjectBuilder()).build();
        }
    }

    /**
     *
     * @author SWPNET\jonessex
     */
    public static class InvalidService extends BaseException {

        public final String service;

        public InvalidService(String service) {
            super(Type.INVALID_SERVICE, "Invalid repository " + service);
            this.service = service;
        }
        
        public static Optional<Type> getService(JsonObject obj) {
            try {
                return Optional.of(Type.valueOf(obj.getString("service")));
            } catch (Exception e) {
                return Optional.empty();
            }
        }
        public JsonObjectBuilder buildJson(JsonObjectBuilder bldr) {
            return super.buildJson(bldr).add("service", service.toString());
        }        
    }

    /**
     *
     * @author SWPNET\jonessex
     */
    public static class AuthenticationError extends BaseException {

        public AuthenticationError(Exception cause) {
            super(Type.AUTHENTICATION_ERROR, "Authentication Error " + cause.getMessage());
        }
        
        public static Optional<Type> getCause(JsonObject obj) {
            try {
                return Optional.of(Type.valueOf(obj.getString("cause")));
            } catch (Exception e) {
                return Optional.empty();
            }
        }
        public JsonObjectBuilder buildJson(JsonObjectBuilder bldr) {
            return super.buildJson(bldr).add("cause", getCause().getMessage());
        }        
    }    
  
}
