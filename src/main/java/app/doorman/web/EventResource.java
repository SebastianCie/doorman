package app.doorman.web;

import app.doorman.model.Checkin;
import app.doorman.model.Checkpoint;
import app.doorman.model.Event;
import app.doorman.model.Participant;
import app.doorman.service.QrCodeService;
import app.doorman.service.TokenGenerator;
import io.quarkus.panache.common.Sort;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import app.doorman.model.AppUser;
import io.quarkus.qute.CheckedTemplate;
import jakarta.annotation.security.RolesAllowed;
import io.quarkus.qute.TemplateInstance;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.jboss.resteasy.reactive.RestForm;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/events")
@Produces(MediaType.TEXT_HTML)
@RolesAllowed(AppUser.ROLE_ADMIN)
public class EventResource {

    @Inject
    QrCodeService qr;

    public record Summary(Event event, long participants, String checkpointNames) {
    }

    public record Cell(Checkpoint checkpoint, Instant time) {
    }

    public record Row(Participant participant, List<Cell> cells) {
    }

    public record Stat(Checkpoint checkpoint, long count, long total) {
        public long percent() {
            return total == 0 ? 0 : Math.round(count * 100.0 / total);
        }
    }

    @CheckedTemplate
    static class Templates {
        static native TemplateInstance list(List<Summary> events);

        static native TemplateInstance create();

        static native TemplateInstance detail(Event event, List<Checkpoint> checkpoints, List<Row> rows,
                                              List<Stat> stats, String scanUrl, String whatsappUrl);

        static native TemplateInstance edit(Event event, List<Checkpoint> checkpoints);
    }

    @GET
    public TemplateInstance list() {
        List<Event> events = Event.listAll(Sort.descending("eventDate").and("id", Sort.Direction.Descending));
        Map<Long, Long> counts = new HashMap<>();
        Participant.getEntityManager()
                .createQuery("select p.event.id, count(p) from Participant p group by p.event.id", Object[].class)
                .getResultList()
                .forEach(r -> counts.put((Long) r[0], (Long) r[1]));
        List<Summary> result = events.stream().map(e -> new Summary(e, counts.getOrDefault(e.id, 0L),
                Checkpoint.forEvent(e.id).stream().map(c -> c.name).collect(Collectors.joining(", ")))).toList();
        return Templates.list(result);
    }

    @GET
    @Path("new")
    public TemplateInstance createForm() {
        return Templates.create();
    }

    @POST
    @Transactional
    public Response create(@RestForm String name, @RestForm String date, @RestForm String description,
                           @RestForm String checkpoints) {
        if (name == null || name.isBlank()) {
            return Redirects.err("/events/new", "Bitte einen Namen angeben.");
        }
        Event e = new Event();
        e.name = name.trim();
        e.eventDate = parseDate(date);
        e.description = blankToNull(description);
        e.scanCode = TokenGenerator.newToken();
        e.persist();

        List<String> names = checkpoints == null ? List.of() : checkpoints.lines()
                .map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        if (names.isEmpty()) {
            names = List.of("Einlass");
        }
        int order = 0;
        for (String n : names) {
            Checkpoint cp = new Checkpoint();
            cp.event = e;
            cp.name = n;
            cp.sortOrder = order++;
            cp.persist();
        }
        return Redirects.ok("/events/" + e.id, "Veranstaltung angelegt.");
    }

    @GET
    @Path("{id}")
    public TemplateInstance detail(@PathParam("id") Long id, @Context UriInfo uriInfo) {
        Event event = load(id);
        List<Checkpoint> cps = Checkpoint.forEvent(id);
        List<Participant> participants = Participant.forEvent(id);

        Map<String, Instant> times = new HashMap<>();
        Map<Long, Long> perCheckpoint = new HashMap<>();
        List<Checkin> checkins = Checkin.list("participant.event.id", id);
        for (Checkin c : checkins) {
            times.put(c.participant.id + ":" + c.checkpoint.id, c.checkedInAt);
            perCheckpoint.merge(c.checkpoint.id, 1L, Long::sum);
        }

        List<Row> rows = new ArrayList<>();
        for (Participant p : participants) {
            List<Cell> cells = cps.stream().map(cp -> new Cell(cp, times.get(p.id + ":" + cp.id))).toList();
            rows.add(new Row(p, cells));
        }
        List<Stat> stats = cps.stream()
                .map(cp -> new Stat(cp, perCheckpoint.getOrDefault(cp.id, 0L), participants.size())).toList();
        String scanUrl = scanUrl(event, uriInfo);
        String whatsappUrl = "https://wa.me/?text=" + URLEncoder.encode(
                "Scanner-Link für „" + event.name + "“: " + scanUrl, StandardCharsets.UTF_8).replace("+", "%20");
        return Templates.detail(event, cps, rows, stats, scanUrl, whatsappUrl);
    }

    /** Öffentliche Adresse des Scan-Links, z. B. https://doorman.example.com/s/K7MQ2XP9… */
    static String scanUrl(Event event, UriInfo uriInfo) {
        return uriInfo.getBaseUriBuilder().path("s").path(event.scanCode).build().toString();
    }

    /** QR-Code mit dem Scan-Link: Scanner öffnen ihn einfach mit der Handykamera. */
    @GET
    @Path("{id}/scan-link.png")
    @Produces("image/png")
    public Response scanLinkQr(@PathParam("id") Long id, @Context UriInfo uriInfo) {
        return Response.ok(qr.png(scanUrl(load(id), uriInfo), 480))
                .header("Cache-Control", "private, no-store").build();
    }

