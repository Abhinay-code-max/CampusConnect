package com.campusconnect.service;

import com.campusconnect.exception.ValidationException;
import com.campusconnect.model.ScoreBatchRequest;
import com.campusconnect.model.ScoringCriterionRequest;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class ScoringInputValidatorTest {
    @Test void criterionAcceptsSchemaBoundsAndRejectsInvalidWeight() {
        ScoringCriterionRequest request=new ScoringCriterionRequest();request.setCriterionName("Presentation");
        request.setMaxPoints(new BigDecimal("20.00"));request.setWeightPercent(new BigDecimal("100.00"));request.setDisplayOrder(1);
        assertEquals("Presentation",ScoringInputValidator.criterion(request).name());
        request.setWeightPercent(new BigDecimal("100.01"));assertThrows(ValidationException.class,()->ScoringInputValidator.criterion(request));
    }
    @Test void scoreBatchAcceptsZeroAndMaximumPrecision() {
        ScoreBatchRequest batch=new ScoreBatchRequest();
        batch.setScores(List.of(entry(1,"0.00"),entry(2,"999999.99")));
        assertEquals(2,ScoringInputValidator.scores(batch).size());
    }
    @Test void scoreBatchRejectsDuplicatesNegativeAndExcessPrecision() {
        ScoreBatchRequest batch=new ScoreBatchRequest();batch.setScores(List.of(entry(2,"1"),entry(2,"2")));
        assertThrows(ValidationException.class,()->ScoringInputValidator.scores(batch));
        batch.setScores(List.of(entry(1,"-0.01")));assertThrows(ValidationException.class,()->ScoringInputValidator.scores(batch));
        batch.setScores(List.of(entry(1,"1.001")));assertThrows(ValidationException.class,()->ScoringInputValidator.scores(batch));
    }
    private static ScoreBatchRequest.ScoreEntryRequest entry(long id,String points) {
        var entry=new ScoreBatchRequest.ScoreEntryRequest();entry.setCriterionId(id);entry.setAwardedPoints(new BigDecimal(points));return entry;
    }
}
