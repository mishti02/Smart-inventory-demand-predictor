package service;

import model.SaleRecord;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

// Recent days count more. Today has weight N, yesterday N-1, ... the oldest day has weight 1.
public class WeightedAverageStrategy extends WindowStrategy {

    public WeightedAverageStrategy(int windowDays) {
        super(windowDays);
    }

    @Override
    protected double calculate(List<SaleRecord> sales, LocalDate today, int days) {
        double weightedSum = 0;
        for (SaleRecord r : sales) {
            long daysAgo = ChronoUnit.DAYS.between(r.getDate(), today);
            if (daysAgo >= 0 && daysAgo < days) {
                weightedSum += (double) r.getQuantitySold() * (days - daysAgo);
            }
        }
        double weightTotal = days * (days + 1) / 2.0;   // 1 + 2 + ... + days
        return weightedSum / weightTotal;
    }

    @Override
    public String getName() {
        return "Weighted average (" + windowText() + ")";
    }
}