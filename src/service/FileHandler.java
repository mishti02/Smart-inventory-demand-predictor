package service;

import model.Product;
import model.SaleRecord;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class FileHandler implements DataStore {
    private final Path folder;
    private final Path productsFile;
    private final Path salesFile;

    public FileHandler(String folderName) {
        this.folder = Paths.get(folderName);
        this.productsFile = folder.resolve("products.csv");
        this.salesFile = folder.resolve("sales.csv");
    }

        @Override
    public String getName() {
        return "CSV files";
    }

    // ---------- save ----------

    // synchronized: the auto-save thread and the screen (page switch, window close)
    // can both call save(), but never write the files at the same time
    public synchronized void save(InventoryManager manager) throws IOException {
        Files.createDirectories(folder);   // makes the data folder if it is missing

        StringBuilder products = new StringBuilder("id,name,category,stock,leadTimeDays\n");
        StringBuilder sales = new StringBuilder("productId,date,quantity\n");

        // synchronized BLOCK: freeze the manager while we take a snapshot, so stock
        // and sales in the files always belong to the same moment
        synchronized (manager) {
            for (Product p : manager.getAllProducts()) {
                products.append(p.getId()).append(',')
                        .append(clean(p.getName())).append(',')
                        .append(clean(p.getCategory())).append(',')
                        .append(p.getCurrentStock()).append(',')
                        .append(p.getLeadTimeDays()).append('\n');

                for (SaleRecord r : manager.getSalesFor(p.getId())) {
                    sales.append(r.getProductId()).append(',')
                            .append(r.getDate()).append(',')
                            .append(r.getQuantitySold()).append('\n');
                }
            }
        }   // lock released: the slow file writing below does not block the app

        writeText(productsFile, products.toString());
        writeText(salesFile, sales.toString());
    }

    private void writeText(Path file, String text) throws IOException {
        try (BufferedWriter writer =
                     new BufferedWriter(new FileWriter(file.toFile(), StandardCharsets.UTF_8))) {
            writer.write(text);
        }   // the file is closed automatically here, even if an error happens
    }

    // Commas and line breaks would break the CSV format, so replace them with spaces
    private String clean(String text) {
        if (text == null) return "";
        return text.replace(',', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    // ---------- load ----------

    // Returns how many products were loaded (0 if there is no saved file yet)
    public int load(InventoryManager manager) throws IOException {
        if (!Files.exists(productsFile)) return 0;

        List<Product> products = new ArrayList<>();
        List<SaleRecord> sales = new ArrayList<>();

        try (BufferedReader reader =
                     new BufferedReader(new FileReader(productsFile.toFile(), StandardCharsets.UTF_8))) {
            reader.readLine();   // skip the header line
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split(",");
                try {
                    products.add(new Product(
                            Integer.parseInt(parts[0].trim()),
                            parts[1].trim(),
                            parts[2].trim(),
                            Integer.parseInt(parts[3].trim()),
                            Integer.parseInt(parts[4].trim())));
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    System.err.println("Skipped bad product line: " + line);
                }
            }
        }

        if (Files.exists(salesFile)) {
            try (BufferedReader reader =
                         new BufferedReader(new FileReader(salesFile.toFile(), StandardCharsets.UTF_8))) {
                reader.readLine();   // skip the header line
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    String[] parts = line.split(",");
                    try {
                        sales.add(new SaleRecord(
                                Integer.parseInt(parts[0].trim()),
                                LocalDate.parse(parts[1].trim()),
                                Integer.parseInt(parts[2].trim())));
                    } catch (NumberFormatException | DateTimeParseException
                             | ArrayIndexOutOfBoundsException e) {
                        System.err.println("Skipped bad sales line: " + line);
                    }
                }
            }
        }

        // only touch the manager after both files were read successfully
        manager.clearAll();
        for (Product p : products) manager.loadProduct(p);
        for (SaleRecord r : sales) manager.loadSale(r);
        return products.size();
    }
}