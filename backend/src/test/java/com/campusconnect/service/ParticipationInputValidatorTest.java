package com.campusconnect.service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.campusconnect.exception.ValidationException;
import com.campusconnect.model.TeamRequest;
import com.campusconnect.model.RegistrationRequest;

class ParticipationInputValidatorTest {
    @Test void trimsAndBoundsTeamNames(){assertEquals("Blue Team",ParticipationInputValidator.teamName(new TeamRequest("  Blue Team ",null)));assertThrows(ValidationException.class,()->ParticipationInputValidator.teamName(new TeamRequest(" ",null)));assertThrows(ValidationException.class,()->ParticipationInputValidator.teamName(new TeamRequest("x".repeat(121),null)));}
    @Test void identifiersMustBePositive(){assertEquals(7,ParticipationInputValidator.eventId(new RegistrationRequest(7L)));assertThrows(ValidationException.class,()->ParticipationInputValidator.eventId(new RegistrationRequest(0L)));assertThrows(ValidationException.class,()->ParticipationInputValidator.invitee(new TeamRequest(null,0L)));}
}
