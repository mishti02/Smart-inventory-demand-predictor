package service;

import model.SaleRequest;

import java.util.LinkedList;
import java.util.Queue;

// A bounded buffer shared by the producer and the consumer thread.
// Both methods are synchronized, so they use this object's lock and monitor.
public class SalesQueue {
    private final Queue<SaleRequest> items = new LinkedList<>();
    private final int capacity;

    public SalesQueue(int capacity) {
        this.capacity = capacity;
    }

    // Called by the PRODUCER
    public synchronized void put(SaleRequest request) throws InterruptedException {
        while (items.size() == capacity) {          // guarded block: wait until there is space
            System.out.println("[Producer] queue is full, waiting...");
            wait();                                 // releases the lock and sleeps until notified
        }
        items.add(request);
        notifyAll();                                // wake up a consumer that may be waiting
    }

    // Called by the CONSUMER
    public synchronized SaleRequest take() throws InterruptedException {
        while (items.isEmpty()) {                   // guarded block: wait until there is something
            wait();
        }
        SaleRequest request = items.remove();
        notifyAll();                                // wake up a producer that may be waiting for space
        return request;
    }

    public synchronized int size() {
        return items.size();
    }
}