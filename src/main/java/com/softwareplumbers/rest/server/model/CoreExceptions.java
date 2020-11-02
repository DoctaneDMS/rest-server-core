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
import javax.json.JsonValue;

/** Exception classes for Feed services.
 *
 * @author jonathan
 */
public class CoreExceptions {
    
    public static enum Type {
        INVALID_SERVICE,
        AUTHENTICATION_ERROR,
        AUTHORIZATION_ERROR
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
                return Optional.ofNullable(obj.getString("type")).map(Type::valueOf);
            } catch (Exception e) {
                return Optional.empty();
            }
        }
        
        public JsonObjectBuilder buildJson(JsonObjectBuilder bldr) {
            bldr.add("type", type.toString());
            bldr.add("error", getMessage());
            if (getCause() != null) bldr.add("cause", getCause().getMessage());
            return bldr;
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
        
        public static Optional<String> getService(JsonObject obj) {
            try {
                return Optional.ofNullable(obj.getString("service"));
            } catch (Exception e) {
                return Optional.empty();
            }
        }
        @Override
        public JsonObjectBuilder buildJson(JsonObjectBuilder bldr) {
            return super.buildJson(bldr).add("service", service);
        }        
    }

    /**
     *
     * @author SWPNET\jonessex
     */
    public static class AuthenticationError extends BaseException {

        public AuthenticationError(Exception cause) {
            super(Type.AUTHENTICATION_ERROR, cause);
        }
        
    } 
    
    /**
     *
     * @author SWPNET\jonessex
     */
    public static class AuthorizationError extends BaseException {
        
        JsonValue acl;
        String location;
        JsonObject metadata;

        public AuthorizationError(String error, JsonValue acl, String location, JsonObject metadata) {
            super(Type.AUTHORIZATION_ERROR, error);
            this.acl = acl;
            
        }
        
        public static Optional<JsonValue> getAcl(JsonObject obj) {
            try {
                return Optional.ofNullable(obj.get("acl"));
            } catch (Exception e) {
                return Optional.empty();
            }
        }
        
        public static Optional<JsonObject> getMetadata(JsonObject obj) {
            try {
                return Optional.ofNullable(obj.getJsonObject("metadata"));
            } catch (Exception e) {
                return Optional.empty();
            }
        }        
        
        public static Optional<String> getLocation(JsonObject obj) {
            try {
                return Optional.ofNullable(obj.getString("location"));
            } catch (Exception e) {
                return Optional.empty();
            }
        }

        public JsonObjectBuilder buildJson(JsonObjectBuilder bldr) {
            if (acl != null) bldr.add("acl", acl);
            if (metadata != null) bldr.add("metadata", metadata);
            if (location != null) bldr.add("location", location);
            return bldr;
        }        
    }     
  
}
