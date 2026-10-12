package com.campusconnect.service;

import com.campusconnect.exception.ValidationException;
import com.campusconnect.model.EventData;
import com.campusconnect.model.EventRequest;
import com.campusconnect.model.EventSearch;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class EventInputValidator {
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,39}");
    private EventInputValidator() { }

    public record ValidEvent(String eventCode, String eventName, String description, String mode,
                             OffsetDateTime registrationOpens, OffsetDateTime registrationCloses,
                             OffsetDateTime startsAt, OffsetDateTime endsAt, Integer capacity, String location) { }

    public static ValidEvent validate(EventRequest r) { return validate(r, Clock.systemUTC(), true); }
    static ValidEvent validate(EventRequest r, Clock clock, boolean requireCode) {
        if (r == null) throw new ValidationException("Request body is required");
        String code = clean(r.getEventCode(), "Event code", 40, requireCode);
        if (code != null && !CODE.matcher(code).matches()) throw new ValidationException("Event code has an invalid format");
        String name = clean(r.getEventName(), "Event name", 160, true);
        String description = cleanOptional(r.getDescription(), "Description", 2000);
        String location = cleanOptional(r.getLocation(), "Location", 200);
        String mode = clean(r.getParticipationMode(), "Participation mode", 12, true).toUpperCase(Locale.ROOT);
        if (!mode.equals("INDIVIDUAL") && !mode.equals("TEAM"))
            throw new ValidationException("Participation mode must be INDIVIDUAL or TEAM");
        OffsetDateTime open = date(r.getRegistrationOpens(), "registrationOpens");
        OffsetDateTime close = date(r.getRegistrationCloses(), "registrationCloses");
        OffsetDateTime starts = date(r.getStartsAt(), "startsAt");
        OffsetDateTime ends = date(r.getEndsAt(), "endsAt");
        if (open.isAfter(close) || close.isAfter(starts) || !starts.isBefore(ends))
            throw new ValidationException("Dates must satisfy registrationOpens <= registrationCloses <= startsAt < endsAt");
        if (!starts.isAfter(OffsetDateTime.now(clock)) || !close.isAfter(OffsetDateTime.now(clock)))
            throw new ValidationException("Registration close and event start must be in the future");
        if (r.getCapacity() != null && (r.getCapacity() < 1 || r.getCapacity() > 99_999_999))
            throw new ValidationException("Capacity must be between 1 and 99999999 or null");
        return new ValidEvent(code, name, description, mode, open, close, starts, ends, r.getCapacity(), location);
    }

    public static EventSearch search(String pageValue, String sizeValue, String q, String mode,
                                     String startsAfter, String startsBefore, String registrationOpen,
                                     String sort, String direction) {
        int page = integer(pageValue, 1, 1, 1_000_000, "page");
        int size = integer(sizeValue, 20, 1, 100, "pageSize");
        String query = q == null || q.isBlank() ? null : q.strip();
        if (query != null && query.length() > 100) throw new ValidationException("q must be at most 100 characters");
        String normalizedMode = mode == null || mode.isBlank() ? null : mode.strip().toUpperCase(Locale.ROOT);
        if (normalizedMode != null && !Set.of("INDIVIDUAL", "TEAM").contains(normalizedMode))
            throw new ValidationException("participationMode must be INDIVIDUAL or TEAM");
        OffsetDateTime after = startsAfter == null || startsAfter.isBlank() ? null : date(startsAfter, "startsAfter");
        OffsetDateTime before = startsBefore == null || startsBefore.isBlank() ? null : date(startsBefore, "startsBefore");
        if (after != null && before != null && after.isAfter(before)) throw new ValidationException("startsAfter must not be after startsBefore");
        Boolean open = null;
        if (registrationOpen != null && !registrationOpen.isBlank()) {
            if (!registrationOpen.equalsIgnoreCase("true") && !registrationOpen.equalsIgnoreCase("false"))
                throw new ValidationException("registrationOpen must be true or false");
            open = Boolean.valueOf(registrationOpen);
        }
        String normalizedSort = sort == null || sort.isBlank() ? "startsAt" : sort;
        if (!Set.of("startsAt", "eventName", "createdAt").contains(normalizedSort))
            throw new ValidationException("sort must be startsAt, eventName, or createdAt");
        String normalizedDirection = direction == null || direction.isBlank() ? "asc" : direction.toLowerCase(Locale.ROOT);
        if (!Set.of("asc", "desc").contains(normalizedDirection)) throw new ValidationException("direction must be asc or desc");
        return new EventSearch(page, size, query, normalizedMode, after, before, open, normalizedSort, normalizedDirection);
    }

    private static int integer(String value, int fallback, int min, int max, String label) {
        if (value == null || value.isBlank()) return fallback;
        try { int n = Integer.parseInt(value); if (n < min || n > max) throw new NumberFormatException(); return n; }
        catch (NumberFormatException e) { throw new ValidationException(label + " must be between " + min + " and " + max); }
    }
    private static OffsetDateTime date(String value, String field) {
        try { if (value == null) throw new DateTimeParseException("missing", "", 0); return OffsetDateTime.parse(value); }
        catch (DateTimeParseException e) { throw new ValidationException(field + " must be an ISO-8601 date-time with an offset"); }
    }
    private static String clean(String value, String label, int max, boolean required) {
        if (value == null) { if (required) throw new ValidationException(label + " is required"); return null; }
        String cleaned = value.strip();
        if (cleaned.isEmpty()) { if (required) throw new ValidationException(label + " is required"); return null; }
        if (cleaned.codePointCount(0, cleaned.length()) > max || cleaned.codePoints().anyMatch(Character::isISOControl))
            throw new ValidationException(label + " exceeds its limit or contains control characters");
        return cleaned;
    }
    private static String cleanOptional(String value, String label, int max) { return clean(value, label, max, false); }
}
