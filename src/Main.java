import exception.InsufficientStockException;
import exception.InvalidInputException;
import model.Product;
import model.ReorderSuggestion;
import service.DemandPredictor;
import service.InventoryManager;

import java.time.LocalDate;
import java.util.List;

public class Main {
    public static void main(String[] args)
            throws InvalidInputException, InsufficientStockException {

        InventoryManager manager = new InventoryManager();

        Product milk = manager.addProduct("Milk 1L", "Dairy", 50, 3);
        Product rice = manager.addProduct("Rice 5kg", "Grocery", 60, 5);
        Product soap = manager.addProduct("Soap", "Personal", 100, 4);

        LocalDate today = LocalDate.of(2026, 11, 20);

        int[] milkSales = {6, 5, 7, 6, 5, 7, 6};   // total 42
        int[] riceSales = {5, 4, 5, 4, 5, 4, 5};   // total 32
        int[] soapSales = {3, 3, 3, 3, 3, 3, 3};   // total 21

        // i = 0 is 6 days ago, i = 6 is today
        for (int i = 0; i < 7; i++) {
            LocalDate d = today.minusDays(6 - i);
            manager.recordSale(milk.getId(), d, milkSales[i]);
            manager.recordSale(rice.getId(), d, riceSales[i]);
            manager.recordSale(soap.getId(), d, soapSales[i]);
        }

        DemandPredictor predictor = new DemandPredictor(manager);
        List<ReorderSuggestion> list = predictor.generateSuggestions(7, 2, 14, today);

        System.out.printf("%-5s %-10s %6s %8s %10s %5s %-9s %11s%n",
                "RANK", "PRODUCT", "STOCK", "AVG/DAY", "DAYS LEFT", "LEAD", "STATUS", "REORDER QTY");

        int rank = 1;
        for (ReorderSuggestion s : list) {
            Product p = s.getProduct();
            System.out.printf("%-5d %-10s %6d %8.2f %10.2f %5d %-9s %11d%n",
                    rank++, p.getName(), p.getCurrentStock(), s.getAvgDailySales(),
                    s.getDaysLeft(), p.getLeadTimeDays(), s.getUrgency(), s.getSuggestedQty());
        }
    }
}