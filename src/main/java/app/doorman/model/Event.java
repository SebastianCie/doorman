package app.doorman.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "events")
public class Event extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String name;

    @Column(name = "event_date")
    public LocalDate eventDate;

    public String description;

    /** Geheimer Teil des Scan-Links /s/{scanCode}, den Scanner bekommen. */
    @Column(name = "scan_code")
    public String scanCode;

    public static Event findByScanCode(String code) {
        return code == null ? null : find("scanCode", code.toUpperCase()).firstResult();
    }

    @Column(name = "created_at")
    public Instant createdAt = Instant.now();
}
