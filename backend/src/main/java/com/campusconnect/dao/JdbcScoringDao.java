package com.campusconnect.dao;

import com.campusconnect.exception.ApiException;
import com.campusconnect.exception.ValidationException;
import com.campusconnect.service.ScoringInputValidator.ValidCriterion;
import com.campusconnect.service.ScoringInputValidator.ValidScore;
import com.campusconnect.service.ScoringInputValidator.ValidWindow;
import com.campusconnect.service.ScoringAccessPolicy;
import com.campusconnect.util.DBConnection;
import com.campusconnect.util.JdbcTransactionRunner;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** Oracle persistence for judge assignment, scoring, and result lifecycle. */
public final class JdbcScoringDao implements ScoringDao {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

    @Override public List<Map<String,Object>> assignedEvents(long judge) {
        return read(c -> rows(c, "SELECT e.event_id,e.event_code,e.event_name,e.event_status,e.starts_at,e.ends_at,"
                + "e.scoring_opens,e.scoring_closes FROM cc_events e JOIN cc_event_judges j ON j.event_id=e.event_id "
                + "JOIN cc_users u ON u.user_id=j.judge_user_id WHERE j.judge_user_id=? AND u.role_code='JUDGE' "
                + "AND u.active_flag='Y' ORDER BY e.starts_at,e.event_id", judge));
    }

    @Override public Map<String,Object> judgingEvent(long judge, long eventId) {
        return read(c -> {
            EventRow event = event(c, eventId, false);
            requireAssigned(c, judge, eventId);
            Map<String,Object> result = new LinkedHashMap<>();
            result.put("event", eventMap(event));
            result.put("criteria", criterionRows(c, eventId));
            result.put("registrations", rows(c, "SELECT registration_id,event_id,registration_type,registrant_name,"
                    + "registration_status,registered_at FROM v_cc_event_registrations "
                    + "WHERE event_id=? AND registration_status='CONFIRMED' ORDER BY registration_id", eventId));
            return result;
        });
    }

    @Override public List<Map<String,Object>> judgeScores(long judge, long eventId, long registrationId) {
        return read(c -> {
            requireAssigned(c, judge, eventId);
            requireConfirmedRegistration(c, eventId, registrationId, false);
            return rows(c, "SELECT score_id,event_id,registration_id,criterion_id,judge_user_id,awarded_points,"
                    + "feedback,submitted_at FROM cc_scores WHERE event_id=? AND registration_id=? AND judge_user_id=? "
                    + "ORDER BY criterion_id", eventId, registrationId, judge);
        });
    }

    @Override public List<Map<String,Object>> managersJudges(long actor, boolean admin, long eventId) {
        return read(c -> { managerEvent(c, eventId, actor, admin, false); return rows(c,
                "SELECT u.user_id,u.display_name,u.role_code,u.active_flag,j.assigned_at FROM cc_event_judges j "
                        + "JOIN cc_users u ON u.user_id=j.judge_user_id WHERE j.event_id=? ORDER BY u.display_name,u.user_id", eventId); });
    }

    @Override public Map<String,Object> assignJudge(long actor, boolean admin, long eventId, long judge) {
        return tx("Judge is already assigned", c -> {
            EventRow event = managerEvent(c,eventId,actor,admin,true); requireEditable(event);
            requireNoScores(c,eventId);
            try(PreparedStatement s=c.prepareStatement("SELECT COUNT(*) FROM cc_users WHERE user_id=? AND role_code='JUDGE' AND active_flag='Y'")) {
                s.setLong(1,judge);try(ResultSet r=s.executeQuery()){r.next();if(r.getInt(1)!=1)throw new ApiException(409,"Only active JUDGE accounts can be assigned");}
            }
            try(PreparedStatement s=c.prepareStatement("INSERT INTO cc_event_judges(event_id,judge_user_id) VALUES(?,?)")) {
                s.setLong(1,eventId);s.setLong(2,judge);s.executeUpdate();
            }
            return judgeRow(c,eventId,judge);
        });
    }

    @Override public Map<String,Object> unassignJudge(long actor, boolean admin, long eventId, long judge) {
        return tx("Judge assignment conflicts with existing data", c -> {
            EventRow event=managerEvent(c,eventId,actor,admin,true);requireEditable(event);requireNoScores(c,eventId);
            try(PreparedStatement s=c.prepareStatement("DELETE FROM cc_event_judges WHERE event_id=? AND judge_user_id=?")) {
                s.setLong(1,eventId);s.setLong(2,judge);if(s.executeUpdate()!=1)throw new ApiException(404,"Judge assignment not found");
            }
            return Map.of("eventId",eventId,"judgeUserId",judge,"assigned",false);
        });
    }

