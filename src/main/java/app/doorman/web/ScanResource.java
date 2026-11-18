package app.doorman.web;

import app.doorman.model.AppUser;
import app.doorman.model.Checkpoint;
import app.doorman.model.Event;
import io.quarkus.panache.common.Sort;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.time.LocalDate;
import java.util.List;

/** Übersicht aller Veranstaltungen zum Scannen – nur für Admins. Scanner kommen über den Scan-Link. */
@Path("/scan")
@Produces(MediaType.TEXT_HTML)
@RolesAllowed(AppUser.ROLE_ADMIN)
public class ScanResource {

    public record Choice(Event event, List<Checkpoint> checkpoints) {
    }

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance select(List<Choice> upcoming, List<Choice> past);
    }

    @GET
    public TemplateInstance select() {
        LocalDate today = LocalDate.now(TemplateExtensions.ZONE);
        List<Event> events = Event.listAll(Sort.ascending("eventDate").and("id"));
        List<Choice> upcoming = events.stream()
                .filter(e -> e.eventDate == null || !e.eventDate.isBefore(today))
                .map(e -> new Choice(e, Checkpoint.forEvent(e.id))).toList();
        List<Choice> past = events.stream()
                .filter(e -> e.eventDate != null && e.eventDate.isBefore(today))
                .sorted((a, b) -> b.eventDate.compareTo(a.eventDate))
                .limit(5)
                .map(e -> new Choice(e, Checkpoint.forEvent(e.id))).toList();
        return Templates.select(upcoming, past);
    }
}
