package app.doorman.web;

import app.doorman.model.AppUser;
import io.quarkus.panache.common.Sort;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;

import java.util.List;

/** Benutzerverwaltung, nur für Admins. */
@Path("/users")
@Produces(MediaType.TEXT_HTML)
@RolesAllowed(AppUser.ROLE_ADMIN)
public class UserResource {

    static final int MIN_PASSWORD = 8;

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance users(List<AppUser> users);
    }

    @Inject
    SecurityIdentity identity;

    @GET
    public TemplateInstance list() {
        return Templates.users(AppUser.listAll(Sort.by("username")));
    }

    @POST
    @Transactional
    public Response create(@RestForm String username, @RestForm String password, @RestForm String role) {
        if (username == null || !username.trim().matches("[A-Za-z0-9._@-]{2,50}")) {
            return Redirects.err("/users", "Benutzername: 2–50 Zeichen, nur Buchstaben, Ziffern und . _ @ -");
        }
        if (AppUser.findByUsername(username.trim()) != null) {
            return Redirects.err("/users", "Diesen Benutzernamen gibt es schon.");
        }
        if (password == null || password.length() < MIN_PASSWORD) {
            return Redirects.err("/users", "Das Passwort muss mindestens " + MIN_PASSWORD + " Zeichen haben.");
        }
        AppUser u = new AppUser();
        u.username = username.trim();
        u.role = AppUser.ROLE_ADMIN.equals(role) ? AppUser.ROLE_ADMIN : AppUser.ROLE_USER;
        u.setPassword(password);
        u.persist();
        return Redirects.ok("/users", "Benutzer " + u.username + " angelegt.");
    }

    @POST
    @Path("{id}/password")
    @Transactional
    public Response resetPassword(@PathParam("id") Long id, @RestForm String password) {
        AppUser u = load(id);
        if (password == null || password.length() < MIN_PASSWORD) {
            return Redirects.err("/users", "Das Passwort muss mindestens " + MIN_PASSWORD + " Zeichen haben.");
        }
        u.setPassword(password);
        return Redirects.ok("/users", "Passwort für " + u.username + " geändert.");
    }

    @POST
    @Path("{id}/role")
    @Transactional
    public Response changeRole(@PathParam("id") Long id, @RestForm String role) {
        AppUser u = load(id);
        String newRole = AppUser.ROLE_ADMIN.equals(role) ? AppUser.ROLE_ADMIN : AppUser.ROLE_USER;
        if (u.isAdmin() && !newRole.equals(AppUser.ROLE_ADMIN) && AppUser.count("role", AppUser.ROLE_ADMIN) <= 1) {
            return Redirects.err("/users", "Es muss mindestens einen Admin geben.");
        }
        u.role = newRole;
        return Redirects.ok("/users", "Rolle von " + u.username + " geändert.");
    }

    @POST
    @Path("{id}/delete")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        AppUser u = load(id);
        if (u.username.equalsIgnoreCase(identity.getPrincipal().getName())) {
            return Redirects.err("/users", "Du kannst dich nicht selbst löschen.");
        }
        if (u.isAdmin() && AppUser.count("role", AppUser.ROLE_ADMIN) <= 1) {
            return Redirects.err("/users", "Es muss mindestens einen Admin geben.");
        }
        u.delete();
        return Redirects.ok("/users", "Benutzer " + u.username + " gelöscht.");
    }

    private static AppUser load(Long id) {
        AppUser u = AppUser.findById(id);
        if (u == null) {
            throw new NotFoundException();
        }
        return u;
    }
}
