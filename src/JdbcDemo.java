import exception.InsufficientStockException;
import exception.InvalidInputException;
import model.Product;
import model.SaleRecord;
import service.Database;
import service.InventoryManager;
import service.JdbcRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class JdbcDemo {
    public static void main(String[] args) {
        try {
            Files.deleteIfExists(Paths.get("data/demo.db"));   // start from an empty demo database
            Database db = new Database("data/demo.db");
            db.createTables();
            JdbcRepository repo = new JdbcRepository(db);
            LocalDate today = LocalDate.of(2026, 11, 20);
            int[] milkSales = {6, 5, 7, 6, 5, 7, 6};   // total 42

            // ---------- CREATE ----------
            repo.insertProduct(new Product(1, "Milk 1L", "Dairy", 8, 3));
            repo.insertProduct(new Product(2, "Rice 5kg", "Grocery", 28, 5));
            repo.insertProduct(new Product(3, "Soap", "Personal", 79, 4));
            for (int i = 0; i < 7; i++) {
                repo.insertSale(new SaleRecord(1, today.minusDays(6 - i), milkSales[i]));
            }
            repo.insertSale(new SaleRecord(3, today, 3));
            repo.insertSale(new SaleRecord(3, today.minusDays(1), 2));
            System.out.println("CREATE : 3 products and 9 sales inserted");

            // ---------- READ ----------
            System.out.println();
            System.out.println("READ all products:");
            for (Product p : repo.findAllProducts()) {
                System.out.println("  " + p.getId() + "  " + p.getName() + "  stock " + p.getCurrentStock());
            }

            int sold = repo.totalSold(1, today.minusDays(6), today);
            System.out.println();
            System.out.printf("SUM done by SQL: Milk sold %d units in 7 days -> avg %.2f per day%n",
                    sold, sold / 7.0);

            // ---------- UPDATE ----------
            Product milk = repo.findProduct(1).orElseThrow();
            milk.addStock(100);
            boolean changed = repo.updateProduct(milk);
            System.out.println();
            System.out.println("UPDATE : restocked Milk by 100, row changed = " + changed
                    + ", stock now " + repo.findProduct(1).orElseThrow().getCurrentStock());

            // ---------- SEARCH ----------
            System.out.println();
            System.out.println("SEARCH 'ilk' : " + names(repo.findProductsByName("ilk")));

            // ---------- SQL INJECTION ----------
            String evil = "x' OR '1'='1";
            System.out.println();
            System.out.println("SQL INJECTION test, the user types: " + evil);
            System.out.println("  unsafe (text glued into the SQL) : " + unsafeSearch(db, evil));
            System.out.println("  safe   (PreparedStatement)       : " + names(repo.findProductsByName(evil)));

            // ---------- DELETE ----------
            System.out.println();
            System.out.println("Soap has " + repo.findSalesFor(3).size() + " sales before the delete");
            boolean deleted = repo.deleteProduct(3);
            System.out.println("DELETE : Soap deleted = " + deleted + ", its sales now = "
                    + repo.findSalesFor(3).size() + " (removed by ON DELETE CASCADE)");
            System.out.println("         products left = " + repo.findAllProducts().size());

            // ---------- ROUND TRIP: save and load a whole InventoryManager ----------
            System.out.println();
            System.out.println("ROUND TRIP with InventoryManager:");
            InventoryManager m1 = new InventoryManager();
            Product a = m1.addProduct("Milk 1L", "Dairy", 50, 3);
            Product b = m1.addProduct("Rice 5kg", "Grocery", 60, 5);
            for (int i = 0; i < 7; i++) {
                m1.recordSale(a.getId(), today.minusDays(6 - i), milkSales[i]);
            }
            m1.recordSale(b.getId(), today, 4);
            repo.save(m1);
            System.out.println("  saved  : 2 products, "
                    + (m1.getSalesFor(a.getId()).size() + m1.getSalesFor(b.getId()).size()) + " sales");

            InventoryManager m2 = new InventoryManager();
            int loaded = repo.load(m2);
            System.out.println("  loaded : " + loaded + " products");
            for (Product p : m2.getAllProducts()) {
                System.out.println("    " + p.getId() + "  " + p.getName() + "  stock " + p.getCurrentStock()
                        + "  sales rows " + m2.getSalesFor(p.getId()).size());
            }

        } catch (SQLException | IOException e) {
            System.out.println("Error: " + e.getMessage());
        } catch (InvalidInputException | InsufficientStockException e) {
            System.out.println("Rule broken: " + e.getMessage());
        }
    }

    private static List<String> names(List<Product> products) {
        return products.stream().map(Product::getName).collect(Collectors.toList());
    }

    // DELIBERATELY UNSAFE: the typed text is glued straight into the SQL. Never write code like this.
    private static List<String> unsafeSearch(Database db, String name) throws SQLException {
        String sql = "SELECT name FROM products WHERE name = '" + name + "' ORDER BY id";
        System.out.println("  SQL that was run                 : " + sql);
        List<String> result = new ArrayList<>();
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) result.add(rs.getString("name"));
        }
        return result;
    }
}