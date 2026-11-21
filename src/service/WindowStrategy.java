package service;

import model.SaleRecord;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

// An abstract class: it holds the part that is the SAME for every strategy
// (how many days to look at) and leaves the part that DIFFERS (calculate) to the subclasses.
public abstract class WindowStrategy implements PredictionStrategy {
    protected final int windowDays;   // 0 means "all history"

    protected WindowStrategy(int windowDays) {
        this.windowDays = windowDays;
    }

    // The number of days the average is spread over
    protected int effectiveDays(List<SaleRecord> sales, LocalDate today) {
        if (windowDays > 0) return windowDays;

        LocalDate earliest = sales.get(0).getDate();
        for (SaleRecord r : sales) {
            if (r.getDate().isBefore(earliest)) earliest = r.getDate();
        }
        return Math.max(1, (int) ChronoUnit.DAYS.between(earliest, today) + 1);
    }

    // The shared steps (a "template"): handle empty history, find the days, then
    // call calculate(), which each subclass writes in its own way
    @Override
    public double averageDailySales(List<SaleRecord> sales, LocalDate today) {
        if (sales.isEmpty()) return 0;
        return calculate(sales, today, effectiveDays(sales, today));
    }

    protected abstract double calculate(List<SaleRecord> sales, LocalDate today, int days);

    protected String windowText() {
        return windowDays > 0 ? "last " + windowDays + " days" : "all history";
    }
}