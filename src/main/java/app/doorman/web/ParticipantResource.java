package app.doorman.web;

import app.doorman.model.Checkin;
import app.doorman.model.Checkpoint;
import app.doorman.model.Event;
import app.doorman.model.Participant;
import app.doorman.service.QrCodeService;
import app.doorman.service.TokenGenerator;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;

import java.time.Instant;
import java.util.Map;

@Path("/events/{eventId}/participants")
@Produces(MediaType.TEXT_HTML)
public class ParticipantResource {

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance edit(Event event, Participant participant);
    }

    @Inject
    QrCodeService qr;

    @Inject
    SecurityIdentity identity;

    @POST
    @Transactional
    public Response add(@PathParam("eventId") Long eventId, @RestForm String firstName,
                        @RestForm String lastName, @RestForm String email, @RestForm String note) {
        Event event = EventResource.load(eventId);
        String error = validate(firstName, lastName, email);
        if (error != null) {
            return Redirects.err("/events/" + eventId, error);
        }
        Participant p = new Participant();
        p.event = event;
        apply(p, firstName, lastName, email, note);
        p.token = TokenGenerator.newToken();
        p.persist();
        return Redirects.ok("/events/" + eventId + "#p" + p.id, p.getFullName() + " hinzugefügt.");
    }

    @GET
    @Path("{pid}/edit")
    public TemplateInstance editForm(@PathParam("eventId") Long eventId, @PathParam("pid") Long pid) {
        Participant p = load(eventId, pid);
        return Templates.edit(EventResource.load(eventId), p);
    }

    @POST
    @Path("{pid}/edit")
    @Transactional
    public Response edit(@PathParam("eventId") Long eventId, @PathParam("pid") Long pid,
                         @RestForm String firstName, @RestForm String lastName,
                         @RestForm String email, @RestForm String note) {
        Participant p = load(eventId, pid);
        String error = validate(firstName, lastName, email);
        if (error != null) {
            return Redirects.err("/events/" + eventId + "/participants/" + pid + "/edit", error);
        }
        apply(p, firstName, lastName, email, note);
        return Redirects.ok("/events/" + eventId, "Gespeichert.");
    }

    @POST
    @Path("{pid}/delete")
    @Transactional
    public Response delete(@PathParam("eventId") Long eventId, @PathParam("pid") Long pid) {
        Participant p = load(eventId, pid);
        String name = p.getFullName();
        p.delete();
        return Redirects.ok("/events/" + eventId, name + " gelöscht.");
    }

    /** Neuer QR-Code, z. B. wenn der alte weitergegeben wurde. Der alte wird ungültig. */
    @POST
    @Path("{pid}/new-token")
    @Transactional
    public Response newToken(@PathParam("eventId") Long eventId, @PathParam("pid") Long pid) {
        Participant p = load(eventId, pid);
        p.token = TokenGenerator.newToken();
        return Redirects.ok("/events/" + eventId + "/participants/" + pid + "/edit",
                "Neuer QR-Code erzeugt. Der alte Code ist ab sofort ungültig.");
    }

    /** Markiert den QR-Code als versendet bzw. nimmt die Markierung zurück (per JavaScript aufgerufen). */
    @POST
    @Path("{pid}/sent")
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Map<String, Boolean> sent(@PathParam("eventId") Long eventId, @PathParam("pid") Long pid,
                                     @RestForm boolean sent) {
        Participant p = load(eventId, pid);
        p.sentAt = sent ? Instant.now() : null;
        return Map.of("sent", sent);
    }

    /** Manueller Check-in bzw. Rücknahme durch Veranstalter. */
    @POST
    @Path("{pid}/checkin/{cpId}")
    @Transactional
    public Response toggleCheckin(@PathParam("eventId") Long eventId, @PathParam("pid") Long pid,
                                  @PathParam("cpId") Long cpId) {
        Participant p = load(eventId, pid);
        Checkpoint cp = Checkpoint.findById(cpId);
        if (cp == null || !cp.event.id.equals(eventId)) {
            throw new NotFoundException();
        }
        Checkin existing = Checkin.of(p.id, cp.id);
        String msg;
        if (existing != null) {
            existing.delete();
            msg = p.getFullName() + ": Check-in „" + cp.name + "“ zurückgenommen.";
        } else {
            Checkin c = new Checkin();
            c.participant = p;
            c.checkpoint = cp;
            c.checkedInBy = identity.getPrincipal().getName() + " (manuell)";
            c.persist();
            msg = p.getFullName() + ": manuell eingecheckt („" + cp.name + "“).";
        }
        return Redirects.ok("/events/" + eventId + "#p" + p.id, msg);
    }

    @GET
    @Path("{pid}/qr.png")
    @Produces("image/png")
    public Response qrPng(@PathParam("eventId") Long eventId, @PathParam("pid") Long pid,
                          @QueryParam("download") boolean download) {
        Participant p = load(eventId, pid);
        Response.ResponseBuilder rb = Response.ok(qr.png(p.token, 480))
                .header("Cache-Control", "private, no-store");
        if (download) {
            String name = EventResource.asciiFilename(("QR_" + p.getFullName()).replace(' ', '_'));
            rb.header("Content-Disposition", "attachment; filename=\"" + name + ".png\"");
        }
        return rb.build();
    }

    private static Participant load(Long eventId, Long pid) {
        Participant p = Participant.findById(pid);
        if (p == null || !p.event.id.equals(eventId)) {
            throw new NotFoundException();
        }
        return p;
    }

    private static String validate(String firstName, String lastName, String email) {
        if ((firstName == null || firstName.isBlank()) && (lastName == null || lastName.isBlank())) {
            return "Bitte Vor- oder Nachnamen angeben.";
        }
        if (email != null && !email.isBlank() && !email.contains("@")) {
            return "Die E-Mail-Adresse ist ungültig.";
        }
        return null;
    }

    private static void apply(Participant p, String firstName, String lastName, String email, String note) {
        p.firstName = EventResource.blankToNull(firstName);
        p.lastName = EventResource.blankToNull(lastName);
        p.email = EventResource.blankToNull(email);
        p.note = EventResource.blankToNull(note);
    }
}
