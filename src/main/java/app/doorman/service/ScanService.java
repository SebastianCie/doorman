package app.doorman.service;

import app.doorman.model.Checkin;
import app.doorman.model.Checkpoint;
import app.doorman.model.Participant;
import io.quarkus.hibernate.orm.panache.Panache;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ApplicationScoped
public class ScanService {

    public enum Status { OK, ALREADY, NOT_FOUND, WRONG_EVENT }

    public record ScanResult(Status status, String name, String note, Instant time,
                             String otherEvent, long checkedIn, long total, boolean manual) {
    }

    /** Treffer der Suche "ohne QR-Code" auf der Scanner-Seite. */
    public record SearchHit(Long id, String name, String email, String note, Instant checkedInAt, boolean manual) {
    }

    /**
     * Prüft einen gescannten Code und checkt den Teilnehmer am Check-in-Punkt ein.
     * Das INSERT ... ON CONFLICT DO NOTHING ist atomar: scannen zwei Geräte
     * gleichzeitig denselben Code, gewinnt genau eines.
     */
    @Transactional
    public ScanResult scan(String rawToken, String eventCode, long checkpointId, String username) {
        Checkpoint cp = checkpoint(eventCode, checkpointId);
        String token = TokenGenerator.normalize(rawToken);

        Participant p = token.isEmpty() || token.length() > 64
                ? null
                : Participant.find("token", token).firstResult();
        if (p == null) {
            return result(Status.NOT_FOUND, null, null, cp);
        }
        if (!p.event.id.equals(cp.event.id)) {
            return new ScanResult(Status.WRONG_EVENT, p.getFullName(), null, null, p.event.name,
                    checkedInCount(cp.id), Participant.count("event.id", cp.event.id), false);
        }
        return checkIn(p, cp, username, Checkin.QR);
    }

    /**
     * Check-in ohne QR-Code: Der Scanner hat den Teilnehmer gesucht und die Personalien geprüft.
     * Wird als {@link Checkin#MANUAL} gespeichert.
     */
    @Transactional
    public ScanResult manual(long participantId, String eventCode, long checkpointId, String username) {
        Checkpoint cp = checkpoint(eventCode, checkpointId);
        Participant p = Participant.findById(participantId);
        if (p == null || !p.event.id.equals(cp.event.id)) {
            throw new NotFoundException();
        }
        return checkIn(p, cp, username, Checkin.MANUAL);
    }

    /** Sucht Teilnehmer der Veranstaltung nach Vorname, Nachname oder E-Mail (mind. 2 Zeichen). */
    public List<SearchHit> search(String query, String eventCode, long checkpointId) {
        Checkpoint cp = checkpoint(eventCode, checkpointId);
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.length() < 2) {
            return List.of();
        }
        // Jedes Wort muss in Vorname, Nachname oder E-Mail vorkommen ("max muster" findet Max Mustermann)
        StringBuilder where = new StringBuilder("event.id = :event");
        Map<String, Object> params = new HashMap<>();
        params.put("event", cp.event.id);
        String[] words = q.split("\\s+");
        for (int i = 0; i < Math.min(words.length, 4); i++) {
            String w = "w" + i;
            where.append(" and (lower(coalesce(firstName, '')) like :").append(w)
                    .append(" or lower(coalesce(lastName, '')) like :").append(w)
                    .append(" or lower(coalesce(email, '')) like :").append(w).append(")");
            params.put(w, "%" + words[i].replace("\\", "").replace("%", "").replace("_", "") + "%");
        }
        List<Participant> found = Participant.find(where.toString(), Sort.by("lastName").and("firstName"), params)
                .page(0, 25).list();
        Map<Long, Checkin> checkins = new HashMap<>();
        if (!found.isEmpty()) {
            Checkin.<Checkin>list("checkpoint.id = ?1 and participant.id in ?2", cp.id,
                            found.stream().map(p -> p.id).toList())
                    .forEach(c -> checkins.put(c.participant.id, c));
        }
        return found.stream().map(p -> {
            Checkin c = checkins.get(p.id);
            return new SearchHit(p.id, p.getFullName(), p.email, p.note,
                    c == null ? null : c.checkedInAt, c != null && c.isManual());
        }).toList();
    }

    private ScanResult checkIn(Participant p, Checkpoint cp, String username, String method) {
        int inserted = Panache.getEntityManager().createNativeQuery("""
                        insert into checkins (participant_id, checkpoint_id, checked_in_at, checked_in_by, method)
                        values (?1, ?2, now(), ?3, ?4)
                        on conflict (participant_id, checkpoint_id) do nothing""")
                .setParameter(1, p.id)
                .setParameter(2, cp.id)
                .setParameter(3, username)
                .setParameter(4, method)
                .executeUpdate();

        Checkin checkin = Checkin.of(p.id, cp.id);
        Status status = inserted == 1 ? Status.OK : Status.ALREADY;
        return new ScanResult(status, p.getFullName(), p.note, checkin == null ? null : checkin.checkedInAt, null,
                checkedInCount(cp.id), Participant.count("event.id", cp.event.id),
                checkin != null && checkin.isManual());
    }

    private ScanResult result(Status status, Participant p, String note, Checkpoint cp) {
        return new ScanResult(status, p == null ? null : p.getFullName(), note, null, null,
                checkedInCount(cp.id), Participant.count("event.id", cp.event.id), false);
    }

    /** Der Scan-Link-Code muss zur Veranstaltung passen, sonst könnte man andere Veranstaltungen durchprobieren. */
    private static Checkpoint checkpoint(String eventCode, long checkpointId) {
        Checkpoint cp = Checkpoint.findById(checkpointId);
        if (cp == null || eventCode == null || !cp.event.scanCode.equalsIgnoreCase(eventCode)) {
            throw new NotFoundException();
        }
        return cp;
    }

    public long checkedInCount(Long checkpointId) {
        return Checkin.count("checkpoint.id", checkpointId);
    }
}
