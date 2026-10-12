package com.campusconnect.model;

import java.time.OffsetDateTime;

public record EventSearch(int page, int pageSize, String query, String participationMode,
                          OffsetDateTime startsAfter, OffsetDateTime startsBefore,
                          Boolean registrationOpen, String sort, String direction) { }
