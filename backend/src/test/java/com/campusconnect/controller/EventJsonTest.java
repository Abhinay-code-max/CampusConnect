package com.campusconnect.controller;

import static org.junit.jupiter.api.Assertions.*;
import com.campusconnect.model.EventData;
import com.campusconnect.model.EventPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventJsonTest {
    @Test void eventListingConvertsJavaTimesToJsonStrings() throws Exception {
        OffsetDateTime time=OffsetDateTime.parse("2026-11-01T10:00:00Z");
        EventData event=new EventData(17,"P8TEST","Test event",null,4,"INDIVIDUAL","DRAFT",
                time,time.plusDays(1),time.plusDays(2),time.plusDays(3),null,"Room A",time,0,0,false);
        String json=new ObjectMapper().writeValueAsString(EventJson.page(new EventPage(List.of(event),1,10,1)));
        assertTrue(json.contains("\"eventId\":17"));
        assertTrue(json.contains("\"startsAt\":\"2026-11-03T10:00Z\""));
        assertTrue(json.contains("\"eventStatus\":\"DRAFT\""));
    }
}
