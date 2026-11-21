package model;

import java.time.LocalDate;

public class SaleRecord {
    private final int productId;
    private final LocalDate date;
    private final int quantitySold;

    public SaleRecord(int productId, LocalDate date, int quantitySold) {
        this.productId = productId;
        this.date = date;
        this.quantitySold = quantitySold;
    }

    public int getProductId() { return productId; }
    public LocalDate getDate() { return date; }
    public int getQuantitySold() { return quantitySold; }
}