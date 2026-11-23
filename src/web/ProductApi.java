package web;

import com.sun.net.httpserver.HttpServer;
import exception.InvalidInputException;
import model.Product;

import java.util.LinkedHashMap;
import java.util.Map;

// The endpoints that CHANGE products. They use POST because they change data.
public class ProductApi {
    private final AppContext app;

    public ProductApi(AppContext app) {
        this.app = app;
    }

    @FunctionalInterface
    private interface Action {
        Map<String, Object> handle(Map<String, String> form) throws Exception;
    }

    public void register(HttpServer server) {
        post(server, "/api/products/add", this::add);
        post(server, "/api/products/update", this::update);
        post(server, "/api/products/delete", this::delete);
        post(server, "/api/products/restock", this::restock);
    }

    // ---------- plumbing ----------

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
                Map<String, Object> result = action.handle(HttpUtil.formBody(ex));
                app.saveQuietly();   // save after EVERY change, so nothing is lost if the program is stopped
                HttpUtil.sendJson(ex, 200, result);
            } catch (InvalidInputException e) {
                // the user typed something wrong: a 400 with a message the page can show
                HttpUtil.sendJson(ex, 400, HttpUtil.single("error", e.getMessage()));
            } catch (Exception e) {
                System.err.println("[Web] error in " + path + ": " + e);
                HttpUtil.sendJson(ex, 500, HttpUtil.single("error", "Server error: " + e.getMessage()));
            }
        });
    }

    // ---------- the four actions ----------

    private Map<String, Object> add(Map<String, String> form) throws InvalidInputException {
        Product p = app.manager.addProduct(
                form.getOrDefault("name", ""),
                category(form),
                HttpUtil.requireInt(form, "stock", "Stock"),
                HttpUtil.requireInt(form, "leadTimeDays", "Lead time"));
        return done("Added " + p.getName(), p);
    }

    private Map<String, Object> update(Map<String, String> form) throws InvalidInputException {
        int id = HttpUtil.requireInt(form, "id", "Product ID");
        app.manager.updateProduct(
                id,
                form.getOrDefault("name", ""),
                category(form),
                HttpUtil.requireInt(form, "stock", "Stock"),
                HttpUtil.requireInt(form, "leadTimeDays", "Lead time"));
        Product p = app.manager.getProduct(id);
        return done("Updated " + (p == null ? "product" : p.getName()), p);
    }

    private Map<String, Object> delete(Map<String, String> form) throws InvalidInputException {
        int id = HttpUtil.requireInt(form, "id", "Product ID");
        Product before = app.manager.getProduct(id);
        String name = (before == null) ? "Product" : before.getName();
        app.manager.deleteProduct(id);   // throws InvalidInputException if the id does not exist
        return done("Deleted " + name, null);
    }

    private Map<String, Object> restock(Map<String, String> form) throws InvalidInputException {
        int id = HttpUtil.requireInt(form, "id", "Product ID");
        int quantity = HttpUtil.requireInt(form, "quantity", "Quantity");
        app.manager.restock(id, quantity);
        Product p = app.manager.getProduct(id);
        return done("Added " + quantity + " units to " + (p == null ? "product" : p.getName()), p);
    }

    // ---------- helpers ----------

    private static String category(Map<String, String> form) {
        String c = form.getOrDefault("category", "").trim();
        return c.isEmpty() ? "General" : c;
    }

    private static Map<String, Object> done(String message, Product p) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("message", message);
        if (p != null) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("category", AppContext.categoryOf(p));
            m.put("stock", p.getCurrentStock());
            m.put("leadTimeDays", p.getLeadTimeDays());
            out.put("product", m);
        }
        return out;
    }
}