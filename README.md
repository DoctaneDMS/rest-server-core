# Document Management REST Server Core

Core interfaces and support code for for the DMS rest server core.

## Architecture

Provides core interfaces and support code for a RESTFul DMS server written in Java. The DMS server is a stateless standalone java executable, implemeted using Spring Boot, Jersey, and Jax-RS. The actual back end is a pluggable spring module, for which several implementations are provided, including Apache Jackrabbit and IBM Filenet P8.

Build support is provided using gradle, and CI via Gitlab's native gitlab-ci.yml

## DMS Services

Doctane provides two basic groups of services - Document Services (under the /docs path) and Workspace Service (under the /ws path).

### Document Services 

Very simply, document services allow for anonymous storage of a document with associated metadata. A document is written using an http 
POST, which returns a document reference which is composed of a unique document id and a version code. The document can then be read with a GET 
using the same document id; an old version of a document can be read by specifying both the document id and the version code.

For detailed information concerning document service see the Documents class in the com.softwareplumbers.dms.rest.server.core package.

### Workspace Services

Workspaces may contain documents and other workspaces. Everything contained in a workspace must have a unique name. The same document 
may exist in several workspaces; it may have a different name in each. A workspace may be Open, Closed, or Finalized; this effects
which version of a document is retrieved by default.

  | State      | Effect |
  |------------|--------|
  | Open       | Documents may be freely added and removed; most recent version of a document is retrieved with GET |
  | Closed     | Any attempt to add or remove document will cause an error; GET will retrieve document version which was current when closed |
  | Finalized  | Documents may be added or removed;  GET will retrieve document version which was current when finalized |
 
For detailed information concerning workspace services see the Workspaces class in the com.softwareplumbers.dms.rest.server.core package.

## Authentication

Doctane currently supports SAML2 and public key authentication protocols under the /auth path. Successful authentication to the Doctane server via either
of these protocols creates a JWT token with a limited lifespan, which can be used to secure further API calls until the token expires.
Once the token expires, the API client must re-authenticate via SAML2 or another supported protocol.

### Public Key Authentication Protocol

A public key may be registered with the Doctane server (this is stored a Java Key Store on the server). A request to the /auth/service 
endpoint is used to pass a service authentication request signed with the related private key. The Doctane server will validate the 
signature of the this request with the stored public key, and return a JWT token if the signature is valid.

### SAML2 Authentication Protococl

SAML2 authentication is typically initiated interactively by the API client. The authentication request passes from the client to the
SAML2 IDP, which returns a response to the client which the client then POSTs to the /auth/saml endpoint of the Doctane server. If the 
doctane server considers the SAML response to be valid, it will respond with a SEE OTHER containing the JWT token. The actual URI 
redirected to in the SEE OTHER is specified in the relay state parameter of the original authentication request.


