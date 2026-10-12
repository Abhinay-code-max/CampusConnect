package com.campusconnect.model;

import java.time.OffsetDateTime;

public record EventData(long eventId, String eventCode, String eventName, String description,
                        long ownerUserId, String participationMode, String eventStatus,
                        OffsetDateTime registrationOpens, OffsetDateTime registrationCloses,
                        OffsetDateTime startsAt, OffsetDateTime endsAt, Integer capacity,
                        String location, OffsetDateTime createdAt, int registrationCount,
                        int confirmedCount, boolean registrationOpen) { }
