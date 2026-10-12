package com.campusconnect.dao;

import com.campusconnect.exception.ApiException;
import com.campusconnect.service.AdminAccountPolicy;
import com.campusconnect.util.DBConnection;
import java.io.IOException;
import java.sql.*;
import java.util.*;

public final class JdbcAdminDao implements AdminDao {
    private static final Set<String> ROLES = Set.of("STUDENT", "ORGANIZER", "JUDGE", "ADMIN");

    @Override public Map<String,Object> listUsers(String query,String role,Boolean active,String sort,String direction,int page,int size) {
        StringBuilder where=new StringBuilder(" WHERE 1=1"); List<Object> args=new ArrayList<>();
        if(query!=null&&!query.isBlank()){where.append(" AND (LOWER(display_name) LIKE ? ESCAPE '\\' OR LOWER(email) LIKE ? ESCAPE '\\')");String q="%"+escapeLike(query.toLowerCase(Locale.ROOT))+"%";args.add(q);args.add(q);}
        if(role!=null){where.append(" AND role_code=?");args.add(role);} if(active!=null){where.append(" AND active_flag=?");args.add(active?"Y":"N");}
        String order=switch(sort){case "email"->"email";case "role"->"role_code";case "active"->"active_flag";case "createdAt"->"created_at";default->"display_name";};
        String dir="asc".equals(direction)?"ASC":"DESC"; String from=" FROM cc_users"+where;
        try(Connection c=DBConnection.getConnection()){
            long total;try(PreparedStatement s=c.prepareStatement("SELECT COUNT(*)"+from)){bind(s,args);try(ResultSet r=s.executeQuery()){r.next();total=r.getLong(1);}}
            List<Map<String,Object>> users=new ArrayList<>();String sql="SELECT user_id,display_name,email,role_code,active_flag,created_at FROM cc_users"+where+" ORDER BY "+order+" "+dir+",user_id ASC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
            try(PreparedStatement s=c.prepareStatement(sql)){int i=bind(s,args);s.setLong(i++,(long)(page-1)*size);s.setInt(i,size);try(ResultSet r=s.executeQuery()){while(r.next())users.add(user(r));}}
            return page(users,page,size,total);
        }catch(SQLException|IOException e){throw db("User listing failed",e);}
    }
    @Override public Map<String,Object> findUser(long id){
        String sql="SELECT u.user_id,u.display_name,u.email,u.role_code,u.active_flag,u.created_at,"
                +"(SELECT COUNT(*) FROM cc_team_members m WHERE m.user_id=u.user_id) team_memberships,"
                +"(SELECT COUNT(*) FROM cc_registrations r WHERE r.registration_status IN ('CONFIRMED','WAITLISTED') AND (r.participant_user_id=u.user_id OR EXISTS (SELECT 1 FROM cc_team_members tm WHERE tm.team_id=r.team_id AND tm.user_id=u.user_id))) active_registrations,"
                +"(SELECT COUNT(*) FROM cc_events e WHERE e.owner_user_id=u.user_id) owned_events,"
                +"(SELECT COUNT(*) FROM cc_event_judges j WHERE j.judge_user_id=u.user_id) judge_assignments "
                +"FROM cc_users u WHERE u.user_id=?";
        try(Connection c=DBConnection.getConnection();PreparedStatement s=c.prepareStatement(sql)){s.setLong(1,id);try(ResultSet r=s.executeQuery()){if(!r.next())throw new ApiException(404,"User not found");Map<String,Object> m=user(r);m.put("teamMemberships",r.getLong("team_memberships"));m.put("activeRegistrations",r.getLong("active_registrations"));m.put("ownedEvents",r.getLong("owned_events"));m.put("judgeAssignments",r.getLong("judge_assignments"));return m;}}
        catch(ApiException e){throw e;}catch(SQLException|IOException e){throw db("User lookup failed",e);}
    }

