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

/** Klickt sich einmal durch alle Seiten: Login, Veranstaltung, Teilnehmer, Import, Scan, Benutzer. */
@QuarkusTest
class PagesTest {

    @Test
    void unauthenticatedRedirectsToLogin() {
        given().redirects().follow(false).when().get("/events")
                .then().statusCode(302).header("Location", containsString("/login"));
        given().when().get("/login").then().statusCode(200).body(containsString("Anmelden"));
    }

    @Test
    void fullFlow() {
        CookieFilter cookies = new CookieFilter();
        RestAssured.given().filter(cookies).redirects().follow(false)
                .formParam("j_username", "admin").formParam("j_password", "admin")
                .post("/j_security_check").then().statusCode(302);

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

        String qrPath = detail.replaceAll("(?s).*?data-copy-qr=\"([^\"]+)\".*", "$1");
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
        String scanPath = select.replaceAll("(?s).*?href=\"(/scan/\\d+)\".*", "$1");
        given().filter(cookies).get(scanPath).then().statusCode(200).body(containsString("Kamera starten"));

        long cpId = Long.parseLong(scanPath.substring("/scan/".length()));
        given().filter(cookies).contentType(ContentType.JSON)
                .body("{\"token\":\"UNBEKANNT\",\"checkpointId\":" + cpId + "}")
                .post("/api/scan").then().statusCode(200).body("status", equalTo("NOT_FOUND"));

        given().filter(cookies).get("/users").then().statusCode(200).body(containsString("admin"));
        given().filter(cookies).get("/account").then().statusCode(200);
    }
}
