package service;

import exception.InsufficientStockException;
import exception.InvalidInputException;
import model.Product;
import model.SaleRecord;

import java.time.LocalDate;
import java.util.*;

// Every public method is synchronized: only ONE thread at a time can be inside
// any of them (they all share the lock of this InventoryManager object).
public class InventoryManager {
    private final Map<Integer, Product> products = new LinkedHashMap<>();
    private final Map<Integer, List<SaleRecord>> sales = new HashMap<>();
    private int nextId = 1;

    public synchronized Product addProduct(String name, String category, int stock, int leadTimeDays)
            throws InvalidInputException {
        if (name == null || name.trim().isEmpty())
            throw new InvalidInputException("Product name cannot be empty.");
        if (stock < 0)
            throw new InvalidInputException("Stock cannot be negative.");
        if (leadTimeDays < 0)
            throw new InvalidInputException("Lead time cannot be negative.");

        Product p = new Product(nextId++, name.trim(), category, stock, leadTimeDays);
        products.put(p.getId(), p);
        sales.put(p.getId(), new ArrayList<>());
        return p;
    }

    public synchronized void updateProduct(int id, String name, String category, int stock, int leadTimeDays)
            throws InvalidInputException {
        Product p = getProductOrThrow(id);
        if (name == null || name.trim().isEmpty())
            throw new InvalidInputException("Product name cannot be empty.");
        if (stock < 0 || leadTimeDays < 0)
            throw new InvalidInputException("Stock and lead time cannot be negative.");
        p.setName(name.trim());
        p.setCategory(category);
        p.setCurrentStock(stock);
        p.setLeadTimeDays(leadTimeDays);
    }

    public synchronized void deleteProduct(int id) throws InvalidInputException {
        getProductOrThrow(id);
        products.remove(id);
        sales.remove(id);
    }

    public synchronized void restock(int id, int qty) throws InvalidInputException {
        Product p = getProductOrThrow(id);
        if (qty <= 0)
            throw new InvalidInputException("Restock quantity must be positive.");
        p.addStock(qty);
    }

    // The check "enough stock?" and the change "reduce stock" must happen together
    // with no other thread in between. That is the critical section.
    public synchronized void recordSale(int id, LocalDate date, int qty)
            throws InvalidInputException, InsufficientStockException {
        Product p = getProductOrThrow(id);
        if (qty <= 0)
            throw new InvalidInputException("Quantity must be positive.");
        if (date == null)
            throw new InvalidInputException("Date is required.");
        if (qty > p.getCurrentStock())
            throw new InsufficientStockException(
                    "Only " + p.getCurrentStock() + " units of " + p.getName() + " in stock.");

        p.reduceStock(qty);
        sales.get(id).add(new SaleRecord(id, date, qty));
    }

    // ---------- used when loading saved data from files ----------

    public synchronized void clearAll() {
        products.clear();
        sales.clear();
        nextId = 1;
    }

    // Adds a product that already has an ID (from a file)
    public synchronized void loadProduct(Product p) {
        products.put(p.getId(), p);
        sales.putIfAbsent(p.getId(), new ArrayList<>());
        if (p.getId() >= nextId) nextId = p.getId() + 1;
    }

    // Adds an old sale WITHOUT reducing stock (the saved stock is already reduced)
    public synchronized void loadSale(SaleRecord r) {
        List<SaleRecord> list = sales.get(r.getProductId());
        if (list != null) list.add(r);
    }

    // ---------- reading ----------

    public synchronized Product getProduct(int id) { return products.get(id); }

    public synchronized List<Product> getAllProducts() { return new ArrayList<>(products.values()); }

    // Returns a COPY, so another thread adding a sale cannot break a loop that is reading the list
    public synchronized List<SaleRecord> getSalesFor(int id) {
        List<SaleRecord> list = sales.get(id);
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    private Product getProductOrThrow(int id) throws InvalidInputException {
        Product p = products.get(id);
        if (p == null) throw new InvalidInputException("No product with ID " + id);
        return p;
    }
}