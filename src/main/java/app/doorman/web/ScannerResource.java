package app.doorman.web;

import app.doorman.model.AppUser;
import app.doorman.model.Checkpoint;
import app.doorman.model.Event;
import app.doorman.model.Participant;
import app.doorman.service.ScanService;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

/**
 * Scan-Link einer Veranstaltung: /s/{code}. Wer den Link hat und als Scanner oder Admin angemeldet ist,
 * kann für diese Veranstaltung scannen. Nicht angemeldet → Login → zurück zum Link.
 */
@Path("/s/{code}")
@Produces(MediaType.TEXT_HTML)
@RolesAllowed({AppUser.ROLE_ADMIN, AppUser.ROLE_SCANNER})
public class ScannerResource {

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance choose(Event event, List<Checkpoint> checkpoints);

        static native TemplateInstance scanner(Event event, Checkpoint checkpoint, long checkedIn, long total,
                                               boolean multiple);
    }

    @Inject
    ScanService scanService;

    @GET
    public Object choose(@PathParam("code") String code) {
        Event event = load(code);
        List<Checkpoint> cps = Checkpoint.forEvent(event.id);
        if (cps.size() == 1) {
            return Redirects.to("/s/" + event.scanCode + "/" + cps.get(0).id);
        }
        return Templates.choose(event, cps);
    }

    @GET
    @Path("{cpId: \\d+}")
    public TemplateInstance scanner(@PathParam("code") String code, @PathParam("cpId") Long cpId) {
        Event event = load(code);
        Checkpoint cp = Checkpoint.findById(cpId);
        if (cp == null || !cp.event.id.equals(event.id)) {
            throw new NotFoundException();
        }
        return Templates.scanner(event, cp, scanService.checkedInCount(cp.id),
                Participant.count("event.id", event.id), Checkpoint.count("event.id", event.id) > 1);
    }

    /** Eigenes PWA-Manifest: "Zum Home-Bildschirm" öffnet direkt diesen Scan-Link. */
    @GET
    @Path("manifest.webmanifest")
    @Produces("application/manifest+json")
    public Map<String, Object> manifest(@PathParam("code") String code) {
        Event event = load(code);
        return Map.of(
                "name", "Doorman – " + event.name,
                "short_name", "Doorman",
                "start_url", "/s/" + event.scanCode,
                "scope", "/",
                "display", "standalone",
                "background_color", "#0f172a",
                "theme_color", "#0f172a",
                "lang", "de",
                "icons", List.of(
                        Map.of("src", "/icons/icon-192.png", "sizes", "192x192", "type", "image/png"),
                        Map.of("src", "/icons/icon-512.png", "sizes", "512x512", "type", "image/png")));
    }

    private static Event load(String code) {
        Event event = Event.findByScanCode(code);
        if (event == null) {
            throw new NotFoundException();
        }
        return event;
    }
}
