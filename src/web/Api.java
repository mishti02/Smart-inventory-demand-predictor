package web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import model.Product;
import model.ReorderSuggestion;
import model.SaleRecord;
import model.UrgencyLevel;
import service.PredictionStrategy;
import service.SimpleAverageStrategy;
import service.WeightedAverageStrategy;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

// The JSON endpoints. Each one turns the results of the service classes into Maps and Lists,
// and Json.stringify turns those into text.
public class Api {
    private static final int DEFAULT_WINDOW = 7;
    private static final int DEFAULT_SAFETY = 2;
    private static final int DEFAULT_COVER = 14;

    private final AppContext app;

    public Api(AppContext app) {
        this.app = app;
    }

    public void register(HttpServer server) {
        get(server, "/api/dashboard", this::dashboard);
        get(server, "/api/products", this::products);
        get(server, "/api/sales", this::sales);
        get(server, "/api/predictions", this::predictions);
        get(server, "/api/categories", this::categories);
        get(server, "/api/reorders", this::reorders);
    }

    // ---------- plumbing ----------

    @FunctionalInterface
    private interface Endpoint {
        Object handle(Map<String, String> params) throws Exception;
    }

    private void get(HttpServer server, String path, Endpoint endpoint) {
        server.createContext(path, ex -> {
            WebServer.log(ex);
            try {
                if (!"GET".equals(ex.getRequestMethod())) {
                    sendJson(ex, 405, error("Use GET for " + path));
                    return;
                }
                sendJson(ex, 200, endpoint.handle(query(ex)));
            } catch (Exception e) {
                System.err.println("[Web] error in " + path + ": " + e);
                sendJson(ex, 500, error("Server error: " + e.getMessage()));
            }
        });
    }

