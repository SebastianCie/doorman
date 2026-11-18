package app.doorman.web;

import io.quarkus.qute.TemplateExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@TemplateExtension
public class TemplateExtensions {

    static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm").withZone(ZONE);
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZONE);
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /** {instant.hm} → "08:14" */
    static String hm(Instant i) {
        return i == null ? "" : HM.format(i);
    }

    /** {instant.dt} → "18.11.2026 08:14" */
    static String dt(Instant i) {
        return i == null ? "" : DT.format(i);
    }

    /** {date.de} → "18.11.2026" */
    static String de(LocalDate d) {
        return d == null ? "" : D.format(d);
    }
}
