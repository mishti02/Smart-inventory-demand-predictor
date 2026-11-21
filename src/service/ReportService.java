package service;

import model.Product;
import model.ReorderSuggestion;
import model.SaleRecord;
import model.UrgencyLevel;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class ReportService {
    private final InventoryManager manager;

    public ReportService(InventoryManager manager) {
        this.manager = manager;
    }

    // ---------- used by the Dashboard ----------

    // How many products are CRITICAL, WARNING and SAFE
    public Map<UrgencyLevel, Long> countByUrgency(List<ReorderSuggestion> list) {
        Map<UrgencyLevel, Long> counts = new EnumMap<>(UrgencyLevel.class);
        for (UrgencyLevel level : UrgencyLevel.values()) {
            counts.put(level, list.stream()
                    .filter(s -> s.getUrgency() == level)
                    .count());
        }
        return counts;
    }

    // The list is already sorted by urgency, so the first n are the most urgent
    public List<ReorderSuggestion> topUrgent(List<ReorderSuggestion> list, int n) {
        return ListUtils.topN(list, Comparator.comparingDouble(ReorderSuggestion::getUrgencyScore), n);
    }

    // Products that need a reorder (suggested quantity above 0)
    public long itemsToReorder(List<ReorderSuggestion> list) {
        return list.stream().filter(s -> s.getSuggestedQty() > 0).count();
    }

    public int totalUnitsToReorder(List<ReorderSuggestion> list) {
        return list.stream().mapToInt(ReorderSuggestion::getSuggestedQty).sum();
    }

    public long countRunningOutWithin(List<ReorderSuggestion> list, int days) {
        return list.stream().filter(s -> s.getDaysLeft() <= days).count();
    }

    // Total units sold across all products in the last N days (ending today)
    public int unitsSold(int days, LocalDate today) {
        LocalDate start = today.minusDays(days - 1);
        return manager.getAllProducts().stream()
                .mapToInt(p -> soldInRange(p.getId(), start, today))
                .sum();
    }

    // The product with the most units sold in the last N days (empty if nothing sold)
    public Optional<Map.Entry<String, Integer>> bestSeller(int days, LocalDate today) {
        LocalDate start = today.minusDays(days - 1);
        Map<String, Integer> totals = manager.getAllProducts().stream()
                .collect(Collectors.toMap(
                        Product::getName,
                        p -> soldInRange(p.getId(), start, today),
                        Integer::sum));
        return totals.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .max(Map.Entry.comparingByValue());
    }

    // ---------- used by the Reports page ----------

    // Units sold per category, sorted by category name (TreeMap keeps keys sorted)
    public Map<String, Integer> unitsByCategory(int days, LocalDate today) {
        LocalDate start = today.minusDays(days - 1);
        return manager.getAllProducts().stream()
                .collect(Collectors.groupingBy(
                        p -> (p.getCategory() == null || p.getCategory().isBlank()) ? "General" : p.getCategory(),
                        TreeMap::new,
                        Collectors.summingInt(p -> soldInRange(p.getId(), start, today))));
    }

    // The n best-selling products, highest first (products with 0 sales are left out)
    public List<Map.Entry<String, Integer>> topSellers(int days, LocalDate today, int n) {
        LocalDate start = today.minusDays(days - 1);
        return manager.getAllProducts().stream()
                .map(p -> Map.entry(p.getName(), soldInRange(p.getId(), start, today)))
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    // Products that still have stock but sold nothing in the last N days
    public List<Product> deadStock(int days, LocalDate today) {
        LocalDate start = today.minusDays(days - 1);
        return manager.getAllProducts().stream()
                .filter(p -> p.getCurrentStock() > 0)
                                .filter(p -> soldInRange(p.getId(), start, today) == 0)
                .sorted()
                .collect(Collectors.toList());
    }

    // Builds the text of the exported report. StringBuilder is used because a normal
    // String would create a new object every time we add a line.
    public String buildTextReport(List<ReorderSuggestion> list, int salesDays, int deadDays, LocalDate today) {
        String nl = System.lineSeparator();
        StringBuilder sb = new StringBuilder();

        sb.append("SMART INVENTORY REPORT").append(nl);
        sb.append("Generated on: ").append(today).append(nl);
        sb.append("=".repeat(78)).append(nl).append(nl);

        sb.append("REORDER PRIORITY (most urgent first)").append(nl);
        sb.append(String.format("%-5s %-16s %6s %8s %10s %5s %-9s %8s%n",
                "RANK", "PRODUCT", "STOCK", "AVG/DAY", "DAYS LEFT", "LEAD", "STATUS", "REORDER"));
        int rank = 1;
        for (ReorderSuggestion s : list) {
            Product p = s.getProduct();
            String daysLeft = (s.getDaysLeft() == Double.MAX_VALUE)
                    ? "No sales" : String.format("%.2f", s.getDaysLeft());
            sb.append(String.format("%-5d %-16s %6d %8.2f %10s %5d %-9s %8d%n",
                    rank++, p.getName(), p.getCurrentStock(), s.getAvgDailySales(),
                    daysLeft, p.getLeadTimeDays(), s.getUrgency(), s.getSuggestedQty()));
        }
        sb.append("Total units to reorder: ").append(totalUnitsToReorder(list)).append(nl).append(nl);

        sb.append("UNITS SOLD BY CATEGORY (last ").append(salesDays).append(" days)").append(nl);
        for (Map.Entry<String, Integer> e : unitsByCategory(salesDays, today).entrySet()) {
            sb.append(String.format("%-20s %6d%n", e.getKey(), e.getValue()));
        }
        sb.append(nl);

        sb.append("TOP SELLERS (last ").append(salesDays).append(" days)").append(nl);
        List<Map.Entry<String, Integer>> top = topSellers(salesDays, today, 5);
        if (top.isEmpty()) sb.append("None").append(nl);
        for (Map.Entry<String, Integer> e : top) {
            sb.append(String.format("%-20s %6d%n", e.getKey(), e.getValue()));
        }
        sb.append(nl);

        sb.append("DEAD STOCK (no sales in ").append(deadDays).append(" days)").append(nl);
        List<Product> dead = deadStock(deadDays, today);
        if (dead.isEmpty()) sb.append("None").append(nl);
        for (Product p : dead) {
            sb.append(String.format("%-20s stock %d%n", p.getName(), p.getCurrentStock()));
        }
        return sb.toString();
    }

    private int soldInRange(int productId, LocalDate start, LocalDate end) {
        return manager.getSalesFor(productId).stream()
                .filter(r -> !r.getDate().isBefore(start) && !r.getDate().isAfter(end))
                .mapToInt(SaleRecord::getQuantitySold)
                .sum();
    }
}