package com.campusconnect.service;

import com.campusconnect.exception.ApiException;
import java.util.Set;

/** Pure account-change rules; the DAO serializes and supplies locked database counts. */
public final class AdminAccountPolicy {
    private AdminAccountPolicy() { }
    public static void protectLastActiveAdmin(String oldRole,boolean oldActive,String newRole,boolean newActive,long activeAdminCount){
        if(oldActive&&"ADMIN".equals(oldRole)&&(!newActive||!"ADMIN".equals(newRole))&&activeAdminCount<=1)
            throw new ApiException(409,"The last active administrator cannot be demoted or deactivated");
    }
    public static void validateRoleChange(String oldRole,String newRole,long teamMemberships,long activeRegistrations,long ownedEvents,long judgeAssignments){
        if(teamMemberships>0||activeRegistrations>0)
            throw new ApiException(409,"Role change conflicts with existing team membership or active registration");
        if("ORGANIZER".equals(oldRole)&&!Set.of("ORGANIZER","ADMIN").contains(newRole)&&ownedEvents>0)
            throw new ApiException(409,"Role change conflicts with event ownership");
        if("JUDGE".equals(oldRole)&&!"JUDGE".equals(newRole)&&judgeAssignments>0)
            throw new ApiException(409,"Role change conflicts with judge assignments");
    }
    public static void validateDeactivation(String role,long activeJudgingResponsibilities){
        if("JUDGE".equals(role)&&activeJudgingResponsibilities>0)
            throw new ApiException(409,"Remove the judge from draft or published events before deactivation");
    }
}