    @Override public List<Map<String,Object>> criteria(long actor, boolean admin, long eventId) {
        return read(c -> {managerEvent(c,eventId,actor,admin,false);return criterionRows(c,eventId);});
    }

    @Override public Map<String,Object> createCriterion(long actor, boolean admin, long eventId, ValidCriterion criterion) {
        return tx("Criterion conflicts with an existing scoring record", c -> {
            EventRow event=managerEvent(c,eventId,actor,admin,true);requireEditable(event);requireNoScores(c,eventId);
            if(scalar(c,"SELECT COUNT(*) FROM cc_scoring_criteria WHERE event_id=?",eventId)>=100)
                throw new ApiException(409,"An event may have at most 100 scoring criteria");
            long id;
            try(PreparedStatement s=c.prepareStatement("INSERT INTO cc_scoring_criteria(event_id,criterion_name,max_points,weight_percent,display_order) VALUES(?,?,?,?,?)",new String[]{"CRITERION_ID"})) {
                s.setLong(1,eventId);s.setString(2,criterion.name());s.setBigDecimal(3,criterion.maxPoints());s.setBigDecimal(4,criterion.weightPercent());s.setInt(5,criterion.displayOrder());s.executeUpdate();
                try(ResultSet r=s.getGeneratedKeys()){if(!r.next())throw new SQLException("Generated criterion ID missing");id=r.getLong(1);}
            }
            return criterionRow(c,eventId,id);
        });
    }

    @Override public Map<String,Object> updateCriterion(long actor, boolean admin, long eventId, long criterionId, ValidCriterion criterion) {
        return tx("Criterion conflicts with an existing scoring record", c -> {
            EventRow event=managerEvent(c,eventId,actor,admin,true);requireEditable(event);requireNoScores(c,eventId);
            if(!exists(c,"SELECT COUNT(*) FROM cc_scoring_criteria WHERE event_id=? AND criterion_id=?",eventId,criterionId))
                throw new ApiException(404,"Scoring criterion not found");
            try(PreparedStatement s=c.prepareStatement("UPDATE cc_scoring_criteria SET criterion_name=?,max_points=?,weight_percent=?,display_order=? WHERE event_id=? AND criterion_id=?")) {
                s.setString(1,criterion.name());s.setBigDecimal(2,criterion.maxPoints());s.setBigDecimal(3,criterion.weightPercent());s.setInt(4,criterion.displayOrder());s.setLong(5,eventId);s.setLong(6,criterionId);s.executeUpdate();
            }
            return criterionRow(c,eventId,criterionId);
        });
    }

    @Override public Map<String,Object> setWindow(long actor, boolean admin, long eventId, ValidWindow window) {
        return tx("Scoring window conflicts with scoring records", c -> {
            EventRow event=managerEvent(c,eventId,actor,admin,true);requireEditable(event);requireNoScores(c,eventId);
            try(PreparedStatement s=c.prepareStatement("UPDATE cc_events SET scoring_opens=?,scoring_closes=? WHERE event_id=?")) {
                s.setObject(1,window.opens());s.setObject(2,window.closes());s.setLong(3,eventId);s.executeUpdate();
            }
            return eventMap(event(c,eventId,false));
        });
    }

    @Override public Map<String,Object> closeScoring(long actor, boolean admin, long eventId) {
        return tx("Scoring could not be closed", c -> {
            EventRow event=managerEvent(c,eventId,actor,admin,true);
            if(!"PUBLISHED".equals(event.status))throw new ApiException(409,"Only a published event can close scoring");
            if(event.opens==null||event.closes==null)throw new ApiException(409,"A scoring window is not configured");
            if(!exists(c,"SELECT COUNT(*) FROM cc_events WHERE event_id=? AND scoring_opens<=SYSTIMESTAMP",eventId))
                throw new ApiException(409,"The scoring window has not opened");
            if(event.closes.isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
                try(PreparedStatement s=c.prepareStatement("UPDATE cc_events SET scoring_closes=SYSTIMESTAMP WHERE event_id=? AND scoring_closes>SYSTIMESTAMP")) {
                    s.setLong(1,eventId);if(s.executeUpdate()!=1)throw new ApiException(409,"Scoring window is already closed");
                }
            }
            return eventMap(event(c,eventId,false));
        });
    }

