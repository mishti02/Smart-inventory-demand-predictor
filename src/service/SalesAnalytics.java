package service;

import model.Product;
import model.SaleRecord;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Questions about sales over time, answered with streams
public class SalesAnalytics {
    private final InventoryManager manager;

    public SalesAnalytics(InventoryManager manager) {
        this.manager = manager;
    }

    // Units sold on each of the last N days (oldest first), all products together.
    // Days with no sales are included with 0, so a chart never has gaps.
    public Map<LocalDate, Integer> dailyUnits(int days, LocalDate today) {
        Map<LocalDate, Integer> result = new LinkedHashMap<>();
        for (int i = days - 1; i >= 0; i--) {
            result.put(today.minusDays(i), 0);
        }
        for (Product p : manager.getAllProducts()) {
            for (SaleRecord r : manager.getSalesFor(p.getId())) {
                result.computeIfPresent(r.getDate(), (date, total) -> total + r.getQuantitySold());
            }
        }
        return result;
    }

    // Units sold by ALL products between two dates (both days included)
    public int unitsBetween(LocalDate from, LocalDate to) {
        return manager.getAllProducts().stream()
                .mapToInt(p -> unitsBetween(p.getId(), from, to))
                .sum();
    }

    // Units sold by ONE product between two dates (both days included)
    public int unitsBetween(int productId, LocalDate from, LocalDate to) {
        return manager.getSalesFor(productId).stream()
                .filter(r -> !r.getDate().isBefore(from) && !r.getDate().isAfter(to))
                .mapToInt(SaleRecord::getQuantitySold)
                .sum();
    }

    // The newest sales across all products
    public List<SaleRecord> recentSales(int limit) {
        return manager.getAllProducts().stream()
                .flatMap(p -> manager.getSalesFor(p.getId()).stream())
                .sorted(Comparator.comparing(SaleRecord::getDate).reversed())
                .limit(Math.max(0, limit))
                .collect(Collectors.toList());
    }

    public int countSales() {
        return manager.getAllProducts().stream()
                .mapToInt(p -> manager.getSalesFor(p.getId()).size())
                .sum();
    }
}