    @Override public Map<String,Object> changeRole(long actor,long target,String role){return change(actor,target,role,null);}
    @Override public Map<String,Object> changeActive(long actor,long target,boolean active){return change(actor,target,null,active);}
    private Map<String,Object> change(long actor,long target,String newRole,Boolean newActive){
        try(Connection c=DBConnection.getConnection()){
            c.setAutoCommit(false);
            try{
                lockControl(c);
                String oldRole;boolean oldActive;
                try(PreparedStatement s=c.prepareStatement("SELECT role_code,active_flag FROM cc_users WHERE user_id=? FOR UPDATE")){s.setLong(1,target);try(ResultSet r=s.executeQuery()){if(!r.next())throw new ApiException(404,"User not found");oldRole=r.getString(1);oldActive="Y".equals(r.getString(2));}}
                String role=newRole==null?oldRole:newRole;boolean active=newActive==null?oldActive:newActive;
                if(newRole!=null&&!ROLES.contains(role))throw new ApiException(400,"Role must be STUDENT, ORGANIZER, JUDGE, or ADMIN");
                if(role.equals(oldRole)&&active==oldActive)throw new ApiException(409,"Account already has the requested value");
                AdminAccountPolicy.protectLastActiveAdmin(oldRole,oldActive,role,active,
                        count(c,"SELECT COUNT(*) FROM cc_users WHERE role_code='ADMIN' AND active_flag='Y'"));
                if(newRole!=null&&!role.equals(oldRole)){
                    AdminAccountPolicy.validateRoleChange(oldRole,role,
                            count(c,"SELECT COUNT(*) FROM cc_team_members WHERE user_id=?",target),
                            count(c,"SELECT COUNT(*) FROM cc_registrations WHERE participant_user_id=? AND registration_status IN ('CONFIRMED','WAITLISTED')",target),
                            count(c,"SELECT COUNT(*) FROM cc_events WHERE owner_user_id=?",target),
                            count(c,"SELECT COUNT(*) FROM cc_event_judges WHERE judge_user_id=?",target));
                }
                if(newActive!=null&&!active&&oldActive)AdminAccountPolicy.validateDeactivation(oldRole,
                        "JUDGE".equals(oldRole)?count(c,"SELECT COUNT(*) FROM cc_event_judges j JOIN cc_events e ON e.event_id=j.event_id WHERE j.judge_user_id=? AND e.event_status IN ('DRAFT','PUBLISHED')",target):0);
                String action,previous,value;
                if(newRole!=null){action="ROLE_CHANGE";previous=oldRole;value=role;try(PreparedStatement s=c.prepareStatement("UPDATE cc_users SET role_code=? WHERE user_id=?")){s.setString(1,role);s.setLong(2,target);s.executeUpdate();}}
                else {action=active?"ACTIVATE":"DEACTIVATE";previous=oldActive?"ACTIVE":"INACTIVE";value=active?"ACTIVE":"INACTIVE";try(PreparedStatement s=c.prepareStatement("UPDATE cc_users SET active_flag=? WHERE user_id=?")){s.setString(1,active?"Y":"N");s.setLong(2,target);s.executeUpdate();}}
                try(PreparedStatement s=c.prepareStatement("INSERT INTO cc_admin_audit(actor_user_id,target_user_id,action_code,previous_value,new_value) VALUES(?,?,?,?,?)")){s.setLong(1,actor);s.setLong(2,target);s.setString(3,action);s.setString(4,previous);s.setString(5,value);s.executeUpdate();}
                c.commit();return findUser(target);
            }catch(SQLException|RuntimeException e){try{c.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}throw e;}
        }catch(ApiException e){throw e;}catch(SQLException|IOException e){throw db("Account change failed",e);}
    }
    private static void lockControl(Connection c)throws SQLException{try(PreparedStatement s=c.prepareStatement("SELECT control_id FROM cc_admin_control WHERE control_id=1 FOR UPDATE")){try(ResultSet r=s.executeQuery()){if(!r.next())throw new SQLException("Admin control lock row is missing");}}}
    @Override public Map<String,Object> dashboard(){try(Connection c=DBConnection.getConnection()){
        Map<String,Object> summary=new LinkedHashMap<>();summary.put("users",count(c,"SELECT COUNT(*) FROM cc_users"));summary.put("activeUsers",count(c,"SELECT COUNT(*) FROM cc_users WHERE active_flag='Y'"));summary.put("events",count(c,"SELECT COUNT(*) FROM cc_events"));summary.put("registrations",count(c,"SELECT COUNT(*) FROM cc_registrations"));summary.put("scores",count(c,"SELECT COUNT(*) FROM cc_scores"));summary.put("publishedResults",count(c,"SELECT COUNT(*) FROM cc_events WHERE results_published_at IS NOT NULL"));
        summary.put("usersByRole",grouped(c,"SELECT role_code,COUNT(*) FROM cc_users GROUP BY role_code ORDER BY role_code"));summary.put("eventsByStatus",grouped(c,"SELECT event_status,COUNT(*) FROM cc_events GROUP BY event_status ORDER BY event_status"));summary.put("registrationsByStatus",grouped(c,"SELECT registration_status,COUNT(*) FROM cc_registrations GROUP BY registration_status ORDER BY registration_status"));return summary;
    }catch(SQLException|IOException e){throw db("Dashboard query failed",e);}}
    @Override public Map<String,Object> listRegistrations(Long eventId,String status,int page,int size){
        StringBuilder where=new StringBuilder(" WHERE 1=1");List<Object> args=new ArrayList<>();if(eventId!=null){where.append(" AND r.event_id=?");args.add(eventId);}if(status!=null){where.append(" AND r.registration_status=?");args.add(status);}
        String from=" FROM cc_registrations r JOIN cc_events e ON e.event_id=r.event_id LEFT JOIN cc_users u ON u.user_id=r.participant_user_id LEFT JOIN cc_teams t ON t.team_id=r.team_id"+where;
        try(Connection c=DBConnection.getConnection()){long total;try(PreparedStatement s=c.prepareStatement("SELECT COUNT(*)"+from)){bind(s,args);try(ResultSet r=s.executeQuery()){r.next();total=r.getLong(1);}}
            List<Map<String,Object>> rows=new ArrayList<>();String sql="SELECT r.registration_id,r.event_id,e.event_code,e.event_name,r.registration_type,r.participant_user_id,u.display_name participant_name,r.team_id,t.team_name,r.registration_status,r.registered_at"+from+" ORDER BY r.registered_at DESC,r.registration_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
            try(PreparedStatement s=c.prepareStatement(sql)){int i=bind(s,args);s.setLong(i++,(long)(page-1)*size);s.setInt(i,size);try(ResultSet r=s.executeQuery()){while(r.next()){Map<String,Object> m=new LinkedHashMap<>();m.put("registrationId",r.getLong("registration_id"));m.put("eventId",r.getLong("event_id"));m.put("eventCode",r.getString("event_code"));m.put("eventName",r.getString("event_name"));m.put("registrationType",r.getString("registration_type"));m.put("participantUserId",nullable(r,"participant_user_id"));m.put("participantName",r.getString("participant_name"));m.put("teamId",nullable(r,"team_id"));m.put("teamName",r.getString("team_name"));m.put("status",r.getString("registration_status"));m.put("registeredAt",r.getString("registered_at"));rows.add(m);}}}
            return page(rows,page,size,total);
        }catch(SQLException|IOException e){throw db("Registration listing failed",e);}
    }
    @Override public Map<String,Object> listAudit(Long actor,Long target,String action,int page,int size){StringBuilder where=new StringBuilder(" WHERE 1=1");List<Object> args=new ArrayList<>();if(actor!=null){where.append(" AND a.actor_user_id=?");args.add(actor);}if(target!=null){where.append(" AND a.target_user_id=?");args.add(target);}if(action!=null){where.append(" AND a.action_code=?");args.add(action);}String from=" FROM cc_admin_audit a JOIN cc_users au ON au.user_id=a.actor_user_id JOIN cc_users tu ON tu.user_id=a.target_user_id"+where;
        try(Connection c=DBConnection.getConnection()){long total;try(PreparedStatement s=c.prepareStatement("SELECT COUNT(*)"+from)){bind(s,args);try(ResultSet r=s.executeQuery()){r.next();total=r.getLong(1);}}List<Map<String,Object>> rows=new ArrayList<>();String sql="SELECT a.audit_id,a.actor_user_id,au.display_name actor_name,a.target_user_id,tu.display_name target_name,a.action_code,a.previous_value,a.new_value,a.created_at"+from+" ORDER BY a.created_at DESC,a.audit_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";try(PreparedStatement s=c.prepareStatement(sql)){int i=bind(s,args);s.setLong(i++,(long)(page-1)*size);s.setInt(i,size);try(ResultSet r=s.executeQuery()){while(r.next()){Map<String,Object> m=new LinkedHashMap<>();m.put("auditId",r.getLong("audit_id"));m.put("actorUserId",r.getLong("actor_user_id"));m.put("actorDisplayName",r.getString("actor_name"));m.put("targetUserId",r.getLong("target_user_id"));m.put("targetDisplayName",r.getString("target_name"));m.put("action",r.getString("action_code"));m.put("previousValue",r.getString("previous_value"));m.put("newValue",r.getString("new_value"));m.put("createdAt",r.getString("created_at"));rows.add(m);}}}return page(rows,page,size,total);
        }catch(SQLException|IOException e){throw db("Audit listing failed",e);}}
    private static List<Map<String,Object>> grouped(Connection c,String sql)throws SQLException{List<Map<String,Object>> rows=new ArrayList<>();try(Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){while(r.next())rows.add(Map.of("key",r.getString(1),"count",r.getLong(2)));}return rows;}
    private static long count(Connection c,String sql,Object...args)throws SQLException{try(PreparedStatement s=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);try(ResultSet r=s.executeQuery()){r.next();return r.getLong(1);}}}
    private static int bind(PreparedStatement s,List<Object> args)throws SQLException{int i=1;for(Object a:args)s.setObject(i++,a);return i;}
    private static Long nullable(ResultSet r,String col)throws SQLException{long n=r.getLong(col);return r.wasNull()?null:n;}
    private static Map<String,Object> user(ResultSet r)throws SQLException{Map<String,Object> m=new LinkedHashMap<>();m.put("userId",r.getLong("user_id"));m.put("displayName",r.getString("display_name"));m.put("email",r.getString("email"));m.put("role",r.getString("role_code"));m.put("active", "Y".equals(r.getString("active_flag")));m.put("createdAt",r.getString("created_at"));return m;}
    private static Map<String,Object> page(List<?> rows,int page,int size,long total){Map<String,Object> m=new LinkedHashMap<>();m.put("items",rows);m.put("page",page);m.put("pageSize",size);m.put("total",total);m.put("totalPages",(total+size-1)/size);return m;}
    private static String escapeLike(String s){return s.replace("\\","\\\\").replace("%","\\%").replace("_","\\_");}
    private static IllegalStateException db(String action,Exception e){return new IllegalStateException(action,e);}
}
