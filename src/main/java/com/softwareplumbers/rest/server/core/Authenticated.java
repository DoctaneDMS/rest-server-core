package com.softwareplumbers.rest.server.core;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.ws.rs.NameBinding;

/** Marker interface to indicate which endpoints are authenticated */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
public @interface Authenticated {

}
