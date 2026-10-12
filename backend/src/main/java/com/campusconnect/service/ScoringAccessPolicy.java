package com.campusconnect.service;

import com.campusconnect.exception.ApiException;
import com.campusconnect.model.AuthPrincipal;

public final class ScoringAccessPolicy {
    private ScoringAccessPolicy() { }
    public static boolean isAdmin(AuthPrincipal actor) { return actor != null && "ADMIN".equals(actor.role()); }
    public static void requireManager(AuthPrincipal actor, long ownerUserId) {
        if (actor == null || !(isAdmin(actor) || "ORGANIZER".equals(actor.role())))
            throw new ApiException(403, "Forbidden");
        requireManager(actor.userId(), isAdmin(actor), ownerUserId);
    }
    public static void requireManager(long actorId, boolean admin, long ownerUserId) {
        if (!admin && actorId != ownerUserId)
            throw new ApiException(403, "You may manage scoring only for your own events");
    }
    public static void requireAssignedActiveJudge(AuthPrincipal actor, boolean assigned, boolean active) {
        requireAssignedActiveJudge(actor == null ? null : actor.role(), assigned, active);
    }
    public static void requireAssignedActiveJudge(String role, boolean assigned, boolean active) {
        if (!"JUDGE".equals(role) || !active)
            throw new ApiException(403, "An active judge account is required");
        if (!assigned) throw new ApiException(403, "You are not assigned to this event");
    }
    public static void requireConfirmedRegistrationForEvent(long expectedEventId,long registrationEventId,String status) {
        if(expectedEventId!=registrationEventId||!"CONFIRMED".equals(status))
            throw new ApiException(409,"Registration is not eligible for scoring in this event");
    }
    public static void requireCriterionForEvent(boolean criterionBelongsToEvent) {
        if(!criterionBelongsToEvent)throw new ApiException(409,"A criterion does not belong to this event");
    }
}
