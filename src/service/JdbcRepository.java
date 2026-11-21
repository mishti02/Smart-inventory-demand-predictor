package service;

import model.Product;
import model.SaleRecord;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcRepository implements DataStore {
    private static final String PRODUCT_COLUMNS = "id, name, category, stock, lead_time_days";

    private final Database db;

    public JdbcRepository(Database db) {
        this.db = db;
    }

    // ================= CREATE =================

    public void insertProduct(Product p) throws SQLException {
        String sql = "INSERT INTO products(" + PRODUCT_COLUMNS + ") VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindProduct(ps, p);
            ps.executeUpdate();
        }
    }

    public void insertSale(SaleRecord r) throws SQLException {
        String sql = "INSERT INTO sales(product_id, sale_date, quantity) VALUES (?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, r.getProductId());
            ps.setString(2, r.getDate().toString());   // saved as text like 2026-11-20
            ps.setInt(3, r.getQuantitySold());
            ps.executeUpdate();
        }
    }

    // ================= READ =================

    public List<Product> findAllProducts() throws SQLException {
        String sql = "SELECT " + PRODUCT_COLUMNS + " FROM products ORDER BY id";
        List<Product> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {          // the cursor starts BEFORE the first row
                list.add(mapProduct(rs));
            }
        }
        return list;
    }

    public Optional<Product> findProduct(int id) throws SQLException {
        String sql = "SELECT " + PRODUCT_COLUMNS + " FROM products WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapProduct(rs)) : Optional.empty();
            }
        }
    }

    // The text the user typed is passed as DATA, so it can never change the SQL itself
    public List<Product> findProductsByName(String text) throws SQLException {
        String sql = "SELECT " + PRODUCT_COLUMNS + " FROM products WHERE name LIKE ? ORDER BY id";
        List<Product> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + text + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapProduct(rs));
            }
        }
        return list;
    }

    public List<SaleRecord> findSalesFor(int productId) throws SQLException {
        String sql = "SELECT product_id, sale_date, quantity FROM sales "
                + "WHERE product_id = ? ORDER BY sale_date, id";
        List<SaleRecord> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapSale(rs));
            }
        }
        return list;
    }

    public List<SaleRecord> findAllSales() throws SQLException {
        String sql = "SELECT product_id, sale_date, quantity FROM sales ORDER BY sale_date, id";
        List<SaleRecord> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapSale(rs));
        }
        return list;
    }

    // The database does the adding (SUM). COALESCE turns "no rows" (NULL) into 0.
    public int totalSold(int productId, LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT COALESCE(SUM(quantity), 0) FROM sales "
                + "WHERE product_id = ? AND sale_date BETWEEN ? AND ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setString(2, from.toString());
            ps.setString(3, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // ================= UPDATE =================

    // Returns true if a row was changed, false if no product has that id
    public boolean updateProduct(Product p) throws SQLException {
        String sql = "UPDATE products SET name = ?, category = ?, stock = ?, lead_time_days = ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.getName());
            ps.setString(2, categoryOf(p));
            ps.setInt(3, p.getCurrentStock());
            ps.setInt(4, p.getLeadTimeDays());
            ps.setInt(5, p.getId());
            return ps.executeUpdate() > 0;   // executeUpdate returns the number of rows changed
        }
    }

    // ================= DELETE =================

    // The product's sales are deleted by the database itself (ON DELETE CASCADE)
    public boolean deleteProduct(int id) throws SQLException {
        String sql = "DELETE FROM products WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    // ================= DataStore: save and load everything =================

    // synchronized: the auto-save thread and the screen thread must never write at the same time
    @Override
    public synchronized void save(InventoryManager manager) throws IOException {
        try (Connection conn = db.getConnection()) {
            db.createTables();
            conn.setAutoCommit(false);   // start a TRANSACTION: all the changes below succeed together or not at all
            try {
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate("DELETE FROM sales");
                    st.executeUpdate("DELETE FROM products");
                }

                String productSql = "INSERT INTO products(" + PRODUCT_COLUMNS + ") VALUES (?, ?, ?, ?, ?)";
                String saleSql = "INSERT INTO sales(product_id, sale_date, quantity) VALUES (?, ?, ?)";

                try (PreparedStatement productStmt = conn.prepareStatement(productSql);
                     PreparedStatement saleStmt = conn.prepareStatement(saleSql)) {

                    synchronized (manager) {   // freeze the data so products and sales match
                        for (Product p : manager.getAllProducts()) {
                            bindProduct(productStmt, p);
                            productStmt.addBatch();   // collect the row now, send it later

                            for (SaleRecord r : manager.getSalesFor(p.getId())) {
                                saleStmt.setInt(1, r.getProductId());
                                saleStmt.setString(2, r.getDate().toString());
                                saleStmt.setInt(3, r.getQuantitySold());
                                saleStmt.addBatch();
                            }
                        }
                    }
                    productStmt.executeBatch();   // products first, because sales point to them
                    saleStmt.executeBatch();
                }
                conn.commit();       // make everything permanent
            } catch (SQLException e) {
                conn.rollback();     // undo everything: the old data stays safe
                throw e;
            }
        } catch (SQLException e) {
            throw new IOException("Database save failed: " + e.getMessage(), e);
        }
    }

    @Override
    public int load(InventoryManager manager) throws IOException {
        try {
            db.createTables();
            List<Product> products = findAllProducts();
            if (products.isEmpty()) return 0;   // nothing saved yet: leave the manager alone

            List<SaleRecord> sales = findAllSales();

            // only touch the manager after everything was read successfully
            manager.clearAll();
            for (Product p : products) manager.loadProduct(p);
            for (SaleRecord r : sales) manager.loadSale(r);
            return products.size();
        } catch (SQLException e) {
            throw new IOException("Database load failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getName() {
        return "SQLite database";
    }

    // ================= helpers =================

    private void bindProduct(PreparedStatement ps, Product p) throws SQLException {
        ps.setInt(1, p.getId());
        ps.setString(2, p.getName());
        ps.setString(3, categoryOf(p));
        ps.setInt(4, p.getCurrentStock());
        ps.setInt(5, p.getLeadTimeDays());
    }

    private String categoryOf(Product p) {
        return p.getCategory() == null ? "General" : p.getCategory();
    }

    // Turns the CURRENT row of the ResultSet into a Product object (object mapping)
    private Product mapProduct(ResultSet rs) throws SQLException {
        return new Product(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("category"),
                rs.getInt("stock"),
                rs.getInt("lead_time_days"));
    }

    private SaleRecord mapSale(ResultSet rs) throws SQLException {
        String text = rs.getString("sale_date");
        try {
            return new SaleRecord(rs.getInt("product_id"), LocalDate.parse(text), rs.getInt("quantity"));
        } catch (DateTimeParseException e) {
            throw new SQLException("Bad date in database: " + text, e);
        }
    }
}