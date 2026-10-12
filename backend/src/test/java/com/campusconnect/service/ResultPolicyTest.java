package com.campusconnect.service;

import com.campusconnect.exception.ApiException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class ResultPolicyTest {
    @Test void weightedResultAveragesJudgesThenAppliesWeights() {
        var criteria=List.of(new ResultPolicy.Criterion(1,new BigDecimal("10"),new BigDecimal("60")),
                new ResultPolicy.Criterion(2,new BigDecimal("20"),new BigDecimal("40")));
        Map<Long,List<BigDecimal>> scores=Map.of(1L,List.of(new BigDecimal("8"),new BigDecimal("10")),
                2L,List.of(new BigDecimal("18"),new BigDecimal("18")));
        assertEquals(0,new BigDecimal("90.00").compareTo(ResultPolicy.weightedScore(criteria,2,scores)));
    }
    @Test void incompleteScoresAndWrongWeightTotalCannotProduceResult() {
        var c1=new ResultPolicy.Criterion(1,new BigDecimal("10"),new BigDecimal("50"));
        var c2=new ResultPolicy.Criterion(2,new BigDecimal("10"),new BigDecimal("50"));
        assertThrows(ApiException.class,()->ResultPolicy.weightedScore(List.of(c1,c2),2,Map.of(1L,List.of(BigDecimal.ONE))));
        assertThrows(ApiException.class,()->ResultPolicy.weightedScore(List.of(c1),1,Map.of(1L,List.of(BigDecimal.ONE))));
    }
    @Test void ranksUseDenseTies() {
        var ranks=ResultPolicy.rank(Map.of(10L,new BigDecimal("92.00"),11L,new BigDecimal("92.00"),12L,new BigDecimal("80.00")));
        assertEquals(List.of(1,1,2),ranks.stream().map(ResultPolicy.Result::rank).toList());
    }
}
