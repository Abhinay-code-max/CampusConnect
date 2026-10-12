package com.campusconnect.service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.campusconnect.exception.ApiException;

class ParticipationPolicyTest {
    @Test void participationModeMustMatchRegistrationSubject(){
        assertDoesNotThrow(()->ParticipationPolicy.requireMode("INDIVIDUAL",false));
        assertDoesNotThrow(()->ParticipationPolicy.requireMode("TEAM",true));
        assertEquals(409,assertThrows(ApiException.class,()->ParticipationPolicy.requireMode("TEAM",false)).getStatus());
        assertEquals(409,assertThrows(ApiException.class,()->ParticipationPolicy.requireMode("INDIVIDUAL",true)).getStatus());
    }
    @Test void teamsNeedTwoToFiveConsentingMembers(){
        assertThrows(ApiException.class,()->ParticipationPolicy.requireTeamSize(1));
        assertDoesNotThrow(()->ParticipationPolicy.requireTeamSize(2));
        assertDoesNotThrow(()->ParticipationPolicy.requireTeamSize(5));
        assertThrows(ApiException.class,()->ParticipationPolicy.requireTeamSize(6));
    }
    @Test void capacityCountsConfirmedEntriesAndWaitlistDoesNotConsumeCapacity(){
        assertEquals("CONFIRMED",ParticipationPolicy.registrationStatus(1,0));
        assertEquals("WAITLISTED",ParticipationPolicy.registrationStatus(1,1));
        assertEquals("CONFIRMED",ParticipationPolicy.registrationStatus(null,1000));
    }
    @Test void cancelledEntryRemainsTerminalAndActiveEntryIsDuplicate(){
        assertFalse(ParticipationPolicy.isActiveRegistration("CANCELLED"));
        assertTrue(ParticipationPolicy.isActiveRegistration("CONFIRMED"));
        assertTrue(ParticipationPolicy.isActiveRegistration("WAITLISTED"));
        assertDoesNotThrow(()->ParticipationPolicy.requireNoPriorRegistration(null));
        assertEquals(409,assertThrows(ApiException.class,()->ParticipationPolicy.requireNoPriorRegistration("CANCELLED")).getStatus());
        assertEquals(409,assertThrows(ApiException.class,()->ParticipationPolicy.requireNoPriorRegistration("CONFIRMED")).getStatus());
    }
    @Test void onlyTheTeamOwnerMayManageTheTeam(){
        assertDoesNotThrow(()->ParticipationPolicy.requireTeamOwner(11,11));
        assertEquals(403,assertThrows(ApiException.class,()->ParticipationPolicy.requireTeamOwner(12,11)).getStatus());
    }
}
