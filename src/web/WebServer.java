package web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// A small web server: it hands out the files in the web folder and answers /api/... requests.
public class WebServer {
    private final int port;
    private final Path webRoot;
    private final AppContext app;
    private HttpServer server;
    private ExecutorService pool;

    public WebServer(int port, Path webRoot, AppContext app) {
        this.port = port;
        this.webRoot = webRoot.toAbsolutePath().normalize();
        this.app = app;
    }

    public void start() throws IOException {
        // "localhost" means only THIS computer can reach the server, not the whole network
        server = HttpServer.create(new InetSocketAddress("localhost", port), 0);

        server.createContext("/", this::serveStatic);       // every address that nothing else claims
        server.createContext("/api/ping", this::ping);      // the longest matching address wins
        new Api(app).register(server);                      // the real endpoints
        new ProductApi(app).register(server);               // the endpoints that change products
        new SalesApi(app).register(server);                 // sales summary, record a sale, simulation

        pool = Executors.newFixedThreadPool(8);             // up to 8 requests handled at the same time
        server.setExecutor(pool);
        server.start();
    }

    public void stop() {
        if (server != null) server.stop(0);
        if (pool != null) pool.shutdownNow();
    }

    // ---------- the test endpoint ----------

    private void ping(HttpExchange ex) throws IOException {
        log(ex);
        String json = "{\"status\":\"ok\","
                + "\"storage\":\"" + app.storageName() + "\","
                + "\"time\":\"" + LocalDateTime.now().withNano(0) + "\","
                + "\"thread\":\"" + Thread.currentThread().getName() + "\"}";
        sendText(ex, 200, "application/json; charset=utf-8", json);
    }

    // ---------- files from the web folder ----------

    private void serveStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";

        Path file;
        try {
            file = webRoot.resolve(path.substring(1)).normalize();
        } catch (InvalidPathException e) {
            sendText(ex, 404, "text/plain; charset=utf-8", "404 Not found: " + path);
            return;
        }

        // the file must be INSIDE the web folder (this blocks addresses such as /../src/Launcher.java)
        if (!file.startsWith(webRoot) || !Files.isRegularFile(file)) {
            sendText(ex, 404, "text/plain; charset=utf-8", "404 Not found: " + path);
            return;
        }

        byte[] bytes = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", contentType(file.getFileName().toString()));
        ex.getResponseHeaders().set("Cache-Control", "no-cache");   // always show the latest edit
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    // ---------- helpers ----------

    static void sendText(HttpExchange ex, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String contentType(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".html")) return "text/html; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (n.endsWith(".json")) return "application/json; charset=utf-8";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".ico")) return "image/x-icon";
        if (n.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }

    // Prints which request arrived and WHICH THREAD handled it
    static void log(HttpExchange ex) {
        System.out.println("[Web] " + ex.getRequestMethod() + " " + ex.getRequestURI()
                + "   (" + Thread.currentThread().getName() + ")");
    }
}