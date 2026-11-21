package model;

public class Product implements Comparable<Product> {
    private int id;
    private String name;
    private String category;
    private volatile int currentStock;
    private int leadTimeDays;   // days a supplier takes to deliver

    public Product(int id, String name, String category, int currentStock, int leadTimeDays) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.currentStock = currentStock;
        this.leadTimeDays = leadTimeDays;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public int getCurrentStock() { return currentStock; }
    public int getLeadTimeDays() { return leadTimeDays; }

    public void setName(String name) { this.name = name; }
    public void setCategory(String category) { this.category = category; }
    public void setCurrentStock(int currentStock) { this.currentStock = currentStock; }
    public void setLeadTimeDays(int leadTimeDays) { this.leadTimeDays = leadTimeDays; }

    public void addStock(int qty) { currentStock += qty; }
    public void reduceStock(int qty) { currentStock -= qty; }

    // The "natural order" of products: alphabetical by name, ignoring capital letters.
    // Negative = this comes first, zero = same, positive = this comes after.
    @Override
    public int compareTo(Product other) {
        return this.name.compareToIgnoreCase(other.name);
    }

    @Override
    public String toString() {
        return name + " (stock: " + currentStock + ")";
    }
}