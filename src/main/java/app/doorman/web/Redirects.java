package app.doorman.web;

import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Post/Redirect/Get mit optionaler Hinweis- oder Fehlermeldung. */
public final class Redirects {

    private Redirects() {
    }

    public static Response to(String path) {
        return Response.seeOther(URI.create(path)).build();
    }

    public static Response ok(String path, String msg) {
        return to(path + sep(path) + "msg=" + enc(msg));
    }

    public static Response err(String path, String err) {
        return to(path + sep(path) + "err=" + enc(err));
    }

    private static String sep(String path) {
        return path.contains("?") ? "&" : "?";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
