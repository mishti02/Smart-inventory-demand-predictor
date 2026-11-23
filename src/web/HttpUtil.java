package web;

import com.sun.net.httpserver.HttpExchange;
import exception.InvalidInputException;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

// Small helpers for the endpoints that receive data from the browser
final class HttpUtil {
    private static final int MAX_BODY = 64 * 1024;   // never read more than 64 KB from a browser

    private HttpUtil() { }

    static void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        WebServer.sendText(ex, status, "application/json; charset=utf-8", Json.stringify(body));
    }

    // {"key": "text"}
    static Map<String, Object> single(String key, String text) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(key, text);
        return m;
    }

    // Turns "name=Milk+1L&stock=10" into a Map (the format browsers use to send form data)
    static Map<String, String> parseParams(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) return map;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            map.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return map;
    }

    static Map<String, String> formBody(HttpExchange ex) throws IOException {
        byte[] bytes = ex.getRequestBody().readNBytes(MAX_BODY);
        return parseParams(new String(bytes, StandardCharsets.UTF_8));
    }

    // A whole number from the form. A missing or bad value becomes a clear message for the user.
    static int requireInt(Map<String, String> form, String name, String label) throws InvalidInputException {
        String text = form.get(name);
        if (text == null || text.trim().isEmpty()) {
            throw new InvalidInputException(label + " is required.");
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new InvalidInputException(label + " must be a whole number.");
        }
    }

    // A page from ANOTHER website must not be able to change your data through your browser.
    // Browsers add an Origin header to such requests, and it has to match this server.
    static boolean fromThisSite(HttpExchange ex) {
        String origin = ex.getRequestHeaders().getFirst("Origin");
        if (origin == null) return true;                       // not a cross-site browser request
        String host = ex.getRequestHeaders().getFirst("Host");
        return origin.equals("http://" + host);
    }
}