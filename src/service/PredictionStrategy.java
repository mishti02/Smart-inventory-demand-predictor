package service;

import model.SaleRecord;

import java.time.LocalDate;
import java.util.List;

// An interface: it says WHAT must be done, not HOW.
// Every class that implements it must provide both methods.
public interface PredictionStrategy {
    double averageDailySales(List<SaleRecord> sales, LocalDate today);

    String getName();
}