package app.doorman.service;

import java.security.SecureRandom;

/**
 * Erzeugt zufällige QR-Codes: 128 Bit Zufall, Base32-kodiert (26 Zeichen, A–Z und 2–7).
 * Großbuchstaben und Ziffern passen in den kompakten "alphanumerischen" QR-Modus,
 * dadurch wird der QR-Code kleiner und lässt sich leichter scannen.
 */
public final class TokenGenerator {

    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenGenerator() {
    }

    public static String newToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(26);
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(ALPHABET[(buffer >> (bits - 5)) & 31]);
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(ALPHABET[(buffer << (5 - bits)) & 31]);
        }
        return sb.toString();
    }

    /** Normalisiert gescannte Eingaben (Leerzeichen, Kleinbuchstaben). */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("\\s+", "").toUpperCase();
    }
}
