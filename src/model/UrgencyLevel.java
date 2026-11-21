package model;

public enum UrgencyLevel {
    CRITICAL, WARNING, SAFE;

    // <= 3 days left = CRITICAL, <= 7 = WARNING, otherwise SAFE
    public static UrgencyLevel fromDaysLeft(double daysLeft) {
        if (daysLeft <= 3) return CRITICAL;
        if (daysLeft <= 7) return WARNING;
        return SAFE;
    }
}