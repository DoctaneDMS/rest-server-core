# Document Management REST Server Core

Core interfaces and support code for for the DMS rest server core.

## Architecture

Provides core interfaces and support code for a RESTFul DMS server written in Java. The DMS server is a stateless standalone java executable, implemeted using Spring Boot, Jersey, and Jax-RS. The actual back end is a pluggable spring module, for which several implementations are provided, including Apache Jackrabbit and IBM Filenet P8.

The server also supports a separate pluggable authorization module that can be used to impose an external access control system on the underlying document store.

Build support is provided using gradle, and CI via Gitlab's native gitlab-ci.yml


