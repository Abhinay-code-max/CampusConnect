package com.campusconnect.service;

import com.campusconnect.dao.EventDao;
import com.campusconnect.dao.JdbcEventDao;
import com.campusconnect.exception.ApiException;
import com.campusconnect.model.AuthPrincipal;
import com.campusconnect.model.EventData;
import com.campusconnect.model.EventPage;
import com.campusconnect.model.EventRequest;
import com.campusconnect.model.EventSearch;

public final class EventService {
    private final EventDao dao;
    public EventService() { this(new JdbcEventDao()); }
    public EventService(EventDao dao) { this.dao=dao; }

    public EventPage listPublic(EventSearch search) { return dao.listPublic(search); }
    public EventData findPublic(long id) {
        EventData event=dao.findPublic(id);
        if(event==null||!EventVisibility.isPublic(event.eventStatus()))throw new ApiException(404,"Event not found");
        return event;
    }
    public EventPage listManaged(AuthPrincipal actor,EventSearch search) { requireManager(actor);return dao.listManaged(actor.userId(),isAdmin(actor),search); }
    public EventData findManaged(AuthPrincipal actor,long eventId) {
        requireManager(actor);
        return dao.findManaged(actor.userId(),isAdmin(actor),eventId);
    }
    public EventData create(AuthPrincipal actor,EventRequest request) {
        requireManager(actor);
        return dao.create(actor.userId(),EventInputValidator.validate(request));
    }
    public EventData update(AuthPrincipal actor,long id,EventRequest request) {
        requireManager(actor);
        EventInputValidator.ValidEvent validated=EventInputValidator.validate(request);
        return dao.update(actor.userId(),isAdmin(actor),id,validated);
    }
    public EventData transition(AuthPrincipal actor,long id,String action) {
        requireManager(actor);
        if(!java.util.Set.of("publish","close-registration","cancel").contains(action))throw new ApiException(404,"Event action not found");
        return dao.transition(actor.userId(),isAdmin(actor),id,action);
    }
    private static boolean isAdmin(AuthPrincipal actor){return "ADMIN".equals(actor.role());}
    private static void requireManager(AuthPrincipal actor) { EventAccessPolicy.requireManager(actor); }
}
