package service;

import java.time.LocalTime;

// A Runnable: the JOB that a background thread will perform
// Implements Runnable means this class is a job that a thread can run. 
// Runnable has one method, run(), and we must write it.
public class AutoSaver implements Runnable {
    private final Runnable saveAction;
    private final long intervalMillis;

    public AutoSaver(Runnable saveAction, long intervalMillis) {
        this.saveAction = saveAction; // The code that actually does the saving
        this.intervalMillis = intervalMillis; 
    }

    @Override
    public void run() {
        System.out.println("[AutoSaver] started on thread: " + Thread.currentThread().getName());
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(intervalMillis);          // thread state: TIMED_WAITING
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();    // restore the flag, then leave the loop
                break;
            }
            saveAction.run();
            System.out.println("[AutoSaver] saved at " + LocalTime.now().withNano(0));
        }
        System.out.println("[AutoSaver] stopped");
    }

    // Creates the Thread, marks it as a daemon, starts it, and returns it
    public static Thread start(Runnable saveAction, long intervalMillis) {
        Thread t = new Thread(new AutoSaver(saveAction, intervalMillis), "AutoSaver");
        t.setDaemon(true);   // a daemon thread never keeps the program alive after the window closes
        t.start();           // start() creates the new thread, which then calls run()
        return t;
    }
}