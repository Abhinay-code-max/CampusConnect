package com.campusconnect.service;

import com.campusconnect.exception.EventConflictException;
import com.campusconnect.model.EventData;
import java.time.OffsetDateTime;

public final class EventEditPolicy {
    private EventEditPolicy() { }
    public static void validate(EventData old,String newCode,String newMode,OffsetDateTime open,OffsetDateTime close,
                                OffsetDateTime starts,OffsetDateTime ends,Integer capacity,int registrations,
                                int confirmed,int scores,int registrationsOutsideWindow) {
        if("CANCELLED".equals(old.eventStatus())||"COMPLETED".equals(old.eventStatus()))
            throw new EventConflictException("Terminal events cannot be edited");
        if(!old.eventCode().equals(newCode))throw new EventConflictException("Event code cannot be changed");
        if(registrations>0&&!old.participationMode().equals(newMode))
            throw new EventConflictException("Participation mode cannot change after registrations exist");
        if(capacity!=null&&capacity<confirmed)
            throw new EventConflictException("Capacity cannot be lower than confirmed registrations");
        boolean scheduleChanged=!old.registrationOpens().isEqual(open)||!old.registrationCloses().isEqual(close)
                ||!old.startsAt().isEqual(starts)||!old.endsAt().isEqual(ends);
        if(scores>0&&scheduleChanged)throw new EventConflictException("Event schedule cannot change after scores are submitted");
        if(registrationsOutsideWindow>0)throw new EventConflictException("Proposed dates conflict with existing registrations");
    }
}
