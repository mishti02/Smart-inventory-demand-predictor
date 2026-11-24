package web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import exception.InsufficientStockException;
import exception.InvalidInputException;
import model.Product;
import service.FileHandler;

import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class SettingsApi {
    private final AppContext app;

    public SettingsApi(AppContext app) {
        this.app = app;
    }

    @FunctionalInterface
    private interface Action {
        Object handle(HttpExchange ex, Map<String, String> params) throws Exception;
    }

    public void register(HttpServer server) {
        get(server, "/api/settings", this::info);
        post(server, "/api/data/save", this::saveNow);
        post(server, "/api/data/sample", this::sample);
        post(server, "/api/data/csv", this::csvCopy);
        post(server, "/api/data/reset", this::reset);
    }

    // ---------- plumbing ----------

    private void get(HttpServer server, String path, Action action) {
        server.createContext(path, ex -> {
            WebServer.log(ex);
            try {
                if (!"GET".equals(ex.getRequestMethod())) {
                    HttpUtil.sendJson(ex, 405, HttpUtil.single("error", "Use GET for " + path));
                    return;
                }
                Map<String, String> params = HttpUtil.parseParams(ex.getRequestURI().getRawQuery());
                HttpUtil.sendJson(ex, 200, action.handle(ex, params));
            } catch (Exception e) {
                System.err.println("[Web] error in " + path + ": " + e);
                HttpUtil.sendJson(ex, 500, HttpUtil.single("error", "Server error: " + e.getMessage()));
            }
        });
    }

    private void post(HttpServer server, String path, Action action) {
        server.createContext(path, ex -> {
            WebServer.log(ex);
            try {
                if (!"POST".equals(ex.getRequestMethod())) {
                    HttpUtil.sendJson(ex, 405, HttpUtil.single("error", "Use POST for " + path));
                    return;
                }
                if (!HttpUtil.fromThisSite(ex)) {
                    HttpUtil.sendJson(ex, 403, HttpUtil.single("error", "Request from another site was blocked."));
                    return;
                }
                Object result = action.handle(ex, HttpUtil.formBody(ex));
                app.saveQuietly();
                HttpUtil.sendJson(ex, 200, result);
            } catch (InvalidInputException e) {
                HttpUtil.sendJson(ex, 400, HttpUtil.single("error", e.getMessage()));
            } catch (Exception e) {
                System.err.println("[Web] error in " + path + ": " + e);
                HttpUtil.sendJson(ex, 500, HttpUtil.single("error", "Server error: " + e.getMessage()));
            }
        });
    }

    private static Map<String, Object> done(String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("message", message);
        return out;
    }

    // ---------- endpoints ----------

    // GET /api/settings
    private Object info(HttpExchange ex, Map<String, String> params) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("storage", app.storageName());
        m.put("address", ex.getRequestHeaders().getFirst("Host"));
        m.put("products", app.manager.getAllProducts().size());
        m.put("salesRecords", app.analytics.countSales());
        m.put("simulationRunning", app.simulator.isRunning());
        m.put("autoSaveSeconds", app.autoSaveMillis() / 1000);
        m.put("javaVersion", System.getProperty("java.version"));
        m.put("dataFolder", Paths.get("data").toAbsolutePath().normalize().toString());
        return m;
    }

    // POST /api/data/save
    private Object saveNow(HttpExchange ex, Map<String, String> form) throws Exception {
        app.save();
        return done("Saved to the " + app.storageName());
    }

    // POST /api/data/sample: 14 days of random sales for every product
    private Object sample(HttpExchange ex, Map<String, String> form) throws Exception {
        List<Product> products = app.manager.getAllProducts();
        if (products.isEmpty()) {
            throw new InvalidInputException("Add products first on the Products page.");
        }
        Random random = new Random();
        LocalDate today = LocalDate.now();
        int added = 0;

        for (Product p : products) {
            for (int daysAgo = 13; daysAgo >= 0; daysAgo--) {
                int qty = 1 + random.nextInt(4);                 // 1 to 4 units
                if (qty > p.getCurrentStock()) continue;         // not enough stock, skip this day
                try {
                    app.manager.recordSale(p.getId(), today.minusDays(daysAgo), qty);
                    added++;
                } catch (InvalidInputException | InsufficientStockException e) {
                    // skipped, nothing to do
                }
            }
        }
        return done("Added " + added + " sample sales over the last 14 days");
    }

    // POST /api/data/csv: writes products.csv and sales.csv into the data folder
    private Object csvCopy(HttpExchange ex, Map<String, String> form) throws Exception {
        new FileHandler("data").save(app.manager);
        return done("CSV copy written to the data folder (products.csv and sales.csv)");
    }

    // POST /api/data/reset   (confirm=RESET)
    private Object reset(HttpExchange ex, Map<String, String> form) throws Exception {
        if (!"RESET".equals(form.getOrDefault("confirm", "").trim())) {
            throw new InvalidInputException("Type RESET to confirm.");
        }
        app.simulator.stop();
        app.manager.clearAll();
        return done("All products and sales were deleted.");
    }
}