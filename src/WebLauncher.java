import service.DataStore;
import service.Database;
import service.FileHandler;
import service.JdbcRepository;
import web.AppContext;
import web.WebServer;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import java.awt.Desktop;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Paths;

public class WebLauncher {
    private static final int PORT = 8080;
    private static final long AUTO_SAVE_MILLIS = 10_000;
    private static final String ADDRESS = "http://localhost:" + PORT;

    public static void main(String[] args) throws Exception {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // the default look is fine too
        }

        // 0. is the program (or something else) already using the port?
        if (!portFree(PORT)) {
            ask("Port " + PORT + " is already in use.\n"
                            + "Smart Inventory is probably already running, so it will be opened in your browser.\n"
                            + "(If another program uses the port, change PORT in WebLauncher.)",
                    "Already running", "OK");
            openBrowser();
            System.exit(0);
        }

        // 1. where should the data live?
        int choice = ask("Where should the inventory be stored?",
                "Smart Inventory - storage", "SQLite database", "CSV files");
        if (choice < 0) {
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
            ask("Could not load saved data:\n" + e.getMessage(), "Load error", "OK");
            System.exit(1);
            return;
        }
        if (loaded == 0 && importSource != null) {
            int found = app.countIn(importSource);
            if (found > 0) {
                int answer = ask("The " + store.getName() + " is empty, but " + found
                                + " products were found in the " + importSource.getName()
                                + ".\nImport them now?",
                        "Import existing data", "Yes, import", "No, start empty");
                if (answer == 0) {
                    app.importFrom(importSource);
                }
            }
        }
        app.startAutoSave(AUTO_SAVE_MILLIS);

        // 3. start the web server
        WebServer server = new WebServer(PORT, Paths.get("web"), app);
        server.start();

        System.out.println("Smart Inventory web app is running at " + ADDRESS);
        System.out.println("Storage: " + store.getName());
        System.out.println("Stop it with Ctrl+C in the terminal (this saves the data first).");

        // stop the server and save the data when the program is closed
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            app.shutdown();
        }, "web-shutdown"));

        // 4. open the browser
        openBrowser();
    }

    // Shows a question with buttons that ALWAYS appears on top. Returns the index of the
    // chosen button, or -1 if the dialog was closed without choosing.
    private static int ask(String message, String title, String... options) {
        JOptionPane pane = new JOptionPane(message, JOptionPane.QUESTION_MESSAGE,
                JOptionPane.DEFAULT_OPTION, null, options, options[0]);
        JDialog dialog = pane.createDialog(null, title);
        dialog.setAlwaysOnTop(true);   // never hide behind VS Code
        dialog.setVisible(true);       // waits here until the user answers
        dialog.dispose();

        Object value = pane.getValue();
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(value)) return i;
        }
        return -1;
    }

    // Tries to open the port: if that works, nobody else is using it
    private static boolean portFree(int port) {
        try (ServerSocket socket = new ServerSocket(port, 1, InetAddress.getByName("localhost"))) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void openBrowser() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(ADDRESS));
            }
        } catch (Exception e) {
            System.out.println("Could not open the browser. Open " + ADDRESS + " yourself.");
        }
    }
}