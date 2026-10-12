package com.campusconnect.service;

import static org.junit.jupiter.api.Assertions.*;
import com.campusconnect.exception.ValidationException;
import com.campusconnect.model.RegisterRequest;
import org.junit.jupiter.api.Test;

class AuthInputValidatorTest {
    @Test void trimsDisplayNameAndNormalizesEmail() {
        var request = new RegisterRequest();
        request.setDisplayName("  Campus Student "); request.setEmail(" Student@Example.edu ");
        request.setPassword("a long passphrase 2026");
        var valid = AuthInputValidator.validate(request);
        assertEquals("Campus Student", valid.displayName());
        assertEquals("student@example.edu", valid.email());
    }

    @Test void rejectsMalformedEmailAndShortPassword() {
        var request = new RegisterRequest();
        request.setDisplayName("Student"); request.setEmail("bad-address");
        request.setPassword("long enough password");
        assertThrows(ValidationException.class, () -> AuthInputValidator.validate(request));
        request.setEmail("s@example.edu"); request.setPassword("short");
        assertThrows(ValidationException.class, () -> AuthInputValidator.validate(request));
    }

    @Test void roleIsNotAnAcceptedRegistrationField() {
        assertThrows(com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException.class,
                () -> new com.fasterxml.jackson.databind.ObjectMapper()
                        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .readValue("{\"displayName\":\"Student\",\"email\":\"s@example.edu\","
                                + "\"password\":\"a long passphrase 2026\",\"role\":\"ADMIN\"}", RegisterRequest.class));
    }
}
