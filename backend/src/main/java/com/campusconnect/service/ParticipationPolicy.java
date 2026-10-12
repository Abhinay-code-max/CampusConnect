package com.campusconnect.service;

import com.campusconnect.exception.ApiException;

/** Small, database-independent rules also used by the JDBC service path. */
public final class ParticipationPolicy {
    public static final int MIN_TEAM_SIZE=2, MAX_TEAM_SIZE=5;
    private ParticipationPolicy() { }
    public static void requireMode(String eventMode,boolean teamRegistration){if(teamRegistration?!"TEAM".equals(eventMode):!"INDIVIDUAL".equals(eventMode))throw new ApiException(409,"Registration type does not match the event participation mode");}
    public static void requireTeamSize(int members){if(members<MIN_TEAM_SIZE||members>MAX_TEAM_SIZE)throw new ApiException(409,"An eligible team must have two to five active student members, including its owner");}
    public static String registrationStatus(Integer capacity,int confirmed){return capacity==null||confirmed<capacity?"CONFIRMED":"WAITLISTED";}
    public static boolean isActiveRegistration(String status){return "CONFIRMED".equals(status)||"WAITLISTED".equals(status);}
    public static void requireNoPriorRegistration(String status){if(isActiveRegistration(status))throw new ApiException(409,"This participant is already registered for the event");if("CANCELLED".equals(status))throw new ApiException(409,"A cancelled registration cannot be reactivated; the existing record is retained");}
    public static void requireTeamOwner(long actor,long owner){if(actor!=owner)throw new ApiException(403,"Only the team owner may make this change");}
}
