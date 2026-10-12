package com.campusconnect.service;

import com.campusconnect.dao.JdbcScoringDao;
import com.campusconnect.dao.ScoringDao;
import com.campusconnect.exception.ApiException;
import com.campusconnect.model.AuthPrincipal;
import com.campusconnect.model.ScoreBatchRequest;
import com.campusconnect.model.ScoringCriterionRequest;
import com.campusconnect.model.ScoringWindowRequest;
import java.util.List;
import java.util.Map;

public final class ScoringService {
    private final ScoringDao dao;
    public ScoringService() { this(new JdbcScoringDao()); }
    public ScoringService(ScoringDao dao) { this.dao = dao; }

    public List<Map<String,Object>> assignedEvents(AuthPrincipal judge) {
        requireJudge(judge); return dao.assignedEvents(judge.userId());
    }
    public Map<String,Object> judgingEvent(AuthPrincipal judge, long eventId) {
        requireJudge(judge); return dao.judgingEvent(judge.userId(), eventId);
    }
    public List<Map<String,Object>> judgeScores(AuthPrincipal judge, long eventId, long registrationId) {
        requireJudge(judge); return dao.judgeScores(judge.userId(), eventId, registrationId);
    }
    public Map<String,Object> submitScores(AuthPrincipal judge, long eventId, long registrationId, ScoreBatchRequest request) {
        requireJudge(judge);
        return dao.submitScores(judge.userId(), eventId, registrationId, ScoringInputValidator.scores(request));
    }
    public List<Map<String,Object>> managersJudges(AuthPrincipal actor, long eventId) {
        return dao.managersJudges(actorId(actor), isAdmin(actor), eventId);
    }
    public Map<String,Object> assignJudge(AuthPrincipal actor, long eventId, Long judgeId) {
        if (judgeId == null || judgeId < 1) throw new com.campusconnect.exception.ValidationException("judgeUserId must be positive");
        return dao.assignJudge(actorId(actor), isAdmin(actor), eventId, judgeId);
    }
    public Map<String,Object> unassignJudge(AuthPrincipal actor, long eventId, long judgeId) {
        return dao.unassignJudge(actorId(actor), isAdmin(actor), eventId, judgeId);
    }
    public List<Map<String,Object>> criteria(AuthPrincipal actor, long eventId) {
        return dao.criteria(actorId(actor), isAdmin(actor), eventId);
    }
    public Map<String,Object> createCriterion(AuthPrincipal actor, long eventId, ScoringCriterionRequest request) {
        return dao.createCriterion(actorId(actor), isAdmin(actor), eventId, ScoringInputValidator.criterion(request));
    }
    public Map<String,Object> updateCriterion(AuthPrincipal actor, long eventId, long criterionId, ScoringCriterionRequest request) {
        return dao.updateCriterion(actorId(actor), isAdmin(actor), eventId, criterionId, ScoringInputValidator.criterion(request));
    }
    public Map<String,Object> setWindow(AuthPrincipal actor, long eventId, ScoringWindowRequest request) {
        return dao.setWindow(actorId(actor), isAdmin(actor), eventId, ScoringInputValidator.window(request));
    }
    public Map<String,Object> closeScoring(AuthPrincipal actor, long eventId) {
        return dao.closeScoring(actorId(actor), isAdmin(actor), eventId);
    }
    public Map<String,Object> finalizeResults(AuthPrincipal actor, long eventId) {
        return dao.finalizeResults(actorId(actor), isAdmin(actor), eventId);
    }
    public Map<String,Object> publishResults(AuthPrincipal actor, long eventId) {
        return dao.publishResults(actorId(actor), isAdmin(actor), eventId);
    }
    public Map<String,Object> managerResults(AuthPrincipal actor, long eventId, String page, String pageSize) {
        return dao.managerResults(actorId(actor), isAdmin(actor), eventId, page(page,1), pageSize(pageSize,50));
    }
    public Map<String,Object> publicResults(long eventId, String page, String pageSize) {
        return dao.publicResults(eventId,page(page,1),pageSize(pageSize,50));
    }

    private static void requireJudge(AuthPrincipal actor) {
        if (actor == null || !"JUDGE".equals(actor.role())) throw new ApiException(403, "Judge account required");
    }
    private static long actorId(AuthPrincipal actor) {
        if (actor == null || !("ORGANIZER".equals(actor.role()) || "ADMIN".equals(actor.role())))
            throw new ApiException(403, "Organizer or admin account required");
        return actor.userId();
    }
    private static boolean isAdmin(AuthPrincipal actor) { return actor != null && "ADMIN".equals(actor.role()); }
    private static int page(String raw,int fallback) { return bounded(raw,fallback,1,1_000_000,"page"); }
    private static int pageSize(String raw,int fallback) { return bounded(raw,fallback,1,100,"pageSize"); }
    private static int bounded(String raw,int fallback,int min,int max,String name) {
        if(raw==null||raw.isBlank())return fallback;
        try{int value=Integer.parseInt(raw);if(value<min||value>max)throw new NumberFormatException();return value;}
        catch(NumberFormatException e){throw new com.campusconnect.exception.ValidationException(name+" must be between "+min+" and "+max);}
    }
}
