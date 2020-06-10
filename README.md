# Doctane REST Server Core

Core interfaces and support code for Doctane rest servers. This module provides authentication services which
are used to protect endpoints defined in other modules. For modules using these authentication services, see:

* rest-server-dms [here](https://projects.softwareplumbers.com/document-management/rest-server-dms)
* rest-server-feeds [here](https://projects.softwareplumbers.com/document-management/rest-server-feeds)
* rest-server-authz (planned)

## Architecture

Provides core interfaces and support code for a RESTFul server written in Java. The server is a standalone java executable, implemeted using Spring Boot, Jersey, and Jax-RS. 

Build support is provided using gradle, and CI via Gitlab's native gitlab-ci.yml

## Authentication Services

REST Server Core currently supports SAML2 and public key authentication protocols under the 
<authentication tenant>/auth path. Successful authentication to the Doctane server via either
of these protocols creates a JWT token with a limited lifespan, which can be used to secure 
further API calls for a tenant until the token expires. Once the token expires, the API client 
must re-authenticate via SAML2 or another supported protocol.

Authentication tenants are non necessarily the same as API tenants; A call to single authentication
tenant (e.g. softwareplumbers/auth/service) may be providing authentication for several API tenants
(softwareplumbers-qa/docs, softwareplumbers-prod/docs, etc).

### Public Key Authentication Protocol

A public key may be registered with the Doctane server (this is stored a Java Key Store on the server). A request to the <authentication tenant>/auth/service 
endpoint is used to pass a service authentication request signed with the related private key. The Doctane server will validate the 
signature of the this request with the stored public key, and return a JWT token if the signature is valid.

### SAML2 Authentication Protocol

SAML2 authentication is typically initiated interactively by the API client. The authentication request passes from the client to the
SAML2 IDP, which returns a response to the client which the client then POSTs to the <authentication-tenant>/auth/saml endpoint of the Doctane server. If the 
doctane server considers the SAML response to be valid, it will respond with a SEE OTHER containing the JWT token. The actual URI 
redirected to in the SEE OTHER is specified in the relay state parameter of the original authentication request.


### Configuration

The main configuration file for any Doctane server is a file services.xml. This file contains several spring bean definitions, 
as described below (or in the README for the specific service module)

## Key Manager

The 'key manager' object manages the cryptographic data the server needs to connect securely with clients.

```xml   
<bean id ="keymgr" class="com.softwareplumbers.keymanager.KeyManager" scope="singleton">
	<constructor-arg index="0" value="/var/tmp/doctane-proxy.keystore"/>
	<constructor-arg index="1" value="password"/>
	<constructor-arg index="2"><value>com.softwareplumbers.dms.rest.server.core.SystemSecretKeys</value></constructor-arg>
	<constructor-arg index="3"><value>com.softwareplumbers.dms.rest.server.core.SystemKeyPairs</value></constructor-arg>
</bean>
```

* Argument 0 specifies the location of a JECKS key store on the server filesystem. If this key store does not exist it will be created.
* Argument 1 specified the keystore password.
* Arguments 2 and 3 provide identifiers that can be used to retrieve keys. You should not need to change these values.


## Authentication Components

### Authentication Service Map

The authentication service map maps a set of authentication services to each repository. Each set of authentication services
must be defined as a spring bean and implement the AuthenticationService interface. The configuration below maps the tenant 
'test' to a set of dummy authentication services implemented by the spring bean 'auth.dummy'.

```xml    
    <!-- Map repositories to authentication services -->
    <bean id="AuthenticationServiceFactory"
            class="org.springframework.beans.factory.config.ServiceLocatorFactoryBean">
    	<property name="serviceLocatorInterface" value="com.softwareplumbers.dms.rest.server.core.AuthenticationServiceFactory"/>
        <property name="serviceMappings">
            <props>
                <prop key="test">auth.dummy</prop>
            </props>
        </property>
    </bean>
```

### Core Authentication Beans

```xml
    <bean id="signedRequestValidationService" class="com.softwareplumbers.dms.rest.server.model.SignedRequestValidationService" scope="singleton">
        <constructor-arg index="0" ref="keymgr"/>
    </bean>
```

This bean connects the signed request validation service to a key manager. The signed request
validation service handles requests to authenticate to a repository using a request signed by
a private key. The public key used to validate the request must be connected to the repository
for which authentication is requested. 

```xml 
    <bean id="dummyValidation" class="com.softwareplumbers.dms.rest.server.core.DummyRequestValidationService" scope="singleton">
        <constructor-arg index="0" value="test"/>
    </bean>
```        

The bean above configures a dummy request validator for the repository 'test'. A dummy
validator allow anyone to access the repository without authentication.

```xml   
    <bean id="auth.dummy" class="com.softwareplumbers.dms.rest.server.model.AuthenticationService" scope="singleton">
        <property name="RequestValidationService" ref="dummyValidation"/>
        <property name="SignonService">
            <bean class="com.softwareplumbers.dms.rest.server.core.DummySignonService">
                <constructor-arg index="0" ref="dummyValidation"/>
            </bean>
        </property>
        <property name="SignedRequestValidationService" ref="signedRequestValidationService"/>
    </bean>
```
The bean above configures a complete set of authentication services. A dummy validator
is specified, so anyone may connect. A dummy sign-on service is also specified, so requests
to /auth/<repo>/signon will not invoke any external signon-service but simply redirect
back to the caller page (as specified in the relayState query parameter). The signed request
validation service is enabled, so services will still be able to authenticate using a
signed request.

The next bean specifies a SAML authentication service. It does not contain much information
as the SAML service is separately configured in the file idp-metadata.xml. Future development
will provide support for multiple SAML and OIDC identity providers.

```xml    
    <!-- SAML Protocol Handler -->
    <bean id="softwarePlumbersSAMLServer" class="com.softwareplumbers.dms.rest.server.model.SAMLProtocolHandlerService" scope="singleton"/>
```

The next bean specifies another complete set of authentication services. The request validation
service 'CookieRequestValidationService' ensures that all API services are validated with a
JWT token. A JWT token is provided on successful authentication using either the SignedRequestValidationService
on /auth/<repo>/service or the SAMLResponseHandlerService on /auth/<repo>/saml. A call to 
/auth/repo/signon will redirect the caller to the specified SAML identity provider.

```xml
    <!-- Authentication configuration usign SAML -->   
    <bean id="auth.test" class="com.softwareplumbers.dms.rest.server.model.AuthenticationService" scope="singleton">
        <property name="RequestValidationService">
            <bean class="com.softwareplumbers.dms.rest.server.core.CookieRequestValidationService">
                <constructor-arg index="0" ref="keymgr"/>
                <constructor-arg index="1" value="test"/>
            </bean>
        </property>
        <property name="SignedRequestValidationService" ref="signedRequestValidationService"/>
        <property name="SignonService">
            <bean class="com.softwareplumbers.dms.rest.server.core.SAMLSignonService">
                <constructor-arg index="0" ref="softwarePlumbersSAMLServer"/>
                <constructor-arg index="1" value="http://localhost:8080/auth/test/saml"/>
            </bean>
        </property>
        <property name="SAMLResponseHandlerService" ref="softwarePlumbersSAMLServer"/>
    </bean>
```

