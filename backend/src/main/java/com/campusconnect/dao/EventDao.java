package com.campusconnect.dao;

import com.campusconnect.model.EventData;
import com.campusconnect.model.EventPage;
import com.campusconnect.model.EventSearch;
import com.campusconnect.service.EventInputValidator.ValidEvent;

public interface EventDao {
    EventPage listPublic(EventSearch search);
    EventData findPublic(long eventId);
    EventPage listManaged(long actorId, boolean admin, EventSearch search);
    EventData findManaged(long actorId, boolean admin, long eventId);
    EventData create(long actorId, ValidEvent event);
    EventData update(long actorId, boolean admin, long eventId, ValidEvent event);
    EventData transition(long actorId, boolean admin, long eventId, String action);
}
