package com.campusconnect.controller;

import com.campusconnect.model.EventData;
import com.campusconnect.model.EventPage;
import java.util.LinkedHashMap;
import java.util.Map;

final class EventJson {
    private EventJson() { }
    static Map<String,Object> page(EventPage page) {
        Map<String,Object> result=new LinkedHashMap<>();result.put("success",true);
        result.put("events",page.events().stream().map(EventJson::event).toList());
        result.put("page",page.page());result.put("pageSize",page.pageSize());result.put("total",page.total());
        result.put("totalPages",page.total()==0?0:(page.total()+page.pageSize()-1)/page.pageSize());return result;
    }
    static Map<String,Object> event(EventData e) {
        Map<String,Object> v=new LinkedHashMap<>();v.put("eventId",e.eventId());v.put("eventCode",e.eventCode());v.put("eventName",e.eventName());
        v.put("description",e.description());v.put("participationMode",e.participationMode());
        v.put("eventStatus",e.eventStatus());v.put("registrationOpens",e.registrationOpens().toString());
        v.put("registrationCloses",e.registrationCloses().toString());v.put("startsAt",e.startsAt().toString());v.put("endsAt",e.endsAt().toString());
        v.put("capacity",e.capacity());v.put("location",e.location());v.put("createdAt",e.createdAt().toString());
        v.put("registrationCount",e.registrationCount());v.put("confirmedCount",e.confirmedCount());v.put("registrationOpen",e.registrationOpen());return v;
    }
    static Map<String,Object> success(EventData event) {return Map.of("success",true,"event",event(event));}
}
