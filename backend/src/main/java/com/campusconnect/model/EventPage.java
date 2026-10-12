package com.campusconnect.model;

import java.util.List;

public record EventPage(List<EventData> events, int page, int pageSize, long total) { }
