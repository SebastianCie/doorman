package app.doorman.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Liest Teilnehmer aus CSV-Dateien. Excel speichert CSV im deutschen Umfeld meist mit
 * Semikolon und Windows-1252, daher werden Trennzeichen und Zeichensatz automatisch erkannt.
 * Erkannte Spalten: Vorname, Nachname, E-Mail, Bemerkung. Ohne Kopfzeile gilt diese Reihenfolge.
 */
public final class CsvImportService {

    public enum RowStatus { NEW, DUPLICATE, ERROR }

    public record Row(int line, String firstName, String lastName, String email, String note,
                      RowStatus status, String message) {
        public boolean isNew() {
            return status == RowStatus.NEW;
        }
    }

    public record Result(List<Row> rows, String text, String encoding, String delimiter, boolean headerDetected) {
        public long countNew() {
            return rows.stream().filter(Row::isNew).count();
        }

        public long countSkipped() {
            return rows.size() - countNew();
        }
    }

    private static final Set<String> FIRST = Set.of("vorname", "firstname", "first name", "first_name", "givenname");
    private static final Set<String> LAST = Set.of("nachname", "name", "lastname", "last name", "last_name",
            "familienname", "surname");
    private static final Set<String> EMAIL = Set.of("email", "e-mail", "mail", "e-mail-adresse", "emailadresse",
            "e-mail adresse", "email address");
    private static final Set<String> NOTE = Set.of("bemerkung", "bemerkungen", "notiz", "note", "notes",
            "kommentar", "info");

    private CsvImportService() {
    }

    /** Dekodiert die Datei als UTF-8, sonst als Windows-1252. Ein BOM wird entfernt. */
    public static DecodedText decode(byte[] bytes) {
        try {
            String s = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            return new DecodedText(stripBom(s), "UTF-8");
        } catch (CharacterCodingException e) {
            return new DecodedText(new String(bytes, Charset.forName("windows-1252")), "Windows-1252");
        }
    }

    public record DecodedText(String text, String encoding) {
    }

    /**
     * @param existingKeys Dublettenschlüssel der bereits vorhandenen Teilnehmer, siehe {@link #key}
     */
    public static Result parse(String text, String encoding, Set<String> existingKeys) {
        text = stripBom(text == null ? "" : text);
        char delimiter = detectDelimiter(text);
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get();

        List<CSVRecord> records;
        try (CSVParser parser = CSVParser.parse(new StringReader(text), format)) {
            records = parser.getRecords();
        } catch (IOException | RuntimeException e) {
            List<Row> err = List.of(new Row(0, null, null, null, null, RowStatus.ERROR,
                    "CSV konnte nicht gelesen werden: " + e.getMessage()));
            return new Result(err, text, encoding, delimiterLabel(delimiter), false);
        }

        int iFirst = 0, iLast = 1, iEmail = 2, iNote = 3;
        boolean header = false;
        if (!records.isEmpty()) {
            CSVRecord first = records.get(0);
            int f = -1, l = -1, m = -1, n = -1;
            for (int i = 0; i < first.size(); i++) {
                String h = first.get(i).toLowerCase(Locale.ROOT).trim();
                if (FIRST.contains(h) && f < 0) f = i;
                else if (LAST.contains(h) && l < 0) l = i;
                else if (EMAIL.contains(h) && m < 0) m = i;
                else if (NOTE.contains(h) && n < 0) n = i;
            }
            if (f >= 0 || l >= 0 || m >= 0) {
                header = true;
                iFirst = f;
                iLast = l;
                iEmail = m;
                iNote = n;
            }
        }

        Set<String> seen = new HashSet<>(existingKeys);
        List<Row> rows = new ArrayList<>();
        for (int r = header ? 1 : 0; r < records.size(); r++) {
            CSVRecord rec = records.get(r);
            int line = (int) rec.getRecordNumber();
            String firstName = cell(rec, iFirst);
            String lastName = cell(rec, iLast);
            String email = cell(rec, iEmail);
            String note = cell(rec, iNote);

            if (firstName == null && lastName == null && email == null && note == null) {
                continue;
            }
            RowStatus status = RowStatus.NEW;
            String message = null;
            if (firstName == null && lastName == null) {
                status = RowStatus.ERROR;
                message = "Kein Name";
            } else if (email != null && !email.contains("@")) {
                status = RowStatus.ERROR;
                message = "Ungültige E-Mail";
            } else if (!seen.add(key(firstName, lastName, email))) {
                status = RowStatus.DUPLICATE;
                message = "Bereits vorhanden";
            }
            rows.add(new Row(line, firstName, lastName, email, note, status, message));
        }
        return new Result(rows, text, encoding, delimiterLabel(delimiter), header);
    }

    /** Dubletten: gleiche E-Mail, ohne E-Mail gleicher Vor- und Nachname. */
    public static String key(String firstName, String lastName, String email) {
        if (email != null && !email.isBlank()) {
            return "m:" + email.trim().toLowerCase(Locale.ROOT);
        }
        return "n:" + norm(firstName) + "|" + norm(lastName);
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static String cell(CSVRecord rec, int index) {
        if (index < 0 || index >= rec.size()) {
            return null;
        }
        String v = rec.get(index).trim();
        return v.isEmpty() ? null : v;
    }

    private static char detectDelimiter(String text) {
        String firstLine = text.lines().filter(l -> !l.isBlank()).findFirst().orElse("");
        char best = ';';
        long bestCount = -1;
        for (char c : new char[]{';', ',', '\t'}) {
            long count = firstLine.chars().filter(ch -> ch == c).count();
            if (count > bestCount) {
                best = c;
                bestCount = count;
            }
        }
        return best;
    }

    private static String delimiterLabel(char c) {
        return switch (c) {
            case ';' -> "Semikolon";
            case ',' -> "Komma";
            default -> "Tabulator";
        };
    }

    private static String stripBom(String s) {
        return s.startsWith("﻿") ? s.substring(1) : s;
    }
}