    @Override public Map<String,Object> submitScores(long judge, long eventId, long registrationId, List<ValidScore> scores) {
        return tx("A score already exists for this judge and criterion", c -> {
            EventRow event=event(c,eventId,true);
            if(!"PUBLISHED".equals(event.status)||event.finalizedAt!=null)
                throw new ApiException(409,"Scoring is not open for this event");
            requireAssigned(c,judge,eventId);
            if(event.opens==null||event.closes==null||!exists(c,"SELECT COUNT(*) FROM cc_events WHERE event_id=? AND scoring_opens<=SYSTIMESTAMP AND scoring_closes>=SYSTIMESTAMP",eventId))
                throw new ApiException(409,"The scoring window is not currently open");
            requireConfirmedRegistration(c,eventId,registrationId,true);

            Map<Long,BigDecimal> maxima=new HashMap<>();
            try(PreparedStatement s=c.prepareStatement("SELECT criterion_id,max_points FROM cc_scoring_criteria WHERE event_id=?")) {
                s.setLong(1,eventId);try(ResultSet r=s.executeQuery()){while(r.next())maxima.put(r.getLong(1),r.getBigDecimal(2));}
            }
            for(ValidScore score:scores) {
                BigDecimal max=maxima.get(score.criterionId());
                ScoringAccessPolicy.requireCriterionForEvent(max!=null);
                if(score.points().compareTo(max)>0)throw new ValidationException("awardedPoints exceeds the criterion maximum");
            }
            for(ValidScore score:scores) {
                Long scoreId=existingScore(c,eventId,registrationId,score.criterionId(),judge);
                if(scoreId==null) {
                    try(PreparedStatement s=c.prepareStatement("INSERT INTO cc_scores(event_id,registration_id,criterion_id,judge_user_id,awarded_points,feedback) VALUES(?,?,?,?,?,?)")) {
                        s.setLong(1,eventId);s.setLong(2,registrationId);s.setLong(3,score.criterionId());s.setLong(4,judge);s.setBigDecimal(5,score.points());s.setString(6,score.feedback());s.executeUpdate();
                    }
                } else {
                    try(PreparedStatement s=c.prepareStatement("UPDATE cc_scores SET awarded_points=?,feedback=?,submitted_at=SYSTIMESTAMP WHERE score_id=? AND judge_user_id=?")) {
                        s.setBigDecimal(1,score.points());s.setString(2,score.feedback());s.setLong(3,scoreId);s.setLong(4,judge);
                        if(s.executeUpdate()!=1)throw new ApiException(403,"A judge may update only their own scores");
                    }
                }
            }
            return scoreBatch(c,eventId,registrationId,judge);
        });
    }

    @Override public Map<String,Object> finalizeResults(long actor, boolean admin, long eventId) {
        return tx("Scoring result finalization conflicts with existing data", c -> {
            EventRow event=managerEvent(c,eventId,actor,admin,true);
            if(!"PUBLISHED".equals(event.status)||event.finalizedAt!=null)
                throw new ApiException(409,"Only an unfinalized published event can be finalized");
            if(event.closes==null||event.closes.isAfter(OffsetDateTime.now(ZoneOffset.UTC)))
                throw new ApiException(409,"Close the scoring window before finalizing results");
            int criterionCount=scalar(c,"SELECT COUNT(*) FROM cc_scoring_criteria WHERE event_id=?",eventId);
            int judgeCount=scalar(c,"SELECT COUNT(*) FROM cc_event_judges WHERE event_id=?",eventId);
            int registrationCount=scalar(c,"SELECT COUNT(*) FROM cc_registrations WHERE event_id=? AND registration_status='CONFIRMED'",eventId);
            if(criterionCount<1||judgeCount<1||registrationCount<1)
                throw new ApiException(409,"Finalization requires criteria, assigned judges, and confirmed registrations");
            BigDecimal weights;
            try(PreparedStatement s=c.prepareStatement("SELECT SUM(weight_percent) FROM cc_scoring_criteria WHERE event_id=?")) {
                s.setLong(1,eventId);try(ResultSet r=s.executeQuery()){r.next();weights=r.getBigDecimal(1);}
            }
            if(weights==null||weights.compareTo(ONE_HUNDRED)!=0)
                throw new ApiException(409,"Criterion weights must total exactly 100 percent");
            String missingSql="SELECT COUNT(*) FROM cc_registrations r CROSS JOIN cc_event_judges j CROSS JOIN cc_scoring_criteria c "
                    +"WHERE r.event_id=? AND r.registration_status='CONFIRMED' AND j.event_id=? AND c.event_id=? "
                    +"AND NOT EXISTS (SELECT 1 FROM cc_scores s WHERE s.event_id=r.event_id AND s.registration_id=r.registration_id "
                    +"AND s.judge_user_id=j.judge_user_id AND s.criterion_id=c.criterion_id)";
            int missing=scalar(c,missingSql,eventId,eventId,eventId);
            if(missing>0)throw new ApiException(409,"Finalization requires complete scores from every assigned judge");
            try(PreparedStatement s=c.prepareStatement("UPDATE cc_events SET event_status='COMPLETED',results_finalized_at=SYSTIMESTAMP WHERE event_id=? AND event_status='PUBLISHED' AND results_finalized_at IS NULL")) {
                s.setLong(1,eventId);if(s.executeUpdate()!=1)throw new ApiException(409,"Event finalization lost a concurrent state change");
            }
            return eventMap(event(c,eventId,false));
        });
    }

