package com.campusconnect.service;

import static org.junit.jupiter.api.Assertions.*;
import com.campusconnect.exception.ApiException;
import org.junit.jupiter.api.Test;

class AdminAccountPolicyTest {
    @Test void protectsLastActiveAdministratorFromDemotionAndDeactivation(){
        assertEquals(409,assertThrows(ApiException.class,()->AdminAccountPolicy.protectLastActiveAdmin("ADMIN",true,"STUDENT",true,1)).getStatus());
        assertEquals(409,assertThrows(ApiException.class,()->AdminAccountPolicy.protectLastActiveAdmin("ADMIN",true,"ADMIN",false,1)).getStatus());
    }
    @Test void permitsAdminChangeWhenAnotherActiveAdministratorExists(){
        assertDoesNotThrow(()->AdminAccountPolicy.protectLastActiveAdmin("ADMIN",true,"STUDENT",true,2));
    }
    @Test void blocksRoleChangesWithRelationships(){
        assertEquals(409,assertThrows(ApiException.class,()->AdminAccountPolicy.validateRoleChange("STUDENT","ORGANIZER",1,0,0,0)).getStatus());
        assertEquals(409,assertThrows(ApiException.class,()->AdminAccountPolicy.validateRoleChange("ORGANIZER","STUDENT",0,0,1,0)).getStatus());
        assertEquals(409,assertThrows(ApiException.class,()->AdminAccountPolicy.validateRoleChange("JUDGE","STUDENT",0,0,0,1)).getStatus());
    }
    @Test void allowsRoleChangesWithoutConflictingRelationships(){
        assertDoesNotThrow(()->AdminAccountPolicy.validateRoleChange("STUDENT","ADMIN",0,0,0,0));
        assertDoesNotThrow(()->AdminAccountPolicy.validateRoleChange("ORGANIZER","ADMIN",0,0,1,0));
    }
    @Test void keepsAssignedJudgesActiveUntilLiveResponsibilitiesAreRemoved(){
        assertEquals(409,assertThrows(ApiException.class,()->AdminAccountPolicy.validateDeactivation("JUDGE",1)).getStatus());
        assertDoesNotThrow(()->AdminAccountPolicy.validateDeactivation("JUDGE",0));
        assertDoesNotThrow(()->AdminAccountPolicy.validateDeactivation("STUDENT",0));
    }
}
