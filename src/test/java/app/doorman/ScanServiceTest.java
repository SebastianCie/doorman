package app.doorman;

import app.doorman.model.Checkpoint;
import app.doorman.model.Event;
import app.doorman.model.Participant;
import app.doorman.service.ScanService;
import app.doorman.service.ScanService.Status;
import app.doorman.service.TokenGenerator;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class ScanServiceTest {

    @Inject
    ScanService scanService;

    record Fixture(Long hin, Long rueck, Long otherCp, String token) {
    }

    @Transactional
    Fixture setup() {
        Event bus = new Event();
        bus.name = "Busreise";
        bus.persist();
        Checkpoint hin = cp(bus, "Hinfahrt", 0);
        Checkpoint rueck = cp(bus, "Rückfahrt", 1);

        Event other = new Event();
        other.name = "Andere Reise";
        other.persist();
        Checkpoint otherCp = cp(other, "Einlass", 0);

        Participant p = new Participant();
        p.event = bus;
        p.firstName = "Max";
        p.lastName = "Mustermann";
        p.token = TokenGenerator.newToken();
        p.persist();
        return new Fixture(hin.id, rueck.id, otherCp.id, p.token);
    }

    private static Checkpoint cp(Event e, String name, int order) {
        Checkpoint cp = new Checkpoint();
        cp.event = e;
        cp.name = name;
        cp.sortOrder = order;
        cp.persist();
        return cp;
    }

    @Test
    void scanFlow() {
        Fixture f = setup();

        var first = scanService.scan(f.token(), f.hin(), "tester");
        assertEquals(Status.OK, first.status());
        assertEquals("Max Mustermann", first.name());
        assertEquals(1, first.checkedIn());
        assertEquals(1, first.total());

        var again = scanService.scan(f.token().toLowerCase(), f.hin(), "tester");
        assertEquals(Status.ALREADY, again.status());
        assertNotNull(again.time());

        // Rückfahrt ist ein eigener Check-in-Punkt
        assertEquals(Status.OK, scanService.scan(f.token(), f.rueck(), "tester").status());

        assertEquals(Status.NOT_FOUND, scanService.scan("GIBTESNICHT", f.hin(), "tester").status());
        assertEquals(Status.NOT_FOUND, scanService.scan("", f.hin(), "tester").status());

        var wrong = scanService.scan(f.token(), f.otherCp(), "tester");
        assertEquals(Status.WRONG_EVENT, wrong.status());
        assertEquals("Busreise", wrong.otherEvent());
    }

    @Test
    void tokenFormat() {
        String t = TokenGenerator.newToken();
        assertEquals(26, t.length());
        assertEquals(t, TokenGenerator.normalize(" " + t.toLowerCase() + "\n"));
    }
}
