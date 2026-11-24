package web;

import model.Product;
import service.AutoSaver;
import service.DataStore;
import service.DemandPredictor;
import service.InventoryManager;
import service.ReportService;
import service.SalesAnalytics;
import service.SalesSimulator;

import java.io.IOException;

// Everything the web layer needs, in one place. The web layer only talks to this class,
// and this class only uses the service classes you already built and tested.
public class AppContext {
    public final InventoryManager manager = new InventoryManager();
    public final DataStore store;
    public final DemandPredictor predictor;
    public final ReportService reports;
    public final SalesAnalytics analytics;

    // the live simulation: the auto-save thread stores whatever it records
    public final SalesSimulator simulator = new SalesSimulator(manager, () -> { });

    // volatile: the auto-save thread must always see the latest value
    private volatile boolean dataLoaded = true;
    private Thread autoSaveThread;
    private volatile long autoSaveMillis = 0;

    public AppContext(DataStore store) {
        this.store = store;
        this.predictor = new DemandPredictor(manager);
        this.reports = new ReportService(manager);
        this.analytics = new SalesAnalytics(manager);
    }

    // Loads the saved data. Returns how many products were found (0 = nothing saved yet).
    public int load() throws IOException {
        try {
            return store.load(manager);
        } catch (IOException e) {
            dataLoaded = false;   // never overwrite the saved data after a failed load
            throw e;
        }
    }

    // How many products another store holds, WITHOUT touching our data
    public int countIn(DataStore other) throws IOException {
        InventoryManager peek = new InventoryManager();
        return other.load(peek);
    }

    // Copies everything from another store into ours and saves it
    public void importFrom(DataStore other) throws IOException {
        other.load(manager);
        store.save(manager);
    }

        public void startAutoSave(long millis) {
        autoSaveMillis = millis;
        autoSaveThread = AutoSaver.start(this::saveQuietly, millis);
    }

    public long autoSaveMillis() {
        return autoSaveMillis;
    }

    public void save() throws IOException {
        if (!dataLoaded) return;
        store.save(manager);
    }

    public void saveQuietly() {
        try {
            save();
        } catch (IOException e) {
            System.err.println("[Save] failed: " + e.getMessage());
        }
    }

    public void shutdown() {
        simulator.stop();
        if (autoSaveThread != null) autoSaveThread.interrupt();
        saveQuietly();
    }

    public String storageName() {
        return store.getName();
    }

    public static String categoryOf(Product p) {
        String c = p.getCategory();
        return (c == null || c.isBlank()) ? "General" : c;
    }
}