package app.doorman.web;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;

@Path("/")
public class LoginResource {

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance login(boolean error);

        static native TemplateInstance scannerHome();
    }

    @Inject
    SecurityIdentity identity;

    @ConfigProperty(name = "quarkus.http.auth.form.cookie-name", defaultValue = "quarkus-credential")
    String cookieName;

    /** Startseite je nach Rolle: Admins → Veranstaltungen, Scanner → Hinweis auf den Scan-Link. */
    @GET
    @Produces(MediaType.TEXT_HTML)
    public Object root() {
        if (identity.hasRole("admin")) {
            return Redirects.to("/events");
        }
        return Templates.scannerHome();
    }

    @GET
    @Path("login")
    @Produces(MediaType.TEXT_HTML)
    public Object login(@QueryParam("error") boolean error) {
        if (!identity.isAnonymous()) {
            return Redirects.to("/");
        }
        return Templates.login(error);
    }

    @GET
    @Path("logout")
    public Response logout() {
        NewCookie expired = new NewCookie.Builder(cookieName).path("/").maxAge(0).value("").build();
        return Response.seeOther(URI.create("/login")).cookie(expired).build();
    }
}
