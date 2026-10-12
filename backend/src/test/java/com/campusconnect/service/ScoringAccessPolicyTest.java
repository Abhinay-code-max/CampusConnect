package com.campusconnect.service;

import com.campusconnect.exception.ApiException;
import com.campusconnect.model.AuthPrincipal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ScoringAccessPolicyTest {
    @Test void ownerAndAdminMayManageButForeignOrganizerMayNot() {
        var owner=new AuthPrincipal(10,"Owner","owner@example.test","ORGANIZER");
        var admin=new AuthPrincipal(11,"Admin","admin@example.test","ADMIN");
        var foreign=new AuthPrincipal(12,"Other","other@example.test","ORGANIZER");
        assertDoesNotThrow(()->ScoringAccessPolicy.requireManager(owner,10));
        assertDoesNotThrow(()->ScoringAccessPolicy.requireManager(admin,10));
        assertThrows(ApiException.class,()->ScoringAccessPolicy.requireManager(foreign,10));
        assertThrows(ApiException.class,()->ScoringAccessPolicy.requireManager(new AuthPrincipal(13,"Student","s@example.test","STUDENT"),10));
    }
    @Test void onlyAssignedActiveJudgeMayScore() {
        var judge=new AuthPrincipal(5,"Judge","judge@example.test","JUDGE");
        assertDoesNotThrow(()->ScoringAccessPolicy.requireAssignedActiveJudge(judge,true,true));
        assertThrows(ApiException.class,()->ScoringAccessPolicy.requireAssignedActiveJudge(judge,false,true));
        assertThrows(ApiException.class,()->ScoringAccessPolicy.requireAssignedActiveJudge(judge,true,false));
        assertThrows(ApiException.class,()->ScoringAccessPolicy.requireAssignedActiveJudge("STUDENT",true,true));
    }
    @Test void rejectsCrossEventOrUnconfirmedRegistrationsAndCriteria() {
        assertDoesNotThrow(()->ScoringAccessPolicy.requireConfirmedRegistrationForEvent(7,7,"CONFIRMED"));
        assertThrows(ApiException.class,()->ScoringAccessPolicy.requireConfirmedRegistrationForEvent(7,8,"CONFIRMED"));
        assertThrows(ApiException.class,()->ScoringAccessPolicy.requireConfirmedRegistrationForEvent(7,7,"WAITLISTED"));
        assertDoesNotThrow(()->ScoringAccessPolicy.requireCriterionForEvent(true));
        assertThrows(ApiException.class,()->ScoringAccessPolicy.requireCriterionForEvent(false));
    }
}