    @Override public Map<String,Object> publishResults(long actor, boolean admin, long eventId) {
        return tx("Result publication conflicts with existing data", c -> {
            EventRow event=managerEvent(c,eventId,actor,admin,true);
            if(!"COMPLETED".equals(event.status)||event.finalizedAt==null)
                throw new ApiException(409,"Finalize event results before publication");
            if(event.publishedAt!=null)throw new ApiException(409,"Results are already published");
            try(PreparedStatement s=c.prepareStatement("UPDATE cc_events SET results_published_at=SYSTIMESTAMP WHERE event_id=? AND results_finalized_at IS NOT NULL AND results_published_at IS NULL")) {
                s.setLong(1,eventId);if(s.executeUpdate()!=1)throw new ApiException(409,"Result publication lost a concurrent state change");
            }
            return eventMap(event(c,eventId,false));
        });
    }

    @Override public Map<String,Object> publicResults(long eventId, int page, int pageSize) {
        return read(c -> {
            EventRow event=event(c,eventId,false);
            if(event.publishedAt==null)throw new ApiException(404,"Results are not published");
            return resultPage(c,eventId,page,pageSize);
        });
    }

    @Override public Map<String,Object> managerResults(long actor, boolean admin, long eventId,int page,int pageSize) {
        return read(c -> {
            EventRow event=managerEvent(c,eventId,actor,admin,false);
            if(event.finalizedAt==null)throw new ApiException(409,"Results are not finalized");
            return resultPage(c,eventId,page,pageSize);
        });
    }

