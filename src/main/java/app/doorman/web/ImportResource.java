package app.doorman.web;

import app.doorman.model.Event;
import app.doorman.model.Participant;
import app.doorman.service.CsvImportService;
import app.doorman.service.TokenGenerator;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import java.util.stream.Collectors;

@Path("/events/{eventId}/import")
@Produces(MediaType.TEXT_HTML)
public class ImportResource {

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance upload(Event event);

        static native TemplateInstance preview(Event event, CsvImportService.Result result);
    }

    @GET
    public TemplateInstance form(@PathParam("eventId") Long eventId) {
        return Templates.upload(EventResource.load(eventId));
    }

    @POST
    @Path("preview")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Object preview(@PathParam("eventId") Long eventId, @RestForm("file") FileUpload file)
            throws IOException {
        Event event = EventResource.load(eventId);
        if (file == null || file.size() == 0) {
            return Redirects.err("/events/" + eventId + "/import", "Bitte eine CSV-Datei auswählen.");
        }
        if (file.size() > 2_000_000) {
            return Redirects.err("/events/" + eventId + "/import", "Die Datei ist zu groß (max. 2 MB).");
        }
        CsvImportService.DecodedText decoded = CsvImportService.decode(Files.readAllBytes(file.uploadedFile()));
        return Templates.preview(event, CsvImportService.parse(decoded.text(), decoded.encoding(),
                existingKeys(eventId)));
    }

    @POST
    @Path("confirm")
    @Transactional
    public Response confirm(@PathParam("eventId") Long eventId, @RestForm String text, @RestForm String encoding) {
        Event event = EventResource.load(eventId);
        CsvImportService.Result result = CsvImportService.parse(text, encoding, existingKeys(eventId));
        int count = 0;
        for (CsvImportService.Row row : result.rows()) {
            if (!row.isNew()) {
                continue;
            }
            Participant p = new Participant();
            p.event = event;
            p.firstName = row.firstName();
            p.lastName = row.lastName();
            p.email = row.email();
            p.note = row.note();
            p.token = TokenGenerator.newToken();
            p.persist();
            count++;
        }
        return Redirects.ok("/events/" + eventId, count + " Teilnehmer importiert.");
    }

    private static Set<String> existingKeys(Long eventId) {
        return Participant.<Participant>list("event.id", eventId).stream()
                .map(p -> CsvImportService.key(p.firstName, p.lastName, p.email))
                .collect(Collectors.toSet());
    }
}
