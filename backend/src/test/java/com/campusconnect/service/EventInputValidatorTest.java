package com.campusconnect.service;

import static org.junit.jupiter.api.Assertions.*;
import com.campusconnect.exception.ValidationException;
import com.campusconnect.model.EventRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class EventInputValidatorTest {
    private static final Clock CLOCK=Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"),ZoneOffset.UTC);
    private EventRequest valid() {
        EventRequest r=new EventRequest();r.setEventCode("FUTURE-01");r.setEventName("Campus event");r.setParticipationMode("individual");
        r.setRegistrationOpens("2030-01-02T09:00:00+05:30");r.setRegistrationCloses("2030-01-03T09:00:00+05:30");
        r.setStartsAt("2030-01-04T09:00:00+05:30");r.setEndsAt("2030-01-04T17:00:00+05:30");r.setCapacity(50);return r;
    }
    @Test void acceptsValidEventAndNormalizesMode(){assertEquals("INDIVIDUAL",EventInputValidator.validate(valid(),CLOCK,true).mode());}
    @Test void rejectsBrokenDateOrdering(){var r=valid();r.setRegistrationCloses("2030-01-05T09:00:00Z");assertThrows(ValidationException.class,()->EventInputValidator.validate(r,CLOCK,true));}
    @Test void requiresOffsetInDateTime(){var r=valid();r.setStartsAt("2030-01-04T09:00:00");assertThrows(ValidationException.class,()->EventInputValidator.validate(r,CLOCK,true));}
    @Test void rejectsInvalidParticipationMode(){var r=valid();r.setParticipationMode("TEAM_OR_INDIVIDUAL");assertThrows(ValidationException.class,()->EventInputValidator.validate(r,CLOCK,true));}
    @Test void boundsPaginationAndSortFields(){assertEquals(100,EventInputValidator.search("2","100",null,null,null,null,null,"startsAt","desc").pageSize());
        assertThrows(ValidationException.class,()->EventInputValidator.search("1","101",null,null,null,null,null,null,null));
        assertThrows(ValidationException.class,()->EventInputValidator.search(null,null,null,null,null,null,null,"event_status desc; DROP TABLE CC_EVENTS",null));}
    @Test void rejectsInvalidDateFilter(){assertThrows(ValidationException.class,()->EventInputValidator.search(null,null,null,null,"tomorrow",null,null,null,null));}
}
