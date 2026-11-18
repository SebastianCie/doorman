package app.doorman;

import app.doorman.service.CsvImportService;
import app.doorman.service.CsvImportService.RowStatus;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvImportServiceTest {

    @Test
    void excelSemicolonWindows1252WithHeader() {
        String csv = "Nachname;Vorname;E-Mail;Bemerkung\r\nMüller;Jürgen;jm@example.com;Sitz 3\r\n;;;\r\n"
                + "Schäfer;Anna;;\r\nDoppelt;Hans;JM@example.com;\r\nOhne;Mail;kaputt;\r\n";
        byte[] bytes = csv.getBytes(Charset.forName("windows-1252"));

        CsvImportService.DecodedText decoded = CsvImportService.decode(bytes);
        assertEquals("Windows-1252", decoded.encoding());

        CsvImportService.Result r = CsvImportService.parse(decoded.text(), decoded.encoding(), Set.of());
        assertTrue(r.headerDetected());
        assertEquals("Semikolon", r.delimiter());
        assertEquals(4, r.rows().size());

        assertEquals("Jürgen", r.rows().get(0).firstName());
        assertEquals("Müller", r.rows().get(0).lastName());
        assertEquals("Sitz 3", r.rows().get(0).note());
        assertEquals(RowStatus.NEW, r.rows().get(0).status());
        assertEquals(RowStatus.NEW, r.rows().get(1).status());
        assertEquals(RowStatus.DUPLICATE, r.rows().get(2).status());
        assertEquals(RowStatus.ERROR, r.rows().get(3).status());
        assertEquals(2, r.countNew());
    }

    @Test
    void utf8CommaWithoutHeaderAndExistingDuplicates() {
        String csv = "﻿Max,Mustermann,max@example.com\nErika,Musterfrau,\n";
        CsvImportService.DecodedText decoded = CsvImportService.decode(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("UTF-8", decoded.encoding());

        Set<String> existing = Set.of(CsvImportService.key("Erika", "Musterfrau", null));
        CsvImportService.Result r = CsvImportService.parse(decoded.text(), decoded.encoding(), existing);
        assertEquals(false, r.headerDetected());
        assertEquals("Komma", r.delimiter());
        assertEquals("Max", r.rows().get(0).firstName());
        assertEquals("max@example.com", r.rows().get(0).email());
        assertEquals(RowStatus.DUPLICATE, r.rows().get(1).status());
    }
}
