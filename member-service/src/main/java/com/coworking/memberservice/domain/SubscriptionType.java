package com.coworking.memberservice.domain;

public enum SubscriptionType {
    BASIC(2),
    PRO(5),
    ENTERPRISE(10);

    private final int maxConcurrentBookings;

    SubscriptionType(int maxConcurrentBookings) {
        this.maxConcurrentBookings = maxConcurrentBookings;
    }

    public int getMaxConcurrentBookings() {
        return maxConcurrentBookings;
    }
}
