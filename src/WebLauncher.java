import service.DataStore;
import service.Database;
import service.FileHandler;
import service.JdbcRepository;
import web.AppContext;
import web.WebServer;

import javax.swing.JOptionPane;
import javax.swing.UIManager;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;

public class WebLauncher {
    private static final int PORT = 8080;
    private static final long AUTO_SAVE_MILLIS = 10_000;

    public static void main(String[] args) throws Exception {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // the default look is fine too
        }

        // 1. where should the data live?
        String[] options = {"SQLite database", "CSV files"};
        int choice = JOptionPane.showOptionDialog(null,
                "Where should the inventory be stored?",
                "Smart Inventory - storage",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        if (choice == JOptionPane.CLOSED_OPTION) {
            System.exit(0);   // the dialog was closed, so just quit
        }

        DataStore csv = new FileHandler("data");
        DataStore store = (choice == 0) ? new JdbcRepository(new Database("data/inventory.db")) : csv;
        DataStore importSource = (choice == 0) ? csv : null;

        // 2. load the data (and offer to import the CSV files into an empty database)
        AppContext app = new AppContext(store);
        int loaded;
        try {
            loaded = app.load();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Could not load saved data:\n" + e.getMessage(),
                    "Load error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (loaded == 0 && importSource != null) {
            int found = app.countIn(importSource);
            if (found > 0) {
                int answer = JOptionPane.showConfirmDialog(null,
                        "The " + store.getName() + " is empty, but " + found + " products were found in the "
                                + importSource.getName() + ".\nImport them now?",
                        "Import existing data", JOptionPane.YES_NO_OPTION);
                if (answer == JOptionPane.YES_OPTION) {
                    app.importFrom(importSource);
                }
            }
        }
        app.startAutoSave(AUTO_SAVE_MILLIS);

        // 3. start the web server
        WebServer server = new WebServer(PORT, Paths.get("web"), app);
        server.start();

        String address = "http://localhost:" + PORT;
        System.out.println("Smart Inventory web app is running at " + address);
        System.out.println("Storage: " + store.getName());
        System.out.println("Stop it with Ctrl+C in the terminal (this saves the data first).");

        // stop the server and save the data when the program is closed
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            app.shutdown();
        }, "web-shutdown"));

        // 4. open the browser
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(new URI(address));
        }
    }
}