    /** Neuer Scan-Link; der bisherige funktioniert danach nicht mehr. */
    @POST
    @Path("{id}/scan-code")
    @Transactional
    public Response newScanCode(@PathParam("id") Long id) {
        load(id).scanCode = TokenGenerator.newToken();
        return Redirects.ok("/events/" + id, "Neuer Scan-Link erzeugt. Der alte Link funktioniert nicht mehr.");
    }

    @GET
    @Path("{id}/edit")
    public TemplateInstance editForm(@PathParam("id") Long id) {
        return Templates.edit(load(id), Checkpoint.forEvent(id));
    }

    @POST
    @Path("{id}/edit")
    @Transactional
    public Response edit(@PathParam("id") Long id, @RestForm String name, @RestForm String date,
                         @RestForm String description) {
        Event e = load(id);
        if (name == null || name.isBlank()) {
            return Redirects.err("/events/" + id + "/edit", "Bitte einen Namen angeben.");
        }
        e.name = name.trim();
        e.eventDate = parseDate(date);
        e.description = blankToNull(description);
        return Redirects.ok("/events/" + id, "Gespeichert.");
    }

    @POST
    @Path("{id}/delete")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        load(id).delete();
        return Redirects.ok("/events", "Veranstaltung und alle Teilnehmerdaten gelöscht.");
    }

    @POST
    @Path("{id}/checkpoints")
    @Transactional
    public Response addCheckpoint(@PathParam("id") Long id, @RestForm String name) {
        Event e = load(id);
        if (name == null || name.isBlank()) {
            return Redirects.err("/events/" + id + "/edit", "Bitte einen Namen für den Check-in-Punkt angeben.");
        }
        Checkpoint cp = new Checkpoint();
        cp.event = e;
        cp.name = name.trim();
        cp.sortOrder = Checkpoint.forEvent(id).stream().mapToInt(c -> c.sortOrder).max().orElse(-1) + 1;
        cp.persist();
        return Redirects.ok("/events/" + id + "/edit", "Check-in-Punkt hinzugefügt.");
    }

    @POST
    @Path("{id}/checkpoints/{cpId}")
    @Transactional
    public Response renameCheckpoint(@PathParam("id") Long id, @PathParam("cpId") Long cpId,
                                     @RestForm String name) {
        Checkpoint cp = loadCheckpoint(id, cpId);
        if (name != null && !name.isBlank()) {
            cp.name = name.trim();
        }
        return Redirects.ok("/events/" + id + "/edit", "Check-in-Punkt umbenannt.");
    }

    @POST
    @Path("{id}/checkpoints/{cpId}/delete")
    @Transactional
    public Response deleteCheckpoint(@PathParam("id") Long id, @PathParam("cpId") Long cpId) {
        Checkpoint cp = loadCheckpoint(id, cpId);
        if (Checkpoint.count("event.id", id) <= 1) {
            return Redirects.err("/events/" + id + "/edit", "Mindestens ein Check-in-Punkt muss bleiben.");
        }
        cp.delete();
        return Redirects.ok("/events/" + id + "/edit", "Check-in-Punkt gelöscht.");
    }

    /** Teilnehmerliste mit Check-in-Status, als CSV für Excel (Semikolon, UTF-8 mit BOM). */
    @GET
    @Path("{id}/export.csv")
    @Produces("text/csv")
    public Response export(@PathParam("id") Long id) throws IOException {
        Event event = load(id);
        List<Checkpoint> cps = Checkpoint.forEvent(id);
        Map<String, Instant> times = new HashMap<>();
        for (Checkin c : Checkin.<Checkin>list("participant.event.id", id)) {
            times.put(c.participant.id + ":" + c.checkpoint.id, c.checkedInAt);
        }
        StringWriter out = new StringWriter();
        out.write('﻿');
        try (CSVPrinter csv = new CSVPrinter(out, CSVFormat.DEFAULT.builder().setDelimiter(';').get())) {
            List<String> header = new ArrayList<>(List.of("Vorname", "Nachname", "E-Mail", "Bemerkung"));
            cps.forEach(cp -> header.add(cp.name));
            csv.printRecord(header);
            for (Participant p : Participant.forEvent(id)) {
                List<String> rec = new ArrayList<>(List.of(nz(p.firstName), nz(p.lastName), nz(p.email), nz(p.note)));
                for (Checkpoint cp : cps) {
                    rec.add(TemplateExtensions.dt(times.get(p.id + ":" + cp.id)));
                }
                csv.printRecord(rec);
            }
        }
        String filename = event.name.replaceAll("[^A-Za-z0-9äöüÄÖÜß _-]", "").trim().replace(' ', '_');
        return Response.ok(out.toString().getBytes(StandardCharsets.UTF_8))
                .type("text/csv; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"" + asciiFilename(filename) + ".csv\"")
                .build();
    }

    static Event load(Long id) {
        Event e = Event.findById(id);
        if (e == null) {
            throw new NotFoundException();
        }
        return e;
    }

    private static Checkpoint loadCheckpoint(Long eventId, Long cpId) {
        Checkpoint cp = Checkpoint.findById(cpId);
        if (cp == null || !cp.event.id.equals(eventId)) {
            throw new NotFoundException();
        }
        return cp;
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(s.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    static String asciiFilename(String s) {
        String r = s.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
                .replace("Ä", "Ae").replace("Ö", "Oe").replace("Ü", "Ue").replace("ß", "ss")
                .replaceAll("[^A-Za-z0-9_-]", "");
        return r.isEmpty() ? "export" : r;
    }
}
