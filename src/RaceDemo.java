public class RaceDemo {
    static int unsafeStock = 2_000_000;
    static int safeStock = 2_000_000;
    static final Object LOCK = new Object();

    public static void main(String[] args) throws InterruptedException {
        // Two "cashiers" each sell 1,000,000 units WITHOUT any protection
        Runnable unsafeSeller = () -> {
            for (int i = 0; i < 1_000_000; i++) {
                unsafeStock--;
            }
        };

        // The same job, but each sale happens inside a synchronized block
        Runnable safeSeller = () -> {
            for (int i = 0; i < 1_000_000; i++) {
                synchronized (LOCK) {
                    safeStock--;
                }
            }
        };

        Thread a = new Thread(unsafeSeller, "Cashier-A");
        Thread b = new Thread(unsafeSeller, "Cashier-B");
        a.start();
        b.start();
        a.join();   // wait until both threads have finished
        b.join();

        Thread c = new Thread(safeSeller, "Cashier-C");
        Thread d = new Thread(safeSeller, "Cashier-D");
        c.start();
        d.start();
        c.join();
        d.join();

        System.out.println("Expected final stock : 0");
        System.out.println("WITHOUT synchronized : " + unsafeStock);
        System.out.println("WITH synchronized    : " + safeStock);
    }
}