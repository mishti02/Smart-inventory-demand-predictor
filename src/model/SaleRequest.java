package model;

// One pending sale waiting in the queue
public class SaleRequest {
    private final int productId;
    private final int quantity;

    public SaleRequest(int productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public int getProductId() { return productId; }
    public int getQuantity() { return quantity; }
}