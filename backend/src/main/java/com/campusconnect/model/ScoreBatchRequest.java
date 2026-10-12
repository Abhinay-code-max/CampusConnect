package com.campusconnect.model;

import java.util.List;

public final class ScoreBatchRequest {
    private List<ScoreEntryRequest> scores;
    public List<ScoreEntryRequest> getScores() { return scores; }
    public void setScores(List<ScoreEntryRequest> scores) { this.scores = scores; }

    public static final class ScoreEntryRequest {
        private Long criterionId;
        private java.math.BigDecimal awardedPoints;
        private String feedback;
        public Long getCriterionId() { return criterionId; }
        public void setCriterionId(Long criterionId) { this.criterionId = criterionId; }
        public java.math.BigDecimal getAwardedPoints() { return awardedPoints; }
        public void setAwardedPoints(java.math.BigDecimal awardedPoints) { this.awardedPoints = awardedPoints; }
        public String getFeedback() { return feedback; }
        public void setFeedback(String feedback) { this.feedback = feedback; }
    }
}
