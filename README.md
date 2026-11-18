# Doorman – Zutrittskontrolle per QR-Code

Eine Anwendung, ein Container:

- **Verwaltung** (`/events`, Desktop): Veranstaltungen mit Check-in-Punkten (z. B. Hin- und Rückfahrt),
  Teilnehmer manuell oder per CSV, QR-Code pro Teilnehmer in die Zwischenablage kopieren und in Outlook einfügen.
- **Scanner** (Smartphone): Jede Veranstaltung hat einen eigenen **Scan-Link** (`/s/<code>`). Der Admin kopiert ihn
  auf der Veranstaltungsseite oder schickt ihn per WhatsApp bzw. als QR-Code an die Scanner. Die öffnen ihn,
  melden sich an und scannen. Die Kamera liest den QR-Code und zeigt dann eine dieser Meldungen an:
  🟢 Gültig, 🟠 Bereits gescannt (mit Uhrzeit), 🔴 QR-Code nicht gefunden / falsche Veranstaltung.
  Auf dem iPhone in Safari: *Teilen → Zum Home-Bildschirm* legt den Scan-Link als App ab (PWA).

Stack: Java 21, Quarkus, Qute (serverseitiges HTML), Hibernate Panache, Flyway, PostgreSQL,
ZXing (QR-Erzeugung), [qr-scanner](https://github.com/nimiq/qr-scanner) (Scannen im Browser, MIT).

## Lokal starten

Voraussetzung: Java 21+, Podman oder Docker (für die automatische Dev-Datenbank).

```bash
podman machine start     # falls nicht schon läuft
./mvnw quarkus:dev
```

Unter http://localhost:8080 mit **admin / admin** anmelden. Quarkus startet PostgreSQL automatisch in einem Container.

Tests: `./mvnw test`

> Auf dem Smartphone braucht die Kamera **HTTPS**. Zum lokalen Testen mit dem Handy daher z. B. direkt auf
> OpenShift deployen oder einen HTTPS-Tunnel verwenden. Ohne Kamera geht auch die manuelle Code-Eingabe auf der Scanner-Seite.

## Auf OpenShift deployen

Das Container-Image baut GitHub Actions bei jedem Push auf `main`: `ghcr.io/sebastiancie/doorman:latest`
(Workflow: [.github/workflows/image.yml](.github/workflows/image.yml)). Die Datenbank läuft extern (z. B. Neon),
deshalb lässt sich die App jederzeit neu aufsetzen, ohne Daten zu verlieren.

**Einmalig**
```bash
brew install openshift-cli                 # falls oc fehlt
cp deploy/.env.example deploy/.env         # DATABASE_URL und INITIAL_ADMIN_PASSWORD eintragen
```
Das Paket `doorman` unter *GitHub → Profil → Packages* einmal auf **Public** stellen, damit OpenShift es ohne
Zugangsdaten laden kann.

**Deployen, aktualisieren oder nach einem Cluster-Reset wiederherstellen**
```bash
oc login --token=... --server=...          # "Copy login command" in der OpenShift-Konsole
./deploy/deploy.sh
```
Das Skript legt im aktuellen Projekt Secret, Deployment, Service und Route an (oder aktualisiert sie), startet mit
dem neuesten Image neu und gibt am Ende die URL aus. Beim ersten Lauf erzeugt es `SESSION_ENCRYPTION_KEY` und
speichert ihn in `deploy/.env`. `deploy/.env` wird nicht eingecheckt.

Die Neon-URL kann so eingetragen werden, wie Neon sie anzeigt. Das Skript wandelt sie in eine JDBC-URL um und
nutzt statt des `-pooler`-Hosts die direkte Verbindung (für die Flyway-Migrationen).

Die Manifeste liegen in [openshift/](openshift/). Für manuelle Deployments ohne Skript gibt es
`openshift/secret.example.yaml`.

### Umgebungsvariablen

| Variable | Bedeutung |
|---|---|
| `DB_URL` | z. B. `jdbc:postgresql://host:5432/doorman` |
| `DB_USER` / `DB_PASSWORD` | DB-Zugang |
| `SESSION_ENCRYPTION_KEY` | Schlüssel für das Login-Cookie (≥ 16 Zeichen). Fehlt er, wird bei jedem Neustart ein neuer erzeugt und alle müssen sich neu anmelden. |
| `INITIAL_ADMIN_PASSWORD` | Passwort für `admin` beim allerersten Start. Fehlt es, wird eines erzeugt und ins Log geschrieben. |

## Wie es funktioniert

- Jeder Teilnehmer bekommt einen zufälligen Code (128 Bit, 26 Zeichen, z. B. `K7MQ2XP9…`), der im QR steht.
  Erraten ist praktisch ausgeschlossen. Über *Bearbeiten → Neuen QR-Code erzeugen* wird ein alter Code ungültig.
- Ein Teilnehmer hat **einen** QR-Code. Er kann an jedem Check-in-Punkt der Veranstaltung genau einmal eingecheckt werden.
  Doppelte Scans, auch gleichzeitig von mehreren Geräten, verhindert die Datenbank (`unique` + `insert … on conflict do nothing`).
- Check-ins lassen sich in der Teilnehmerliste manuell setzen oder zurücknehmen (Klick auf „–“ bzw. „✓ 08:14“).
- Login: Formular-Login, Benutzer und bcrypt-Passwörter in der DB. Rollen:
  - **Admin**: darf alles (Veranstaltungen, Teilnehmer, Benutzer, scannen).
  - **Scanner**: darf nur scannen, und nur über den Scan-Link einer Veranstaltung. Ohne gültigen Link-Code
    ist kein Scan möglich. „Link erneuern“ macht einen weitergegebenen Link sofort ungültig.
- Datenschutz: Beim Löschen einer Veranstaltung werden alle Teilnehmer und Check-ins mitgelöscht.

## CSV-Format

```
Vorname;Nachname;E-Mail;Bemerkung
Max;Mustermann;max@example.com;Sitz 12
```

Semikolon/Komma/Tab und UTF-8/Windows-1252 (Excel) werden erkannt. Ohne Kopfzeile gilt die Reihenfolge oben.
Vor dem Import gibt es eine Vorschau. Dubletten und fehlerhafte Zeilen werden übersprungen.
