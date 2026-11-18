package app.doorman.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "participants")
public class Participant extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id")
    public Event event;

    @Column(name = "first_name")
    public String firstName;

    @Column(name = "last_name")
    public String lastName;

    public String email;

    public String note;

    /** Zufälliger Code, der im QR steht. */
    public String token;

    /** Zeitpunkt, an dem der QR-Code als "versendet" markiert wurde. */
    @Column(name = "sent_at")
    public Instant sentAt;

    @Column(name = "created_at")
    public Instant createdAt = Instant.now();

    public String getFullName() {
        String f = firstName == null ? "" : firstName.trim();
        String l = lastName == null ? "" : lastName.trim();
        return (f + " " + l).trim();
    }

    public static List<Participant> forEvent(Long eventId) {
        return list("event.id", Sort.by("lastName").and("firstName").and("id"), eventId);
    }
}
