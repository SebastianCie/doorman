package app.doorman.service;

import app.doorman.model.Checkin;
import app.doorman.model.Checkpoint;
import app.doorman.model.Participant;
import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.time.Instant;

@ApplicationScoped
public class ScanService {

    public enum Status { OK, ALREADY, NOT_FOUND, WRONG_EVENT }

    public record ScanResult(Status status, String name, String note, Instant time,
                             String otherEvent, long checkedIn, long total) {
    }

    /**
     * Prüft einen gescannten Code und checkt den Teilnehmer am Check-in-Punkt ein.
     * Das INSERT ... ON CONFLICT DO NOTHING ist atomar: scannen zwei Geräte
     * gleichzeitig denselben Code, gewinnt genau eines.
     */
    @Transactional
    public ScanResult scan(String rawToken, long checkpointId, String username) {
        Checkpoint cp = Checkpoint.findById(checkpointId);
        if (cp == null) {
            throw new NotFoundException();
        }
        Long eventId = cp.event.id;
        String token = TokenGenerator.normalize(rawToken);

        Participant p = token.isEmpty() || token.length() > 64
                ? null
                : Participant.find("token", token).firstResult();
        if (p == null) {
            return result(Status.NOT_FOUND, null, null, null, null, cp);
        }
        if (!p.event.id.equals(eventId)) {
            return result(Status.WRONG_EVENT, p, null, null, p.event.name, cp);
        }

        int inserted = Panache.getEntityManager().createNativeQuery("""
                        insert into checkins (participant_id, checkpoint_id, checked_in_at, checked_in_by)
                        values (?1, ?2, now(), ?3)
                        on conflict (participant_id, checkpoint_id) do nothing""")
                .setParameter(1, p.id)
                .setParameter(2, cp.id)
                .setParameter(3, username)
                .executeUpdate();

        Checkin checkin = Checkin.of(p.id, cp.id);
        Status status = inserted == 1 ? Status.OK : Status.ALREADY;
        return result(status, p, p.note, checkin == null ? null : checkin.checkedInAt, null, cp);
    }

    private ScanResult result(Status status, Participant p, String note, Instant time, String otherEvent,
                              Checkpoint cp) {
        return new ScanResult(status, p == null ? null : p.getFullName(), note, time, otherEvent,
                checkedInCount(cp.id), Participant.count("event.id", cp.event.id));
    }

    public long checkedInCount(Long checkpointId) {
        return Checkin.count("checkpoint.id", checkpointId);
    }
}
