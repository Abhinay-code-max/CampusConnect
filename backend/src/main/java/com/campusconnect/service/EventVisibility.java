package com.campusconnect.service;

public final class EventVisibility {
    private EventVisibility() { }
    public static boolean isPublic(String status) { return "PUBLISHED".equals(status)||"CANCELLED".equals(status)||"COMPLETED".equals(status); }
}
