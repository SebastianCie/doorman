package app.doorman.web;

import app.doorman.model.Checkpoint;
import app.doorman.model.Event;
import app.doorman.model.Participant;
import app.doorman.service.ScanService;
import io.quarkus.panache.common.Sort;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.time.LocalDate;
import java.util.List;

/** Scanner-Oberfläche fürs Smartphone. */
@Path("/scan")
@Produces(MediaType.TEXT_HTML)
public class ScanResource {

    public record Choice(Event event, List<Checkpoint> checkpoints) {
    }

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance select(List<Choice> upcoming, List<Choice> past);

        static native TemplateInstance scanner(Event event, Checkpoint checkpoint, long checkedIn, long total);
    }

    @Inject
    ScanService scanService;

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

    @GET
    @Path("{cpId}")
    public TemplateInstance scanner(@PathParam("cpId") Long cpId) {
        Checkpoint cp = Checkpoint.findById(cpId);
        if (cp == null) {
            throw new NotFoundException();
        }
        Event event = Event.findById(cp.event.id);
        return Templates.scanner(event, cp, scanService.checkedInCount(cp.id), Participant.count("event.id", event.id));
    }
}
