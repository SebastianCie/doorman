package app.doorman.web;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Benutzerhandbuch in der App ("?"-Knopf). Öffentlich erreichbar, damit es auch vor der Anmeldung hilft.
 * Admins sehen alle Kapitel, Scanner und nicht Angemeldete nur die für sie relevanten.
 */
@Path("/help")
@Produces(MediaType.TEXT_HTML)
public class HelpResource {

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance help();
    }

    @GET
    public TemplateInstance help() {
        return Templates.help();
    }
}
