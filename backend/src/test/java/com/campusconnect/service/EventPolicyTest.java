package com.campusconnect.service;

import static org.junit.jupiter.api.Assertions.*;
import com.campusconnect.exception.ApiException;
import com.campusconnect.exception.EventConflictException;
import com.campusconnect.model.AuthPrincipal;
import com.campusconnect.model.EventData;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class EventPolicyTest {
    private static final OffsetDateTime OPEN=OffsetDateTime.parse("2031-01-01T09:00:00Z");
    private static final OffsetDateTime CLOSE=OffsetDateTime.parse("2031-01-02T09:00:00Z");
    private static final OffsetDateTime START=OffsetDateTime.parse("2031-01-03T09:00:00Z");
    private static final OffsetDateTime END=OffsetDateTime.parse("2031-01-03T17:00:00Z");
    private EventData draft(){return new EventData(1,"EV-1","Test",null,10,"INDIVIDUAL","DRAFT",OPEN,CLOSE,START,END,10,null,OPEN,0,0,false);}
    @Test void organizersManageOnlyOwnedEventsAndAdminsManageAll(){var owner=new AuthPrincipal(10,"O","o@x.test","ORGANIZER");
        assertTrue(EventAccessPolicy.canManage(owner,10));assertFalse(EventAccessPolicy.canManage(owner,11));
        assertTrue(EventAccessPolicy.canManage(new AuthPrincipal(12,"A","a@x.test","ADMIN"),11));
        assertFalse(EventAccessPolicy.canManage(new AuthPrincipal(10,"S","s@x.test","STUDENT"),10));}
    @Test void studentsAndJudgesCannotEnterManagerService(){assertThrows(ApiException.class,()->EventAccessPolicy.requireManager(new AuthPrincipal(1,"S","s@x.test","STUDENT")));
        assertThrows(ApiException.class,()->EventAccessPolicy.requireManager(new AuthPrincipal(2,"J","j@x.test","JUDGE")));}
    @Test void publicVisibilityExcludesDrafts(){assertFalse(EventVisibility.isPublic("DRAFT"));assertTrue(EventVisibility.isPublic("PUBLISHED"));assertTrue(EventVisibility.isPublic("CANCELLED"));assertTrue(EventVisibility.isPublic("COMPLETED"));}
    @Test void enforcesSupportedLifecycleTransitions(){assertEquals("PUBLISHED",EventLifecycle.nextStatus("DRAFT","publish"));
        assertEquals("CANCELLED",EventLifecycle.nextStatus("PUBLISHED","cancel"));
        assertThrows(EventConflictException.class,()->EventLifecycle.nextStatus("COMPLETED","publish"));
        assertThrows(EventConflictException.class,()->EventLifecycle.nextStatus("CANCELLED","cancel"));}
    @Test void blocksConflictingEditsButAllowsMetadataEdits(){EventEditPolicy.validate(draft(),"EV-1","INDIVIDUAL",OPEN,CLOSE,START,END,10,0,0,0,0);
        assertThrows(EventConflictException.class,()->EventEditPolicy.validate(draft(),"EV-1","TEAM",OPEN,CLOSE,START,END,10,1,1,0,0));
        assertThrows(EventConflictException.class,()->EventEditPolicy.validate(draft(),"EV-1","INDIVIDUAL",OPEN,CLOSE,START,END,0,1,1,0,0));
        assertThrows(EventConflictException.class,()->EventEditPolicy.validate(draft(),"EV-1","INDIVIDUAL",OPEN,CLOSE,
                START.plusDays(1),END,10,1,1,1,0));}
    @Test void rejectsWindowChangesThatInvalidateRegistrations(){assertThrows(EventConflictException.class,()->EventEditPolicy.validate(draft(),"EV-1","INDIVIDUAL",OPEN,CLOSE,START,END,10,1,1,0,1));}
}
