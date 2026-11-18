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

import java.util.List;

/** Ein Check-in-Punkt einer Veranstaltung, z. B. "Einlass", "Hinfahrt" oder "Rückfahrt". */
@Entity
@Table(name = "checkpoints")
public class Checkpoint extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id")
    public Event event;

    public String name;

    @Column(name = "sort_order")
    public int sortOrder;

    public static List<Checkpoint> forEvent(Long eventId) {
        return list("event.id", Sort.by("sortOrder").and("id"), eventId);
    }
}
