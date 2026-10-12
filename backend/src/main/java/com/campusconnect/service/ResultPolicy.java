package com.campusconnect.service;

import com.campusconnect.exception.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Pure scoring and completeness rules aligned with V_CC_EVENT_RESULTS. */
public final class ResultPolicy {
    private ResultPolicy() { }
    public record Criterion(long id, BigDecimal maxPoints, BigDecimal weightPercent) { }
    public record Result(long registrationId, BigDecimal weightedScore, int rank) { }

    public static BigDecimal weightedScore(List<Criterion> criteria, int assignedJudges,
                                           Map<Long, List<BigDecimal>> pointsByCriterion) {
        if (criteria == null || criteria.isEmpty() || assignedJudges < 1)
            throw new ApiException(409, "Results require criteria and at least one assigned judge");
        BigDecimal weight = criteria.stream().map(Criterion::weightPercent).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (weight.compareTo(new BigDecimal("100")) != 0)
            throw new ApiException(409, "Criterion weights must total exactly 100 percent");
        BigDecimal total = BigDecimal.ZERO;
        for (Criterion criterion : criteria) {
            List<BigDecimal> scores = pointsByCriterion.get(criterion.id());
            if (scores == null || scores.size() != assignedJudges)
                throw new ApiException(409, "Scoring is incomplete");
            BigDecimal average = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(assignedJudges), 8, RoundingMode.HALF_UP);
            total = total.add(average.divide(criterion.maxPoints(), 12, RoundingMode.HALF_UP)
                    .multiply(criterion.weightPercent()));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    public static List<Result> rank(Map<Long, BigDecimal> completeScores) {
        List<Map.Entry<Long, BigDecimal>> sorted = new ArrayList<>(completeScores.entrySet());
        sorted.sort(Map.Entry.<Long, BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));
        List<Result> result = new ArrayList<>();
        BigDecimal prior = null;
        int rank = 0;
        for (Map.Entry<Long, BigDecimal> row : sorted) {
            if (prior == null || prior.compareTo(row.getValue()) != 0) rank++;
            result.add(new Result(row.getKey(), row.getValue(), rank));
            prior = row.getValue();
        }
        return List.copyOf(result);
    }
}
