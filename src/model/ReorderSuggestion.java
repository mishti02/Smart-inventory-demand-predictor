package model;

public class ReorderSuggestion {
    private final Product product;
    private final double avgDailySales;
    private final double daysLeft;
    private final int suggestedQty;
    private final UrgencyLevel urgency;
    private final double urgencyScore;   // daysLeft - leadTime (smaller = more urgent)

    public ReorderSuggestion(Product product, double avgDailySales, double daysLeft,
                             int suggestedQty, UrgencyLevel urgency, double urgencyScore) {
        this.product = product;
        this.avgDailySales = avgDailySales;
        this.daysLeft = daysLeft;
        this.suggestedQty = suggestedQty;
        this.urgency = urgency;
        this.urgencyScore = urgencyScore;
    }

    public Product getProduct() { return product; }
    public double getAvgDailySales() { return avgDailySales; }
    public double getDaysLeft() { return daysLeft; }
    public int getSuggestedQty() { return suggestedQty; }
    public UrgencyLevel getUrgency() { return urgency; }
    public double getUrgencyScore() { return urgencyScore; }
}