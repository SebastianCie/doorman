package app.doorman.web;

import app.doorman.model.AppUser;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;

/** Eigenes Passwort ändern. */
@Path("/account")
@Produces(MediaType.TEXT_HTML)
public class AccountResource {

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance account();
    }

    @Inject
    SecurityIdentity identity;

    @GET
    public TemplateInstance form() {
        return Templates.account();
    }

    @POST
    @Transactional
    public Response change(@RestForm String current, @RestForm String password, @RestForm String repeat) {
        AppUser u = AppUser.findByUsername(identity.getPrincipal().getName());
        if (u == null || current == null || !u.checkPassword(current)) {
            return Redirects.err("/account", "Das aktuelle Passwort ist falsch.");
        }
        if (password == null || password.length() < UserResource.MIN_PASSWORD) {
            return Redirects.err("/account",
                    "Das neue Passwort muss mindestens " + UserResource.MIN_PASSWORD + " Zeichen haben.");
        }
        if (!password.equals(repeat)) {
            return Redirects.err("/account", "Die beiden neuen Passwörter stimmen nicht überein.");
        }
        u.setPassword(password);
        return Redirects.ok("/account", "Passwort geändert.");
    }
}
