package service;

import java.io.IOException;

// A contract: anything that can load and save the inventory.
// The CSV files and the database will both implement it (Step 5).
public interface DataStore {
    // Returns how many products were loaded (0 if nothing was saved yet)
    int load(InventoryManager manager) throws IOException;

    void save(InventoryManager manager) throws IOException;

    String getName();
}