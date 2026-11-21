package service;

import model.Product;
import model.ReorderSuggestion;
import model.UrgencyLevel;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DemandPredictor {
    private final InventoryManager manager;

    public DemandPredictor(InventoryManager manager) {
        this.manager = manager;
    }

    // ---------- average daily sales ----------

    // Old style call: uses the simple average. (Method OVERLOADING: same name, different parameters.)
    public double avgDailySales(int productId, int windowDays, LocalDate today) {
        return avgDailySales(productId, new SimpleAverageStrategy(windowDays), today);
    }

    // The strategy decides HOW the average is calculated
    public double avgDailySales(int productId, PredictionStrategy strategy, LocalDate today) {
        return strategy.averageDailySales(manager.getSalesFor(productId), today);
    }

    // If nothing sells, stock never runs out -> huge number, avoids divide by zero
    public double daysUntilStockout(int stock, double avgDaily) {
        if (avgDaily == 0) return Double.MAX_VALUE;
        return stock / avgDaily;
    }

    // ---------- suggestions ----------

    // Old style call: the other screens still use this one
    public List<ReorderSuggestion> generateSuggestions(int windowDays, int safetyDays,
                                                       int coverDays, LocalDate today) {
        return generateSuggestions(new SimpleAverageStrategy(windowDays), safetyDays, coverDays, today);
    }

    public List<ReorderSuggestion> generateSuggestions(PredictionStrategy strategy, int safetyDays,
                                                       int coverDays, LocalDate today) {
        List<ReorderSuggestion> result = new ArrayList<>();

        for (Product p : manager.getAllProducts()) {
            double avg = avgDailySales(p.getId(), strategy, today);
            double daysLeft = daysUntilStockout(p.getCurrentStock(), avg);
            UrgencyLevel level = UrgencyLevel.fromDaysLeft(daysLeft);

            boolean needsReorder = daysLeft <= p.getLeadTimeDays() + safetyDays;
            int qty = 0;
            if (needsReorder) {
                double needed = avg * (p.getLeadTimeDays() + coverDays) - p.getCurrentStock();
                qty = (int) Math.max(0, Math.ceil(needed));
            }

            double score = daysLeft - p.getLeadTimeDays();
            result.add(new ReorderSuggestion(p, avg, daysLeft, qty, level, score));
        }

        // smallest score first = most urgent first
        result.sort(Comparator.comparingDouble(ReorderSuggestion::getUrgencyScore));
        return result;
    }
}