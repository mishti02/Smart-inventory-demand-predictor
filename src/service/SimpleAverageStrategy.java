package service;

import model.SaleRecord;

import java.time.LocalDate;
import java.util.List;

// Every day counts equally: total units sold divided by the number of days
public class SimpleAverageStrategy extends WindowStrategy {

    public SimpleAverageStrategy(int windowDays) {
        super(windowDays);   // calls the constructor of the parent class
    }

    @Override
    protected double calculate(List<SaleRecord> sales, LocalDate today, int days) {
        LocalDate start = today.minusDays(days - 1);
        int total = 0;
        for (SaleRecord r : sales) {
            boolean inRange = !r.getDate().isBefore(start) && !r.getDate().isAfter(today);
            if (inRange) total += r.getQuantitySold();
        }
        return (double) total / days;
    }

    @Override
    public String getName() {
        return "Simple average (" + windowText() + ")";
    }
}