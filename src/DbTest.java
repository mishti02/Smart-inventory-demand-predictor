import service.Database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DbTest {
    public static void main(String[] args) {
        Database db = new Database("data/inventory.db");

        try {
            db.createTables();
            System.out.println("Tables ready in data/inventory.db");
            System.out.println();

            // 1. What exists in the database?
            try (Connection conn = db.getConnection();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT type, name FROM sqlite_master "
                                 + "WHERE name NOT LIKE 'sqlite_%' ORDER BY type DESC, name")) {
                while (rs.next()) {
                    System.out.printf("%-6s %s%n", rs.getString("type"), rs.getString("name"));
                }
            }

            // 2. What columns does each table have?
            printColumns(db, "products");
            printColumns(db, "sales");

            // 3. Do the rules really work? Both of these MUST be rejected.
            System.out.println();
            check(db, "sale for a product that does not exist",
                    "INSERT INTO sales(product_id, sale_date, quantity) VALUES (999, '2026-11-21', 1)");
            check(db, "product with negative stock",
                    "INSERT INTO products(name, category, stock, lead_time_days) VALUES ('Bad', 'X', -5, 1)");

        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }

    private static void printColumns(Database db, String table) throws SQLException {
        System.out.println();
        System.out.println("Columns of " + table + ":");
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                System.out.printf("  %-16s %-8s %s%s%n",
                        rs.getString("name"),
                        rs.getString("type"),
                        rs.getInt("notnull") == 1 ? "NOT NULL " : "",
                        rs.getInt("pk") > 0 ? "PRIMARY KEY" : "");
            }
        }
    }

    private static void check(Database db, String description, String sql) {
        try (Connection conn = db.getConnection();
             Statement st = conn.createStatement()) {
            st.executeUpdate(sql);
            System.out.println("NOT rejected (unexpected): " + description);
        } catch (SQLException e) {
            System.out.println("Rejected as expected: " + description);
            System.out.println("   -> " + e.getMessage());
        }
    }
}