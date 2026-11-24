package web;

import com.sun.net.httpserver.HttpServer;
import model.Product;
import model.ReorderSuggestion;
import model.SaleRecord;
import model.UrgencyLevel;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReportApi {
    private final AppContext app;

    public ReportApi(AppContext app) {
        this.app = app;
    }

    public void register(HttpServer server) {
        // GET /api/reports?days=14&dead=30
        server.createContext("/api/reports", ex -> {
            WebServer.log(ex);
            try {
                if (!"GET".equals(ex.getRequestMethod())) {
                    HttpUtil.sendJson(ex, 405, HttpUtil.single("error", "Use GET for /api/reports"));
                    return;
                }
                Map<String, String> params = HttpUtil.parseParams(ex.getRequestURI().getRawQuery());
                HttpUtil.sendJson(ex, 200, reports(params));
            } catch (Exception e) {
                System.err.println("[Web] error in /api/reports: " + e);
                HttpUtil.sendJson(ex, 500, HttpUtil.single("error", "Server error: " + e.getMessage()));
            }
        });

        // GET /api/report/download?days=14&dead=30  (sends the text report as a file)
        server.createContext("/api/report/download", ex -> {
            WebServer.log(ex);
            try {
                if (!"GET".equals(ex.getRequestMethod())) {
                    HttpUtil.sendJson(ex, 405, HttpUtil.single("error", "Use GET for /api/report/download"));
                    return;
                }
                Map<String, String> params = HttpUtil.parseParams(ex.getRequestURI().getRawQuery());
                int days = intParam(params, "days", 14, 1, 365);
                int deadDays = intParam(params, "dead", 30, 1, 365);
                LocalDate today = LocalDate.now();

                List<ReorderSuggestion> list = app.predictor.generateSuggestions(7, 2, 14, today);
                String text = app.reports.buildTextReport(list, days, deadDays, today);

                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                ex.getResponseHeaders().set("Content-Disposition",
                        "attachment; filename=\"smart-inventory-report-" + today + ".txt\"");
                ex.getResponseHeaders().set("Cache-Control", "no-store");
                ex.sendResponseHeaders(200, bytes.length);
                try (OutputStream out = ex.getResponseBody()) {
                    out.write(bytes);
                }
            } catch (Exception e) {
                System.err.println("[Web] error in /api/report/download: " + e);
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

    private static double round2(double x) {
        return Math.round(x * 100.0) / 100.0;
    }

    private Object reports(Map<String, String> params) {
        int days = intParam(params, "days", 14, 1, 365);
        int deadDays = intParam(params, "dead", 30, 1, 365);
        LocalDate today = LocalDate.now();
        List<Product> products = app.manager.getAllProducts();

        int unitsSold = app.analytics.unitsBetween(today.minusDays(days - 1), today);

        // status counts from the standard prediction (7 days, 2 safety, 14 cover)
        List<ReorderSuggestion> list = app.predictor.generateSuggestions(7, 2, 14, today);
        Map<UrgencyLevel, Long> counts = app.reports.countByUrgency(list);
        long critical = counts.get(UrgencyLevel.CRITICAL);
        long warning = counts.get(UrgencyLevel.WARNING);
        long safe = counts.get(UrgencyLevel.SAFE);

        // the 8 products that run out first (products nobody buys have no days-left figure)
        List<Map<String, Object>> daysLeft = new ArrayList<>();
        for (ReorderSuggestion s : list) {
            if (daysLeft.size() >= 8) break;
            if (s.getDaysLeft() == Double.MAX_VALUE) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", s.getProduct().getName());
            m.put("daysLeft", round2(s.getDaysLeft()));
            m.put("status", s.getUrgency().name());
            daysLeft.add(m);
        }

        // best sellers of the period
        List<Map<String, Object>> top = new ArrayList<>();
        for (Map.Entry<String, Integer> e : app.reports.topSellers(days, today, 8)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", e.getKey());
            m.put("units", e.getValue());
            m.put("sharePct", unitsSold == 0 ? 0.0 : round1(e.getValue() * 100.0 / unitsSold));
            top.add(m);
        }

        // products that still have stock but sold nothing for deadDays
        List<Map<String, Object>> dead = new ArrayList<>();
        int deadUnits = 0;
        for (Product p : app.reports.deadStock(deadDays, today)) {
            LocalDate last = null;
            for (SaleRecord r : app.manager.getSalesFor(p.getId())) {
                if (last == null || r.getDate().isAfter(last)) last = r.getDate();
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("category", AppContext.categoryOf(p));
            m.put("stock", p.getCurrentStock());
            m.put("lastSale", last);
            dead.add(m);
            deadUnits += p.getCurrentStock();
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", days);
        out.put("deadDays", deadDays);
        out.put("unitsSold", unitsSold);
        out.put("avgPerDay", round1(unitsSold / (double) days));
        out.put("totalProducts", products.size());
        out.put("totalStock", products.stream().mapToInt(Product::getCurrentStock).sum());
        out.put("critical", critical);
        out.put("warning", warning);
        out.put("safe", safe);
        out.put("healthPct", products.isEmpty() ? 100.0 : round1(safe * 100.0 / products.size()));
        out.put("daysLeft", daysLeft);
        out.put("topSellers", top);
        out.put("deadStock", dead);
        out.put("deadStockUnits", deadUnits);
        return out;
    }
}