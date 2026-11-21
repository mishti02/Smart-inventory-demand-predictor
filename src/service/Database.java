package service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

// The only class that knows HOW to reach the database: the URL, the folder, the table layout.
public class Database {
    private final String url;
    private final Path folder;

    public Database(String filePath) {
        this.url = "jdbc:sqlite:" + filePath;
        this.folder = Paths.get(filePath).toAbsolutePath().getParent();
    }

    // Every caller gets its own Connection and must close it (use try-with-resources)
    public Connection getConnection() throws SQLException {
        try {
            if (folder != null) Files.createDirectories(folder);
        } catch (IOException e) {
            throw new SQLException("Cannot create folder " + folder + ": " + e.getMessage(), e);
        }

        Connection conn = DriverManager.getConnection(url);
        try (Statement st = conn.createStatement()) {
            // SQLite ignores foreign keys unless this is switched on for EVERY connection
            st.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException e) {
            conn.close();
            throw e;
        }
        return conn;
    }

    // Safe to call every time the app starts: IF NOT EXISTS skips tables that are already there
    public void createTables() throws SQLException {
        String products = "CREATE TABLE IF NOT EXISTS products ("
                + "id INTEGER PRIMARY KEY, "
                + "name TEXT NOT NULL, "
                + "category TEXT NOT NULL, "
                + "stock INTEGER NOT NULL CHECK (stock >= 0), "
                + "lead_time_days INTEGER NOT NULL CHECK (lead_time_days >= 0))";

        String sales = "CREATE TABLE IF NOT EXISTS sales ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "product_id INTEGER NOT NULL, "
                + "sale_date TEXT NOT NULL, "
                + "quantity INTEGER NOT NULL CHECK (quantity > 0), "
                + "FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE)";

        String index = "CREATE INDEX IF NOT EXISTS idx_sales_product_date "
                + "ON sales(product_id, sale_date)";

        try (Connection conn = getConnection();
             Statement st = conn.createStatement()) {
            st.execute(products);
            st.execute(sales);
            st.execute(index);
        }
    }
}