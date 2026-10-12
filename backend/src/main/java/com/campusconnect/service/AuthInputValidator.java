package com.campusconnect.service;

import com.campusconnect.exception.ValidationException;
import com.campusconnect.model.LoginRequest;
import com.campusconnect.model.RegisterRequest;
import java.util.Locale;
import java.util.regex.Pattern;

public final class AuthInputValidator {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@"
            + "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");
    private AuthInputValidator() { }

    public static String normalizeEmail(String email) {
        if (email == null) throw new ValidationException("Email is required");
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 254 || !EMAIL.matcher(normalized).matches())
            throw new ValidationException("Email must be a valid address of at most 254 characters");
        return normalized;
    }

    public static ValidRegistration validate(RegisterRequest request) {
        if (request == null) throw new ValidationException("Request body is required");
        String name = request.getDisplayName();
        if (name == null) throw new ValidationException("Display name is required");
        name = name.strip();
        int nameLength = name.codePointCount(0, name.length());
        if (nameLength < 1 || nameLength > 120 || name.codePoints().anyMatch(Character::isISOControl))
            throw new ValidationException("Display name must contain 1 to 120 characters");
        String email = normalizeEmail(request.getEmail());
        validatePassword(request.getPassword());
        return new ValidRegistration(name, email, request.getPassword());
    }

    public static ValidLogin validate(LoginRequest request) {
        if (request == null) throw new ValidationException("Request body is required");
        String email = normalizeEmail(request.getEmail());
        if (request.getPassword() == null || request.getPassword().isEmpty())
            throw new ValidationException("Password is required");
        if (request.getPassword().codePointCount(0, request.getPassword().length()) > 128)
            throw new ValidationException("Password must be at most 128 characters");
        return new ValidLogin(email, request.getPassword());
    }

    private static void validatePassword(String password) {
        if (password == null) throw new ValidationException("Password is required");
        int length = password.codePointCount(0, password.length());
        if (length < 12 || length > 128) throw new ValidationException("Password must contain 12 to 128 characters");
    }

    public record ValidRegistration(String displayName, String email, String password) { }
    public record ValidLogin(String email, String password) { }
}
