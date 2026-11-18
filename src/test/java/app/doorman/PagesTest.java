package app.doorman;

import io.restassured.RestAssured;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.ContentType;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

/** Klickt sich einmal durch alle Seiten: Login, Veranstaltung, Teilnehmer, Import, Scan, Benutzer. */
@QuarkusTest
class PagesTest {

    @Test
    void unauthenticatedRedirectsToLogin() {
        given().redirects().follow(false).when().get("/events")
                .then().statusCode(302).header("Location", containsString("/login"));
        given().when().get("/login").then().statusCode(200).body(containsString("Anmelden"))
                .body(containsString("href=\"/help\""));
        // Hilfe ohne Anmeldung: nur die Kapitel für Scanner
        given().when().get("/help").then().statusCode(200)
                .body(containsString("id=\"scannen\"")).body(containsString("id=\"fragen\""))
                .body(not(containsString("id=\"teilnehmer\"")));
    }

    static CookieFilter login(String user, String password) {
        CookieFilter cookies = new CookieFilter();
        RestAssured.given().filter(cookies).redirects().follow(false)
                .formParam("j_username", user).formParam("j_password", password)
                .post("/j_security_check").then().statusCode(302);
        return cookies;
    }

    @Test
    void fullFlow() {
        CookieFilter cookies = login("admin", "admin");

        given().filter(cookies).get("/events").then().statusCode(200).body(containsString("Veranstaltungen"));
        given().filter(cookies).get("/events/new").then().statusCode(200);

        String location = given().filter(cookies).redirects().follow(false)
                .formParam("name", "Testreise").formParam("date", "2030-05-01")
                .formParam("checkpoints", "Hinfahrt\nRückfahrt")
                .post("/events").then().statusCode(303).extract().header("Location");
        String eventPath = location.replaceAll("^https?://[^/]+", "").replaceAll("\\?.*$", "");

        given().filter(cookies).redirects().follow(false)
                .formParam("firstName", "Erika").formParam("lastName", "Musterfrau")
                .formParam("email", "erika@example.com")
                .post(eventPath + "/participants").then().statusCode(303);

        String csv = "Vorname;Nachname;E-Mail\nMax;Mustermann;max@example.com\n";
        given().filter(cookies)
                .multiPart("file", "teilnehmer.csv", csv.getBytes(StandardCharsets.UTF_8), "text/csv")
                .post(eventPath + "/import/preview").then().statusCode(200)
                .body(containsString("1 Teilnehmer importieren"));
        given().filter(cookies).redirects().follow(false)
                .formParam("text", csv).formParam("encoding", "UTF-8")
                .post(eventPath + "/import/confirm").then().statusCode(303);

        String detail = given().filter(cookies).get(eventPath).then().statusCode(200)
                .body(containsString("Musterfrau")).body(containsString("Mustermann"))
                .body(containsString("Rückfahrt")).extract().asString();

        String qrPath = detail.replaceAll("(?s).*?data-copy-qr=\"([^\"]+/participants/[^\"]+)\".*", "$1");
        given().filter(cookies).get(qrPath).then().statusCode(200).contentType("image/png");
        String participantPath = qrPath.replace("/qr.png", "");
        given().filter(cookies).get(participantPath + "/edit").then().statusCode(200);
        given().filter(cookies).formParam("sent", "true").post(participantPath + "/sent")
                .then().statusCode(200).body("sent", equalTo(true));

        given().filter(cookies).get(eventPath + "/edit").then().statusCode(200);
        given().filter(cookies).get(eventPath + "/import").then().statusCode(200);
        given().filter(cookies).get(eventPath + "/export.csv").then().statusCode(200)
                .body(containsString("Mustermann"));

        String select = given().filter(cookies).get("/scan").then().statusCode(200)
                .body(containsString("Testreise")).extract().asString();
        String scanPath = select.replaceAll("(?s).*?href=\"(/s/[A-Z0-9]+/\\d+)\".*", "$1");
        given().filter(cookies).get(scanPath).then().statusCode(200).body(containsString("Kamera starten"));
        String code = scanPath.split("/")[2];
        long cpId = Long.parseLong(scanPath.split("/")[3]);
        given().filter(cookies).contentType(ContentType.JSON)
                .body("{\"token\":\"UNBEKANNT\",\"eventCode\":\"" + code + "\",\"checkpointId\":" + cpId + "}")
                .post("/api/scan").then().statusCode(200).body("status", equalTo("NOT_FOUND"));

        // Scan-Link wird auf der Veranstaltungsseite angezeigt, inkl. QR
        given().filter(cookies).get(eventPath).then().body(containsString("/s/" + code))
                .body(containsString("https://wa.me/?text="));
        given().filter(cookies).get(eventPath + "/scan-link.png").then().statusCode(200).contentType("image/png");
        given().filter(cookies).get("/s/" + code).then().statusCode(200).body(containsString("Check-in-Punkt wählen"));
        given().filter(cookies).get("/s/" + code + "/manifest.webmanifest").then().statusCode(200)
                .body("start_url", equalTo("/s/" + code));

        // Scanner-Benutzer anlegen: darf nur scannen
        given().filter(cookies).redirects().follow(false)
                .formParam("username", "scanner1").formParam("password", "geheim123").formParam("role", "scanner")
                .post("/users").then().statusCode(303);
        CookieFilter scanner = login("scanner1", "geheim123");
        given().filter(scanner).get("/").then().statusCode(200).body(containsString("Scan-Link"));
        given().filter(scanner).get(scanPath).then().statusCode(200).body(containsString("Kamera starten"));
        given().filter(scanner).contentType(ContentType.JSON)
                .body("{\"token\":\"UNBEKANNT\",\"eventCode\":\"" + code + "\",\"checkpointId\":" + cpId + "}")
                .post("/api/scan").then().statusCode(200).body("status", equalTo("NOT_FOUND"));
        String pid = given().filter(scanner).queryParam("q", "erika").queryParam("eventCode", code)
                .queryParam("checkpointId", cpId).get("/api/scan/search")
                .then().statusCode(200).body("[0].name", equalTo("Erika Musterfrau")).extract().path("[0].id").toString();
        given().filter(scanner).contentType(ContentType.JSON)
                .body("{\"participantId\":" + pid + ",\"eventCode\":\"" + code + "\",\"checkpointId\":" + cpId + ",\"verified\":false}")
                .post("/api/scan/manual").then().statusCode(400);
        given().filter(scanner).contentType(ContentType.JSON)
                .body("{\"participantId\":" + pid + ",\"eventCode\":\"" + code + "\",\"checkpointId\":" + cpId + ",\"verified\":true}")
                .post("/api/scan/manual").then().statusCode(200).body("status", equalTo("OK")).body("manual", equalTo(true));
        given().filter(cookies).get(eventPath).then().body(containsString("· M"));
        given().filter(cookies).get(eventPath + "/export.csv").then().body(containsString("(manuell)"));
        given().filter(scanner).get("/account").then().statusCode(200);
        given().filter(scanner).get("/help").then().statusCode(200)
                .body(containsString("id=\"scannen\"")).body(not(containsString("id=\"teilnehmer\"")));
        for (String forbidden : new String[]{"/events", eventPath, eventPath + "/import", "/scan", "/users", qrPath}) {
            given().filter(scanner).redirects().follow(false).get(forbidden).then().statusCode(403);
        }
        given().filter(scanner).redirects().follow(false).formParam("name", "Hack").post("/events")
                .then().statusCode(403);
        // Falscher Scan-Link → 404
        given().filter(scanner).get("/s/FALSCH/" + cpId).then().statusCode(404);

        // Neuer Scan-Link macht den alten ungültig
        given().filter(cookies).redirects().follow(false).post(eventPath + "/scan-code").then().statusCode(303);
        given().filter(scanner).get(scanPath).then().statusCode(404);

        given().filter(cookies).get("/users").then().statusCode(200).body(containsString("admin"));
        given().filter(cookies).get("/help").then().statusCode(200)
                .body(containsString("id=\"teilnehmer\"")).body(containsString("id=\"scanner-einrichten\""));
        given().filter(cookies).get("/account").then().statusCode(200);
    }
}
