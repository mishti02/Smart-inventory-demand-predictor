import gui.MainFrame;
import gui.UiStyle;
import service.DataStore;
import service.Database;
import service.FileHandler;
import service.JdbcRepository;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class Launcher {
    public static void main(String[] args) {
        UiStyle.install();   // FlatLaf plus the rounded, blue look

        SwingUtilities.invokeLater(() -> {
            String[] options = {"SQLite database", "CSV files"};
            int choice = JOptionPane.showOptionDialog(null,
                    "Where should the inventory be stored?",
                    "Smart Inventory - storage",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, options, options[0]);

            if (choice == JOptionPane.CLOSED_OPTION) {
                System.exit(0);   // the user closed the dialog, so just quit
            }

            DataStore csv = new FileHandler("data");
            DataStore store;
            DataStore importSource;

            if (choice == 0) {
                store = new JdbcRepository(new Database("data/inventory.db"));
                importSource = csv;      // an empty database can import the old CSV data
            } else {
                store = csv;
                importSource = null;
            }

            new MainFrame(store, importSource).setVisible(true);
        });
    }
}