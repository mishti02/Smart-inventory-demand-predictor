package service;

import exception.InsufficientStockException;
import exception.InvalidInputException;
import model.Product;
import model.SaleRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class SalesSimulator {
    private final InventoryManager manager;
    private final Runnable onSaleProcessed;   // tells the screen that something changed
    private final Random random = new Random();

    private Thread producer;
    private Thread consumer;

    public SalesSimulator(InventoryManager manager, Runnable onSaleProcessed) {
        this.manager = manager;
        this.onSaleProcessed = onSaleProcessed;
    }

    public synchronized boolean isRunning() {
        return producer != null;
    }

    public synchronized void start() {
        if (isRunning()) return;

        SalesQueue queue = new SalesQueue(3);   // a fresh, empty queue for every run
        producer = new Thread(() -> produce(queue), "Producer");
        consumer = new Thread(() -> consume(queue), "Consumer");
        producer.setDaemon(true);
        consumer.setDaemon(true);
        consumer.start();   // the consumer goes first and waits on the empty queue
        producer.start();
    }

    public synchronized void stop() {
        if (!isRunning()) return;
        producer.interrupt();   // wakes a sleeping or waiting thread with an InterruptedException
        consumer.interrupt();
        producer = null;
        consumer = null;
    }

    // ---------- the two jobs ----------

    private void produce(SalesQueue queue) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                List<Product> inStock = manager.getAllProducts().stream()
                        .filter(p -> p.getCurrentStock() > 0)
                        .collect(Collectors.toList());

                if (inStock.isEmpty()) {
                    System.out.println("[Producer] nothing in stock to sell, waiting...");
                    Thread.sleep(2000);
                    continue;
                }

                Product p = inStock.get(random.nextInt(inStock.size()));
                int qty = 1 + random.nextInt(3);   // 1 to 3 units
                queue.put(new SaleRequest(p.getId(), qty));
                System.out.println("[Producer] queued " + qty + " x " + p.getName()
                        + " (queue size " + queue.size() + ")");

                Thread.sleep(300 + random.nextInt(500));   // producer is fast
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("[Producer] stopped");
    }

    private void consume(SalesQueue queue) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                SaleRequest request = queue.take();
                Product p = manager.getProduct(request.getProductId());
                String name = (p == null) ? "product " + request.getProductId() : p.getName();

                try {
                    manager.recordSale(request.getProductId(), LocalDate.now(), request.getQuantity());
                    System.out.println("[Consumer] sold " + request.getQuantity() + " x " + name);
                    onSaleProcessed.run();
                } catch (InsufficientStockException | InvalidInputException e) {
                    System.out.println("[Consumer] skipped: " + e.getMessage());
                }

                Thread.sleep(1000);   // the consumer is slower, so the queue can fill up
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("[Consumer] stopped");
    }
}