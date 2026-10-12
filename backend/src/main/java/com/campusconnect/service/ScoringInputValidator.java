package com.campusconnect.service;

import com.campusconnect.exception.ValidationException;
import com.campusconnect.model.ScoreBatchRequest;
import com.campusconnect.model.ScoringCriterionRequest;
import com.campusconnect.model.ScoringWindowRequest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ScoringInputValidator {
    public static final int MAX_CRITERIA = 100;
    private static final BigDecimal MAX_POINTS = new BigDecimal("999999.99");
    private ScoringInputValidator() { }

    public record ValidCriterion(String name, BigDecimal maxPoints, BigDecimal weightPercent, int displayOrder) { }
    public record ValidScore(long criterionId, BigDecimal points, String feedback) { }
    public record ValidWindow(OffsetDateTime opens, OffsetDateTime closes) { }

    public static ValidCriterion criterion(ScoringCriterionRequest r) {
        if (r == null) throw new ValidationException("Criterion body is required");
        String name = r.getCriterionName();
        if (name == null) throw new ValidationException("criterionName is required");
        name = name.strip();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > 120
                || name.codePoints().anyMatch(Character::isISOControl))
            throw new ValidationException("criterionName must contain 1 to 120 printable characters");
        BigDecimal max = decimal(r.getMaxPoints(), "maxPoints", new BigDecimal("0.01"), MAX_POINTS);
        BigDecimal weight = decimal(r.getWeightPercent(), "weightPercent", new BigDecimal("0.01"), new BigDecimal("100.00"));
        int order = r.getDisplayOrder() == null ? 0 : r.getDisplayOrder();
        if (order < 1 || order > 9999) throw new ValidationException("displayOrder must be between 1 and 9999");
        return new ValidCriterion(name, max, weight, order);
    }

    public static List<ValidScore> scores(ScoreBatchRequest request) {
        if (request == null || request.getScores() == null || request.getScores().isEmpty())
            throw new ValidationException("scores must contain at least one entry");
        if (request.getScores().size() > MAX_CRITERIA)
            throw new ValidationException("A score batch may contain at most 100 entries");
        Set<Long> seen = new HashSet<>();
        return request.getScores().stream().map(entry -> {
            if (entry == null || entry.getCriterionId() == null || entry.getCriterionId() < 1)
                throw new ValidationException("Each score requires a positive criterionId");
            long criterionId = entry.getCriterionId();
            if (!seen.add(criterionId)) throw new ValidationException("A criterion may appear only once in a score batch");
            BigDecimal points = decimal(entry.getAwardedPoints(), "awardedPoints", BigDecimal.ZERO, MAX_POINTS);
            if (points.stripTrailingZeros().scale() > 2)
                throw new ValidationException("awardedPoints may have at most two decimal places");
            String feedback = entry.getFeedback();
            if (feedback != null && feedback.length() > 1000)
                throw new ValidationException("feedback must be at most 1000 characters");
            return new ValidScore(criterionId, points, feedback);
        }).toList();
    }

    public static ValidWindow window(ScoringWindowRequest request) {
        if (request == null) throw new ValidationException("Scoring window body is required");
        OffsetDateTime opens = date(request.getScoringOpens(), "scoringOpens");
        OffsetDateTime closes = date(request.getScoringCloses(), "scoringCloses");
        if (!opens.isBefore(closes)) throw new ValidationException("scoringOpens must be before scoringCloses");
        if (!closes.isAfter(OffsetDateTime.now())) throw new ValidationException("scoringCloses must be in the future");
        return new ValidWindow(opens, closes);
    }

    private static BigDecimal decimal(BigDecimal value, String label, BigDecimal min, BigDecimal max) {
        if (value == null || value.compareTo(min) < 0 || value.compareTo(max) > 0
                || value.stripTrailingZeros().scale() > 2)
            throw new ValidationException(label + " must be in range with at most two decimal places");
        return value;
    }
    private static OffsetDateTime date(String value, String label) {
        try { return OffsetDateTime.parse(value); }
        catch (DateTimeParseException | NullPointerException e) {
            throw new ValidationException(label + " must be an ISO-8601 date-time with an offset");
        }
    }
}
