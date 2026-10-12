package com.campusconnect.dao;

import com.campusconnect.service.ScoringInputValidator.ValidCriterion;
import com.campusconnect.service.ScoringInputValidator.ValidScore;
import com.campusconnect.service.ScoringInputValidator.ValidWindow;
import java.util.List;
import java.util.Map;

public interface ScoringDao {
    List<Map<String,Object>> assignedEvents(long judgeUserId);
    Map<String,Object> judgingEvent(long judgeUserId, long eventId);
    List<Map<String,Object>> judgeScores(long judgeUserId, long eventId, long registrationId);
    List<Map<String,Object>> managersJudges(long actorId, boolean admin, long eventId);
    Map<String,Object> assignJudge(long actorId, boolean admin, long eventId, long judgeUserId);
    Map<String,Object> unassignJudge(long actorId, boolean admin, long eventId, long judgeUserId);
    List<Map<String,Object>> criteria(long actorId, boolean admin, long eventId);
    Map<String,Object> createCriterion(long actorId, boolean admin, long eventId, ValidCriterion criterion);
    Map<String,Object> updateCriterion(long actorId, boolean admin, long eventId, long criterionId, ValidCriterion criterion);
    Map<String,Object> setWindow(long actorId, boolean admin, long eventId, ValidWindow window);
    Map<String,Object> closeScoring(long actorId, boolean admin, long eventId);
    Map<String,Object> submitScores(long judgeUserId, long eventId, long registrationId, List<ValidScore> scores);
    Map<String,Object> finalizeResults(long actorId, boolean admin, long eventId);
    Map<String,Object> publishResults(long actorId, boolean admin, long eventId);
    Map<String,Object> publicResults(long eventId, int page, int pageSize);
    Map<String,Object> managerResults(long actorId, boolean admin, long eventId, int page, int pageSize);
}
