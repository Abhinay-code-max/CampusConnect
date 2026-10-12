package com.campusconnect.service;

import com.campusconnect.exception.EventConflictException;

public final class EventLifecycle {
    private EventLifecycle() { }
    public static String nextStatus(String current,String action) {
        if("publish".equals(action)&&"DRAFT".equals(current))return "PUBLISHED";
        if("cancel".equals(action)&&("DRAFT".equals(current)||"PUBLISHED".equals(current)))return "CANCELLED";
        if("close-registration".equals(action)&&"PUBLISHED".equals(current))return "PUBLISHED";
        if("publish".equals(action))throw new EventConflictException("Only draft events can be published");
        if("cancel".equals(action))throw new EventConflictException("Only draft or published events can be cancelled");
        if("close-registration".equals(action))throw new EventConflictException("Only published events can close registration");
        throw new EventConflictException("Unsupported event status transition");
    }
}
