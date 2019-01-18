package com.softwareplumbers.dms.rest.server.util;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.tomcat.util.http.fileupload.util.Streams;

public class Log {
	
	public final Logger log;
	public final String className;
	
	public Log(Class<?> clazz) {
		className = clazz.getName();
		log = Logger.getLogger(className);	
	}
	
	public final <T> T logReturn(String method, T result) {
		log.log(Level.FINER, "Exiting: " + method + " with " + result);
		return result;
	}
	
	public final <T extends Throwable> T logThrow(String method, T r) {
		log.log(Level.WARNING, method + " Throwing: " + r);
		return r;
	}

	public final <T extends Throwable> T logRethrow(String method, T r) {
		log.log(Level.FINER, method + " Rethrowing: " + r);
		return r;
	}
	
	public final void logEntering(String method, Object ...args) {
		if (log.isLoggable(Level.FINER)) {
			String argList = Stream.of(args).map(arg -> arg == null ? "null" : arg.toString()).collect(Collectors.joining(","));
			log.log(Level.FINER, "Entering: " + method + "(" + argList + ")");
		}
	}

	public final void logExiting(String method) {
		log.log(Level.FINER, "Exiting: " + method);
	}

}
