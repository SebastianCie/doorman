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
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class ScanServiceTest {

    @Inject
    ScanService scanService;

    record Fixture(Long hin, Long rueck, Long otherCp, String token, String code, String otherCode) {
    }

    @Transactional
    Fixture setup() {
        Event bus = new Event();
        bus.name = "Busreise";
        bus.scanCode = TokenGenerator.newToken();
        bus.persist();
        Checkpoint hin = cp(bus, "Hinfahrt", 0);
        Checkpoint rueck = cp(bus, "Rückfahrt", 1);

        Event other = new Event();
        other.name = "Andere Reise";
        other.scanCode = TokenGenerator.newToken();
        other.persist();
        Checkpoint otherCp = cp(other, "Einlass", 0);

        Participant p = new Participant();
        p.event = bus;
        p.firstName = "Max";
        p.lastName = "Mustermann";
        p.token = TokenGenerator.newToken();
        p.persist();
        return new Fixture(hin.id, rueck.id, otherCp.id, p.token, bus.scanCode, other.scanCode);
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

        var first = scanService.scan(f.token(), f.code(), f.hin(), "tester");
        assertEquals(Status.OK, first.status());
        assertEquals("Max Mustermann", first.name());
        assertEquals(1, first.checkedIn());
        assertEquals(1, first.total());

        var again = scanService.scan(f.token().toLowerCase(), f.code(), f.hin(), "tester");
        assertEquals(Status.ALREADY, again.status());
        assertNotNull(again.time());

        // Rückfahrt ist ein eigener Check-in-Punkt
        assertEquals(Status.OK, scanService.scan(f.token(), f.code(), f.rueck(), "tester").status());

        assertEquals(Status.NOT_FOUND, scanService.scan("GIBTESNICHT", f.code(), f.hin(), "tester").status());
        assertEquals(Status.NOT_FOUND, scanService.scan("", f.code(), f.hin(), "tester").status());

        var wrong = scanService.scan(f.token(), f.otherCode(), f.otherCp(), "tester");
        assertEquals(Status.WRONG_EVENT, wrong.status());
        assertEquals("Busreise", wrong.otherEvent());

        // Checkpoint-ID einer anderen Veranstaltung mit falschem Scan-Link-Code → abgelehnt
        assertThrows(NotFoundException.class, () -> scanService.scan(f.token(), f.code(), f.otherCp(), "tester"));
        assertThrows(NotFoundException.class, () -> scanService.scan(f.token(), null, f.hin(), "tester"));
    }

    @Test
    void manualCheckinAndSearch() {
        Fixture f = setup();
        var hits = scanService.search("max must", f.code(), f.hin());
        assertEquals(1, hits.size());
        assertEquals("Max Mustermann", hits.get(0).name());
        assertEquals(null, hits.get(0).checkedInAt());
        assertEquals(0, scanService.search("x", f.code(), f.hin()).size());
        assertEquals(0, scanService.search("niemand", f.code(), f.hin()).size());
        // Suche über fremden Scan-Link-Code ist nicht möglich
        assertThrows(NotFoundException.class, () -> scanService.search("max", f.otherCode(), f.hin()));

        var first = scanService.manual(hits.get(0).id(), f.code(), f.hin(), "tester");
        assertEquals(Status.OK, first.status());
        assertEquals(true, first.manual());
        assertEquals(Status.ALREADY, scanService.manual(hits.get(0).id(), f.code(), f.hin(), "tester").status());
        // danach zählt auch der QR-Code als bereits gescannt
        assertEquals(Status.ALREADY, scanService.scan(f.token(), f.code(), f.hin(), "tester").status());
        assertNotNull(scanService.search("mustermann", f.code(), f.hin()).get(0).checkedInAt());
        // Teilnehmer einer anderen Veranstaltung kann nicht über diesen Link eingecheckt werden
        assertThrows(NotFoundException.class,
                () -> scanService.manual(hits.get(0).id(), f.otherCode(), f.otherCp(), "tester"));
    }

    @Test
    void tokenFormat() {
        String t = TokenGenerator.newToken();
        assertEquals(26, t.length());
        assertEquals(t, TokenGenerator.normalize(" " + t.toLowerCase() + "\n"));
    }
}
