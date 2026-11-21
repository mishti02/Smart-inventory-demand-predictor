package gui;

import service.AutoSaver;
import service.DataStore;
import service.InventoryManager;
import service.SalesSimulator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class MainFrame extends JFrame {
    private static final String[] PAGES =
            {"Dashboard", "Products", "Record Sales", "Predictions", "Reports"};

    private static final long AUTO_SAVE_MILLIS = 10_000;   // 10 seconds (use 60_000 later)

    private final InventoryManager manager = new InventoryManager();
    private final DataStore store;          // WHERE the data lives (CSV or database): MainFrame does not care
    private final DataStore importSource;   // an older store to copy from when "store" is empty (or null)
    private final SalesSimulator simulator = new SalesSimulator(manager, this::onSimulatedSale);
    private final JButton simButton = new JButton("Start live simulation");

    // volatile: the auto-save thread must always see the latest value written by the screen thread
    private volatile boolean dataLoaded = true;
    private Thread autoSaveThread;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(cardLayout);
    private final Map<String, JLabel> navItems = new LinkedHashMap<>();
    private String activePage = "";

    private DashboardPanel dashboardPanel;
    private ProductPanel productPanel;
    private SalesPanel salesPanel;
    private PredictionPanel predictionPanel;
    private ReportPanel reportPanel;

    // The store is handed IN from outside ("dependency injection"), so this class never
    // needs to know whether it is talking to files or to a database
    public MainFrame(DataStore store, DataStore importSource) {
        super("Smart Inventory Demand Predictor  |  " + store.getName());
        this.store = store;
        this.importSource = importSource;

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1200, 750);
        setMinimumSize(new Dimension(1000, 600));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // stop the background threads and save when the window is closed
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                simulator.stop();
                if (autoSaveThread != null) autoSaveThread.interrupt();
                saveData();
            }
        });

        loadData();   // must happen before the pages are built

        add(buildSidebar(), BorderLayout.WEST);

        dashboardPanel = new DashboardPanel(manager);
        productPanel = new ProductPanel(manager);
        salesPanel = new SalesPanel(manager);
        predictionPanel = new PredictionPanel(manager);
        reportPanel = new ReportPanel(manager);

        contentPanel.setBackground(Theme.BACKGROUND);
        for (String page : PAGES) {
            JComponent panel;
            switch (page) {
                case "Dashboard":    panel = dashboardPanel;  break;
                case "Products":     panel = productPanel;    break;
                case "Record Sales": panel = salesPanel;      break;
                case "Predictions":  panel = predictionPanel; break;
                case "Reports":      panel = reportPanel;     break;
                default:             panel = buildPlaceholder(page); break;
            }
            contentPanel.add(panel, page);
        }
        add(contentPanel, BorderLayout.CENTER);

        showPage("Dashboard");

        // start the background thread last, when everything else is ready
        autoSaveThread = AutoSaver.start(this::saveQuietly, AUTO_SAVE_MILLIS);
    }

    // ---------- saving and loading ----------

    private void loadData() {
        try {
            int loaded = store.load(manager);   // POLYMORPHISM: CSV or database, decided at run time
            if (loaded == 0 && importSource != null) {
                offerImport();
            }
        } catch (IOException ex) {
            dataLoaded = false;
            JOptionPane.showMessageDialog(null,
                    "Could not load saved data from " + store.getName() + ":\n" + ex.getMessage(),
                    "Load error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // The chosen store is empty. If the old store has data, offer to copy it over.
    private void offerImport() throws IOException {
        InventoryManager peek = new InventoryManager();
        int found = importSource.load(peek);   // only LOOKS: the real manager is untouched
        if (found == 0) return;

        int answer = JOptionPane.showConfirmDialog(null,
                "The " + store.getName() + " is empty, but " + found + " products were found in the "
                        + importSource.getName() + ".\nImport them now?",
                "Import existing data", JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION) {
            importSource.load(manager);   // now load them for real
            store.save(manager);          // and write them into the new store
        }
    }

    // Used by the screen thread: shows a pop-up if saving fails
    private void saveData() {
        if (!dataLoaded) return;   // protect the old data if loading failed
        try {
            store.save(manager);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not save data to " + store.getName() + ":\n" + ex.getMessage(),
                    "Save error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Used by the background thread: Swing windows must only be touched by the
    // screen thread, so here an error is just printed to the console
    private void saveQuietly() {
        if (!dataLoaded) return;
        try {
            store.save(manager);
        } catch (IOException ex) {
            System.err.println("[AutoSaver] save failed: " + ex.getMessage());
        }
    }

    // ---------- live simulation ----------

    private void toggleSimulation() {
        if (simulator.isRunning()) {
            simulator.stop();
            simButton.setText("Start live simulation");
            simButton.setBackground(Theme.BLUE);
            simButton.setForeground(Color.BLACK);
        } else {
            simulator.start();
            simButton.setText("Stop live simulation");
            simButton.setBackground(Theme.CRITICAL);
            simButton.setForeground(Color.WHITE);
        }
    }

    // Runs on the CONSUMER thread after each sale. Swing components may only be
    // changed by the screen thread, so invokeLater hands the work over to it.
    private void onSimulatedSale() {
        SwingUtilities.invokeLater(this::refreshLivePages);
    }

    private void refreshLivePages() {
        switch (activePage) {
            case "Dashboard":    dashboardPanel.refresh();    break;
            case "Record Sales": salesPanel.refresh();        break;
            case "Predictions":  predictionPanel.calculate(); break;
            case "Reports":      reportPanel.refresh();       break;
            default:             break;   // Products is left alone so typing is not interrupted
        }
    }

    // ---------- layout ----------

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(Theme.SIDEBAR);
        sidebar.setPreferredSize(new Dimension(230, 0));
        sidebar.setBorder(BorderFactory.createEmptyBorder(28, 16, 24, 16));

        JLabel brand = new JLabel("Smart Inventory");
        brand.setFont(Theme.BRAND);
        brand.setForeground(Color.WHITE);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        brand.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));
        sidebar.add(brand);
        sidebar.add(Box.createRigidArea(new Dimension(0, 30)));

        for (String name : PAGES) {
            JLabel item = new JLabel(name);
            item.setFont(Theme.NAV);
            item.setForeground(Theme.SIDEBAR_TEXT);
            item.setOpaque(true);
            item.setBackground(Theme.SIDEBAR);
            item.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
            item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            item.setAlignmentX(Component.LEFT_ALIGNMENT);
            item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            item.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    showPage(name);
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    if (!name.equals(activePage)) item.setBackground(Theme.SIDEBAR_HOVER);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    if (!name.equals(activePage)) item.setBackground(Theme.SIDEBAR);
                }
            });

            navItems.put(name, item);
            sidebar.add(item);
            sidebar.add(Box.createRigidArea(new Dimension(0, 6)));
        }

        // the simulation button sits at the bottom of the sidebar
        sidebar.add(Box.createVerticalGlue());
        simButton.setFont(Theme.BODY);
        simButton.setBackground(Theme.BLUE);
        simButton.setForeground(Color.BLACK);
        simButton.setFocusPainted(false);
        simButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        simButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        simButton.addActionListener(e -> toggleSimulation());
        sidebar.add(simButton);

        return sidebar;
    }

    private JPanel buildPlaceholder(String title) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(32, 36, 32, 36));

        JLabel heading = new JLabel(title);
        heading.setFont(Theme.TITLE);
        panel.add(heading, BorderLayout.NORTH);

        JLabel note = new JLabel("This page will be built in a later phase.");
        note.setFont(Theme.BODY);
        panel.add(note, BorderLayout.CENTER);
        return panel;
    }

    private void showPage(String name) {
        saveData();   // quick save every time the user changes page

        // refresh pages whose data may have changed on another page
        if (name.equals("Dashboard")) dashboardPanel.refresh();
        if (name.equals("Products")) productPanel.refreshTable();
        if (name.equals("Record Sales")) salesPanel.refresh();
        if (name.equals("Predictions")) predictionPanel.calculate();
        if (name.equals("Reports")) reportPanel.refresh();

        cardLayout.show(contentPanel, name);
        activePage = name;

        for (Map.Entry<String, JLabel> entry : navItems.entrySet()) {
            boolean active = entry.getKey().equals(name);
            entry.getValue().setBackground(active ? Theme.ACTIVE : Theme.SIDEBAR);
            entry.getValue().setForeground(active ? Color.BLACK : Theme.SIDEBAR_TEXT);
        }
    }
}