# Document Management REST Server Core

Core interfaces and support code for for the DMS rest server core.

## Architecture

Provides core interfaces and support code for a RESTFul DMS server written in Java. The DMS server is a stateless standalone java executable, implemeted using Spring Boot, Jersey, and Jax-RS. The actual back end is a pluggable spring module, for which several implementations are provided, including Apache Jackrabbit and IBM Filenet P8.

Build support is provided using gradle, and CI via Gitlab's native gitlab-ci.yml

## DMS Services

Doctane provides two basic groups of services - Document Services (under the <tenant>/docs path) and Workspace Service (under the <tenant>/ws path).

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

Doctane currently supports SAML2 and public key authentication protocols under the <authentication tenant>/auth path. Successful authentication to the Doctane server via either
of these protocols creates a JWT token with a limited lifespan, which can be used to secure further API calls for a tenant until the token expires.
Once the token expires, the API client must re-authenticate via SAML2 or another supported protocol.

Authentication tenants are non necessarily the same as API tenants; A call to single authentication tenant (e.g. softwareplumbers/auth/service) may be
providing authentication for several API tenants (softwareplumbers-qa/docs, softwareplumbers-prod/docs, etc).

### Public Key Authentication Protocol

A public key may be registered with the Doctane server (this is stored a Java Key Store on the server). A request to the <authentication tenant>/auth/service 
endpoint is used to pass a service authentication request signed with the related private key. The Doctane server will validate the 
signature of the this request with the stored public key, and return a JWT token if the signature is valid.

### SAML2 Authentication Protocol

SAML2 authentication is typically initiated interactively by the API client. The authentication request passes from the client to the
SAML2 IDP, which returns a response to the client which the client then POSTs to the <authentication-tenant>/auth/saml endpoint of the Doctane server. If the 
doctane server considers the SAML response to be valid, it will respond with a SEE OTHER containing the JWT token. The actual URI 
redirected to in the SEE OTHER is specified in the relay state parameter of the original authentication request.

## Authorization

Authorization to access a repository object (document or workspace) is a function of user metadata and an ACL. Doctane has an internal 
service module for each API tenant which will retrieve the ACL for a respository object; this ACL takes the form of a list of filters. 
A user is granted access to a document if any of the returned filters for the document returns 'true' when applied to the user's metadata.

The function which returns the ACL takes a repository object's metadata as an argument; the results will be cached for a predetermined period. 
The cached ACL is always refreshed if it does not grant access to a document for a user; thus, a change to document or folder metadata
which permits access to a data will always take effect immediately, wheres a change to document metadata which removes access will only 
take effect when the cache entry expires.

A User's metadata is a union of data returned by the IDP and data returned by the authorization service's getUserMetadata method. The
data returned by the IDP are stored in the authentication token and thus cached at the client for the duration of the user session, 
and the data from the authorization service is cached by the Doctane server for a predetermined period.

For search operations the process is slightly different. The authorization service getAccessConstraint method is passed both the user
metadata and the path to be searched; the filters returned are passed in to the API search operation to ensure that only accessible
repository objects are returned.



