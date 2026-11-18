package app.doorman.web;

import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.http.HttpServerRequest;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/** In Templates verfügbar als {inject:ctx.…}: angemeldeter Benutzer und Hinweismeldungen. */
@Named("ctx")
@RequestScoped
public class RequestContext {

    @Inject
    SecurityIdentity identity;

    @Inject
    HttpServerRequest request;

    public String getUsername() {
        return identity.isAnonymous() ? null : identity.getPrincipal().getName();
    }

    public boolean isLoggedIn() {
        return !identity.isAnonymous();
    }

    public boolean isAdmin() {
        return identity.hasRole("admin");
    }

    public String getMsg() {
        return request.getParam("msg");
    }

    public String getErr() {
        return request.getParam("err");
    }
}
