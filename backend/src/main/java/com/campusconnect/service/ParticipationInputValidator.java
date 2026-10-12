package com.campusconnect.service;

import com.campusconnect.exception.ValidationException;
import com.campusconnect.model.RegistrationRequest;
import com.campusconnect.model.TeamRequest;

public final class ParticipationInputValidator {
    private ParticipationInputValidator() { }
    public static String teamName(TeamRequest request) {
        if (request == null || request.teamName() == null) throw new ValidationException("Team name is required");
        String value = request.teamName().strip();
        if (value.isEmpty() || value.codePointCount(0, value.length()) > 120 || value.codePoints().anyMatch(Character::isISOControl))
            throw new ValidationException("Team name must contain 1 to 120 characters");
        return value;
    }
    public static long invitee(TeamRequest request) {
        if (request == null || request.inviteeUserId() == null || request.inviteeUserId() < 1) throw new ValidationException("inviteeUserId must be a positive integer");
        return request.inviteeUserId();
    }
    public static long eventId(RegistrationRequest request) {
        if (request == null || request.eventId() == null || request.eventId() < 1) throw new ValidationException("eventId must be a positive integer");
        return request.eventId();
    }
}