    private static Map<String,Object> resultPage(Connection c,long eventId,int page,int pageSize)throws SQLException {
        long total=scalarLong(c,"SELECT COUNT(*) FROM v_cc_event_results WHERE event_id=?",eventId);
        List<Map<String,Object>> results=rows(c,"SELECT event_id,event_code,event_name,registration_id,registration_type,registrant_name,"
                +"weighted_score,criteria_scored,result_rank FROM v_cc_event_results WHERE event_id=? "
                +"ORDER BY result_rank,registrant_name,registration_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                eventId,(long)(page-1)*pageSize,pageSize);
        Map<String,Object> out=new LinkedHashMap<>();out.put("results",results);out.put("page",page);out.put("pageSize",pageSize);
        out.put("total",total);out.put("totalPages",total==0?0:(total+pageSize-1)/pageSize);return out;
    }
    private static List<Map<String,Object>> criterionRows(Connection c,long eventId)throws SQLException {
        return rows(c,"SELECT criterion_id,event_id,criterion_name,max_points,weight_percent,display_order "
                +"FROM cc_scoring_criteria WHERE event_id=? ORDER BY display_order,criterion_id",eventId);
    }
    private static Map<String,Object> criterionRow(Connection c,long eventId,long criterionId)throws SQLException {
        List<Map<String,Object>> found=rows(c,"SELECT criterion_id,event_id,criterion_name,max_points,weight_percent,display_order FROM cc_scoring_criteria WHERE event_id=? AND criterion_id=?",eventId,criterionId);
        if(found.isEmpty())throw new ApiException(404,"Scoring criterion not found");return found.get(0);
    }
    private static Map<String,Object> judgeRow(Connection c,long eventId,long judge)throws SQLException {
        List<Map<String,Object>> found=rows(c,"SELECT u.user_id,u.display_name,u.role_code,u.active_flag,j.assigned_at FROM cc_event_judges j JOIN cc_users u ON u.user_id=j.judge_user_id WHERE j.event_id=? AND j.judge_user_id=?",eventId,judge);
        if(found.isEmpty())throw new ApiException(404,"Judge assignment not found");return found.get(0);
    }
    private static Map<String,Object> scoreBatch(Connection c,long eventId,long registrationId,long judge)throws SQLException {
        List<Map<String,Object>> found=rows(c,"SELECT score_id,event_id,registration_id,criterion_id,judge_user_id,awarded_points,feedback,submitted_at FROM cc_scores WHERE event_id=? AND registration_id=? AND judge_user_id=? ORDER BY criterion_id",eventId,registrationId,judge);
        Map<String,Object> result=new LinkedHashMap<>();result.put("eventId",eventId);result.put("registrationId",registrationId);result.put("scores",found);return result;
    }
    private static Long existingScore(Connection c,long event,long registration,long criterion,long judge)throws SQLException {
        try(PreparedStatement s=c.prepareStatement("SELECT score_id FROM cc_scores WHERE event_id=? AND registration_id=? AND criterion_id=? AND judge_user_id=?")) {
            s.setLong(1,event);s.setLong(2,registration);s.setLong(3,criterion);s.setLong(4,judge);try(ResultSet r=s.executeQuery()){return r.next()?r.getLong(1):null;}
        }
    }
    private static void requireConfirmedRegistration(Connection c,long event,long registration,boolean lock)throws SQLException {
        String sql="SELECT event_id,registration_status FROM cc_registrations WHERE registration_id=?"+(lock?" FOR UPDATE":"");
        try(PreparedStatement s=c.prepareStatement(sql)) {
            s.setLong(1,registration);try(ResultSet r=s.executeQuery()) {
                if(!r.next())throw new ApiException(404,"Registration not found");
                ScoringAccessPolicy.requireConfirmedRegistrationForEvent(event,r.getLong(1),r.getString(2));
            }
        }
    }
    private static void requireAssigned(Connection c,long judge,long event)throws SQLException {
        if(!exists(c,"SELECT COUNT(*) FROM cc_events WHERE event_id=?",event))throw new ApiException(404,"Event not found");
        boolean assigned=exists(c,"SELECT COUNT(*) FROM cc_event_judges WHERE event_id=? AND judge_user_id=?",event,judge);
        boolean active=exists(c,"SELECT COUNT(*) FROM cc_users WHERE user_id=? AND role_code='JUDGE' AND active_flag='Y'",judge);
        ScoringAccessPolicy.requireAssignedActiveJudge("JUDGE",assigned,active);
    }
    private static void requireNoScores(Connection c,long event)throws SQLException {
        if(scalar(c,"SELECT COUNT(*) FROM cc_scores WHERE event_id=?",event)>0)
            throw new ApiException(409,"Judge assignments and criteria are frozen after the first score");
    }
    private static void requireEditable(EventRow event) {
        if(!Set.of("DRAFT","PUBLISHED").contains(event.status)||event.finalizedAt!=null)
            throw new ApiException(409,"Scoring configuration is frozen for this event");
    }
    private static EventRow managerEvent(Connection c,long id,long actor,boolean admin,boolean lock)throws SQLException {
        EventRow row=event(c,id,lock);
        ScoringAccessPolicy.requireManager(actor,admin,row.ownerId);
        return row;
    }
    private static EventRow event(Connection c,long id,boolean lock)throws SQLException {
        String sql="SELECT event_id,event_code,event_name,owner_user_id,event_status,starts_at,ends_at,scoring_opens,scoring_closes,results_finalized_at,results_published_at FROM cc_events WHERE event_id=?"+(lock?" FOR UPDATE":"");
        try(PreparedStatement s=c.prepareStatement(sql)) {
            s.setLong(1,id);try(ResultSet r=s.executeQuery()) {
                if(!r.next())throw new ApiException(404,"Event not found");
                return new EventRow(r.getLong("event_id"),r.getString("event_code"),r.getString("event_name"),r.getLong("owner_user_id"),r.getString("event_status"),date(r,"starts_at"),date(r,"ends_at"),date(r,"scoring_opens"),date(r,"scoring_closes"),date(r,"results_finalized_at"),date(r,"results_published_at"));
            }
        }
    }
    private static Map<String,Object> eventMap(EventRow e) {
        Map<String,Object> m=new LinkedHashMap<>();m.put("eventId",e.id);m.put("eventCode",e.code);m.put("eventName",e.name);m.put("ownerUserId",e.ownerId);m.put("eventStatus",e.status);
        m.put("startsAt",iso(e.startsAt));m.put("endsAt",iso(e.endsAt));m.put("scoringOpens",iso(e.opens));m.put("scoringCloses",iso(e.closes));m.put("resultsFinalizedAt",iso(e.finalizedAt));m.put("resultsPublishedAt",iso(e.publishedAt));return m;
    }
    private static OffsetDateTime date(ResultSet r,String label)throws SQLException {
        Timestamp ts=r.getTimestamp(label);return ts==null?null:ts.toInstant().atOffset(ZoneOffset.UTC);
    }
    private static String iso(OffsetDateTime value){return value==null?null:value.toInstant().toString();}
    private static int scalar(Connection c,String sql,Object... args)throws SQLException {
        try(PreparedStatement s=c.prepareStatement(sql)){bind(s,args);try(ResultSet r=s.executeQuery()){r.next();return r.getInt(1);}}
    }
    private static long scalarLong(Connection c,String sql,Object... args)throws SQLException {
        try(PreparedStatement s=c.prepareStatement(sql)){bind(s,args);try(ResultSet r=s.executeQuery()){r.next();return r.getLong(1);}}
    }
    private static boolean exists(Connection c,String sql,Object... args)throws SQLException{return scalar(c,sql,args)>0;}
    private static void bind(PreparedStatement s,Object... args)throws SQLException {
        for(int i=0;i<args.length;i++) {
            Object value=args[i];int index=i+1;
            if(value instanceof OffsetDateTime time)s.setObject(index,time);
            else if(value instanceof BigDecimal decimal)s.setBigDecimal(index,decimal);
            else s.setObject(index,value);
        }
    }
    private static List<Map<String,Object>> rows(Connection c,String sql,Object... args)throws SQLException {
        try(PreparedStatement s=c.prepareStatement(sql)){bind(s,args);try(ResultSet r=s.executeQuery()){
            ResultSetMetaData md=r.getMetaData();List<Map<String,Object>> out=new ArrayList<>();
            while(r.next()){Map<String,Object> row=new LinkedHashMap<>();for(int i=1;i<=md.getColumnCount();i++){
                String name=camel(md.getColumnLabel(i).toLowerCase(Locale.ROOT));int type=md.getColumnType(i);String typeName=md.getColumnTypeName(i).toUpperCase(Locale.ROOT);Object value;
                if(type==Types.TIMESTAMP_WITH_TIMEZONE||type==Types.TIMESTAMP||type==-100||type==-101||type==-102||typeName.contains("TIMESTAMP")){Timestamp ts=r.getTimestamp(i);value=ts==null?null:ts.toInstant().toString();}
                else value=r.getObject(i);row.put(name,value);
            }out.add(row);}return out;
        }}
    }
    private static String camel(String label){StringBuilder out=new StringBuilder();boolean upper=false;for(char ch:label.toCharArray()){if(ch=='_'){upper=true;continue;}out.append(upper?Character.toUpperCase(ch):ch);upper=false;}return out.toString();}
    private static boolean isUnique(SQLException e){for(SQLException x=e;x!=null;x=x.getNextException())if(x.getErrorCode()==1)return true;return false;}
    private static void rollback(Connection c,Throwable e){try{c.rollback();}catch(SQLException x){e.addSuppressed(x);}}
    @FunctionalInterface private interface Work<T>{T run(Connection c)throws SQLException;}
    private static <T>T read(Work<T> work) {
        try(Connection c=DBConnection.getConnection()){return work.run(c);}catch(ApiException|ValidationException e){throw e;}catch(SQLException|IOException e){throw new IllegalStateException("Scoring database operation failed",e);}
    }
    private static <T>T tx(String conflictMessage,Work<T> work) {
        try(Connection c=DBConnection.getConnection()) {
            try{return JdbcTransactionRunner.run(c,work::run);}
            catch(RuntimeException e){throw e;}
            catch(SQLException e){if(isUnique(e))throw new ApiException(409,conflictMessage);throw new IllegalStateException("Scoring database operation failed",e);}
        }catch(ApiException|ValidationException e){throw e;}catch(SQLException|IOException e){throw new IllegalStateException("Scoring database operation failed",e);}
    }
    private record EventRow(long id,String code,String name,long ownerId,String status,OffsetDateTime startsAt,OffsetDateTime endsAt,
                            OffsetDateTime opens,OffsetDateTime closes,OffsetDateTime finalizedAt,OffsetDateTime publishedAt) { }
}
