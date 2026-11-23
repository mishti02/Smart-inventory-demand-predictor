package web;

import com.sun.net.httpserver.HttpServer;
import exception.InsufficientStockException;
import exception.InvalidInputException;
import model.Product;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SalesApi {
    private final AppContext app;

    public SalesApi(AppContext app) {
        this.app = app;
    }

    @FunctionalInterface
    private interface Action {
        Object handle(Map<String, String> params) throws Exception;
    }

    public void register(HttpServer server) {
        get(server, "/api/sales/summary", this::summary);
        get(server, "/api/simulation", params -> simulationStatus());
        post(server, "/api/sales/record", this::record);
        post(server, "/api/simulation/start", form -> {
            app.simulator.start();
            return simulationStatus();
        });
        post(server, "/api/simulation/stop", form -> {
            app.simulator.stop();
            return simulationStatus();
        });
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
                HttpUtil.sendJson(ex, 200, action.handle(params));
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
                Object result = action.handle(HttpUtil.formBody(ex));
                app.saveQuietly();
                HttpUtil.sendJson(ex, 200, result);
            } catch (InvalidInputException | InsufficientStockException e) {
                HttpUtil.sendJson(ex, 400, HttpUtil.single("error", e.getMessage()));
            } catch (Exception e) {
                System.err.println("[Web] error in " + path + ": " + e);
                HttpUtil.sendJson(ex, 500, HttpUtil.single("error", "Server error: " + e.getMessage()));
            }
        });
    }

    private static int intParam(Map<String, String> params, String name, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(params.getOrDefault(name, "").trim());
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double round1(double x) {
        return Math.round(x * 10.0) / 10.0;
    }

    // ---------- endpoints ----------

    // GET /api/sales/summary?days=7
    private Object summary(Map<String, String> params) {
        int days = intParam(params, "days", 7, 1, 90);
        LocalDate today = LocalDate.now();

        int total = app.analytics.unitsBetween(today.minusDays(days - 1), today);
        int previous = app.analytics.unitsBetween(today.minusDays(2 * days - 1), today.minusDays(days));
        double changePct = previous == 0 ? (total == 0 ? 0 : 100) : round1((total - previous) * 100.0 / previous);

        List<Map<String, Object>> daily = new ArrayList<>();
        for (Map.Entry<LocalDate, Integer> e : app.analytics.dailyUnits(days, today).entrySet()) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("date", e.getKey());
            d.put("label", e.getKey().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            d.put("units", e.getValue());
            daily.add(d);
        }

        Map<String, Integer> sold = app.reports.unitsByCategory(days, today);
        int totalSold = sold.values().stream().mapToInt(Integer::intValue).sum();
        List<Map<String, Object>> byCategory = new ArrayList<>();
        sold.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("category", e.getKey());
                    m.put("units", e.getValue());
                    m.put("sharePct", totalSold == 0 ? 0.0 : round1(e.getValue() * 100.0 / totalSold));
                    byCategory.add(m);
                });

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", days);
        out.put("total", total);
        out.put("previousTotal", previous);
        out.put("changePct", changePct);
        out.put("totalRecords", app.analytics.countSales());
        out.put("daily", daily);
        out.put("byCategory", byCategory);
        return out;
    }

    // POST /api/sales/record   (productId, date, quantity)
    private Object record(Map<String, String> form) throws Exception {
        int productId = HttpUtil.requireInt(form, "productId", "Product");
        int quantity = HttpUtil.requireInt(form, "quantity", "Quantity");
        LocalDate date = parseDate(form.getOrDefault("date", ""));
        if (date.isAfter(LocalDate.now())) {
            throw new InvalidInputException("Date cannot be in the future.");
        }

        app.manager.recordSale(productId, date, quantity);   // validates, reduces stock, stores the sale
        Product p = app.manager.getProduct(productId);

        Map<String, Object> product = new LinkedHashMap<>();
        product.put("id", productId);
        product.put("name", p == null ? "" : p.getName());
        product.put("stock", p == null ? 0 : p.getCurrentStock());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("message", "Recorded " + quantity + " x " + (p == null ? "product" : p.getName()));
        out.put("product", product);
        return out;
    }

    private static LocalDate parseDate(String text) throws InvalidInputException {
        String t = text.trim();
        if (t.isEmpty()) return LocalDate.now();
        try {
            return LocalDate.parse(t);
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("Date must look like 2026-11-20 (yyyy-MM-dd).");
        }
    }

    private Map<String, Object> simulationStatus() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("running", app.simulator.isRunning());
        return m;
    }
}