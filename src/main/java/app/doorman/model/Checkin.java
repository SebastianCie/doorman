package app.doorman.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
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

@Entity
@Table(name = "checkins")
public class Checkin extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id")
    public Participant participant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "checkpoint_id")
    public Checkpoint checkpoint;

    @Column(name = "checked_in_at")
    public Instant checkedInAt = Instant.now();

    @Column(name = "checked_in_by")
    public String checkedInBy;

    public static Checkin of(Long participantId, Long checkpointId) {
        return find("participant.id = ?1 and checkpoint.id = ?2", participantId, checkpointId).firstResult();
    }
}