    private static void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        WebServer.sendText(ex, status, "application/json; charset=utf-8", Json.stringify(body));
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", message);
        return m;
    }

    // Reads ?name=value&other=value into a Map
    private static Map<String, String> query(HttpExchange ex) {
        Map<String, String> map = new HashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
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

    // A whole number from the query, kept between min and max. A missing or bad value gives the fallback.
    private static int intParam(Map<String, String> params, String name, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(params.getOrDefault(name, "").trim());
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static PredictionStrategy strategy(Map<String, String> params, int window) {
        return "weighted".equalsIgnoreCase(params.get("method"))
                ? new WeightedAverageStrategy(window)
                : new SimpleAverageStrategy(window);
    }

    private static double round2(double x) {
        return Math.round(x * 100.0) / 100.0;
    }

    private static double share(int part, int total) {
        return total == 0 ? 0.0 : round2(part * 100.0 / total);
    }

    private static double pctChange(int current, int previous) {
        if (previous == 0) return current == 0 ? 0 : 100;
        return round2((current - previous) * 100.0 / previous);
    }

    // ---------- endpoints ----------

    // GET /api/dashboard
    private Object dashboard(Map<String, String> params) {
        LocalDate today = LocalDate.now();
        List<Product> products = app.manager.getAllProducts();
        List<ReorderSuggestion> list =
                app.predictor.generateSuggestions(DEFAULT_WINDOW, DEFAULT_SAFETY, DEFAULT_COVER, today);
        Map<UrgencyLevel, Long> counts = app.reports.countByUrgency(list);

        int sold7 = app.analytics.unitsBetween(today.minusDays(6), today);
        int prev7 = app.analytics.unitsBetween(today.minusDays(13), today.minusDays(7));
        long critical = counts.get(UrgencyLevel.CRITICAL);
        long warning = counts.get(UrgencyLevel.WARNING);
        long safe = counts.get(UrgencyLevel.SAFE);

        Map<String, Object> cards = new LinkedHashMap<>();
        cards.put("totalProducts", products.size());
        cards.put("categories", products.stream().map(AppContext::categoryOf).distinct().count());
        cards.put("totalStock", products.stream().mapToInt(Product::getCurrentStock).sum());
        cards.put("unitsSold7", sold7);
        cards.put("avgPerDay", round2(sold7 / 7.0));
        cards.put("unitsSoldPrev7", prev7);
        cards.put("soldChangePct", pctChange(sold7, prev7));
        cards.put("critical", critical);
        cards.put("warning", warning);
        cards.put("safe", safe);
        cards.put("needAttention", critical + warning);
        cards.put("reorderItems", app.reports.itemsToReorder(list));
        cards.put("reorderUnits", app.reports.totalUnitsToReorder(list));
        cards.put("runningOutIn3Days", app.reports.countRunningOutWithin(list, 3));

        List<Map<String, Object>> daily = new ArrayList<>();
        for (Map.Entry<LocalDate, Integer> e : app.analytics.dailyUnits(7, today).entrySet()) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("date", e.getKey());
            d.put("label", e.getKey().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            d.put("units", e.getValue());
            daily.add(d);
        }

        List<Map<String, Object>> urgent = new ArrayList<>();
        int rank = 1;
        for (ReorderSuggestion s : app.reports.topUrgent(list, 5)) {
            urgent.add(suggestionMap(s, rank++));
        }

        List<Map<String, Object>> topSellers = new ArrayList<>();
        for (Map.Entry<String, Integer> e : app.reports.topSellers(DEFAULT_WINDOW, today, 5)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", e.getKey());
            m.put("units", e.getValue());
            topSellers.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("storage", app.storageName());
        out.put("today", today);
        out.put("cards", cards);
        out.put("daily", daily);
        out.put("topUrgent", urgent);
        out.put("topSellers", topSellers);
        out.put("recentSales", saleMaps(app.analytics.recentSales(5)));
        return out;
    }

    // GET /api/products
    private Object products(Map<String, String> params) {
        LocalDate today = LocalDate.now();
        Map<Integer, ReorderSuggestion> byId = new HashMap<>();
        for (ReorderSuggestion s :
                app.predictor.generateSuggestions(DEFAULT_WINDOW, DEFAULT_SAFETY, DEFAULT_COVER, today)) {
            byId.put(s.getProduct().getId(), s);
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (Product pr : app.manager.getAllProducts()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", pr.getId());
            m.put("name", pr.getName());
            m.put("category", AppContext.categoryOf(pr));
            m.put("stock", pr.getCurrentStock());
            m.put("leadTimeDays", pr.getLeadTimeDays());
            m.put("soldLast7", app.analytics.unitsBetween(pr.getId(), today.minusDays(6), today));

            ReorderSuggestion s = byId.get(pr.getId());
            if (s != null) {
                m.put("avgDaily", round2(s.getAvgDailySales()));
                m.put("daysLeft", s.getDaysLeft() == Double.MAX_VALUE ? null : round2(s.getDaysLeft()));
                m.put("status", s.getUrgency().name());
                m.put("reorderQty", s.getSuggestedQty());
            }
            items.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", items.size());
        out.put("items", items);
        return out;
    }

    // GET /api/sales?limit=50
    private Object sales(Map<String, String> params) {
        int limit = intParam(params, "limit", 50, 1, 500);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalRecords", app.analytics.countSales());
        out.put("items", saleMaps(app.analytics.recentSales(limit)));
        return out;
    }

    // GET /api/predictions?window=7&method=simple&safety=2&cover=14
    private Object predictions(Map<String, String> params) {
        int window = intParam(params, "window", DEFAULT_WINDOW, 0, 90);   // 0 = all history
        int safety = intParam(params, "safety", DEFAULT_SAFETY, 0, 60);
        int cover = intParam(params, "cover", DEFAULT_COVER, 1, 120);
        PredictionStrategy strategy = strategy(params, window);

        List<ReorderSuggestion> list =
                app.predictor.generateSuggestions(strategy, safety, cover, LocalDate.now());
        Map<UrgencyLevel, Long> counts = app.reports.countByUrgency(list);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", list.size());
        summary.put("critical", counts.get(UrgencyLevel.CRITICAL));
        summary.put("warning", counts.get(UrgencyLevel.WARNING));
        summary.put("safe", counts.get(UrgencyLevel.SAFE));

        List<Map<String, Object>> items = new ArrayList<>();
        int rank = 1;
        for (ReorderSuggestion s : list) {
            items.add(suggestionMap(s, rank++));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("window", window);
        out.put("method", strategy.getName());
        out.put("safetyDays", safety);
        out.put("coverDays", cover);
        out.put("summary", summary);
        out.put("items", items);
        return out;
    }

    // GET /api/categories?days=14
    private Object categories(Map<String, String> params) {
        int days = intParam(params, "days", 14, 1, 365);
        LocalDate today = LocalDate.now();
        List<Product> products = app.manager.getAllProducts();
        Map<String, Integer> soldByCategory = app.reports.unitsByCategory(days, today);

        int totalStock = products.stream().mapToInt(Product::getCurrentStock).sum();
        int totalSold = soldByCategory.values().stream().mapToInt(Integer::intValue).sum();

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map.Entry<String, Integer> e : soldByCategory.entrySet()) {
            String category = e.getKey();
            List<Product> inCategory = products.stream()
                    .filter(pr -> AppContext.categoryOf(pr).equals(category))
                    .collect(Collectors.toList());
            int stock = inCategory.stream().mapToInt(Product::getCurrentStock).sum();

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category", category);
            m.put("products", inCategory.size());
            m.put("stock", stock);
            m.put("stockSharePct", share(stock, totalStock));
            m.put("unitsSold", e.getValue());
            m.put("salesSharePct", share(e.getValue(), totalSold));
            items.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", days);
        out.put("totalStock", totalStock);
        out.put("totalUnitsSold", totalSold);
        out.put("items", items);
        return out;
    }

    // GET /api/reorders?window=7&method=simple&safety=2&cover=14
    private Object reorders(Map<String, String> params) {
        int window = intParam(params, "window", DEFAULT_WINDOW, 0, 90);
        int safety = intParam(params, "safety", DEFAULT_SAFETY, 0, 60);
        int cover = intParam(params, "cover", DEFAULT_COVER, 1, 120);

        List<ReorderSuggestion> all = app.predictor.generateSuggestions(
                strategy(params, window), safety, cover, LocalDate.now());

        List<Map<String, Object>> items = new ArrayList<>();
        int rank = 1;
        int units = 0;
        for (ReorderSuggestion s : all) {
            if (s.getSuggestedQty() > 0) {
                items.add(suggestionMap(s, rank++));
                units += s.getSuggestedQty();
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", items.size());
        out.put("totalUnits", units);
        out.put("safetyDays", safety);
        out.put("coverDays", cover);
        out.put("items", items);
        return out;
    }

    // ---------- shared builders ----------

    private Map<String, Object> suggestionMap(ReorderSuggestion s, int rank) {
        Product p = s.getProduct();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rank", rank);
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("category", AppContext.categoryOf(p));
        m.put("stock", p.getCurrentStock());
        m.put("leadTimeDays", p.getLeadTimeDays());
        m.put("avgDaily", round2(s.getAvgDailySales()));
        m.put("daysLeft", s.getDaysLeft() == Double.MAX_VALUE ? null : round2(s.getDaysLeft()));
        m.put("status", s.getUrgency().name());
        m.put("reorderQty", s.getSuggestedQty());
        return m;
    }

    private List<Map<String, Object>> saleMaps(List<SaleRecord> records) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (SaleRecord r : records) {
            Product pr = app.manager.getProduct(r.getProductId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", r.getDate());
            m.put("productId", r.getProductId());
            m.put("product", pr == null ? "(deleted)" : pr.getName());
            m.put("category", pr == null ? "" : AppContext.categoryOf(pr));
            m.put("quantity", r.getQuantitySold());
            items.add(m);
        }
        return items;
    }
}