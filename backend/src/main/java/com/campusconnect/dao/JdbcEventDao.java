package com.campusconnect.dao;

import com.campusconnect.exception.ApiException;
import com.campusconnect.exception.EventConflictException;
import com.campusconnect.model.EventData;
import com.campusconnect.model.EventPage;
import com.campusconnect.model.EventSearch;
import com.campusconnect.service.EventInputValidator.ValidEvent;
import com.campusconnect.service.EventAccessPolicy;
import com.campusconnect.service.EventEditPolicy;
import com.campusconnect.service.EventLifecycle;
import com.campusconnect.util.DBConnection;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public final class JdbcEventDao implements EventDao {
    private static final String BASE_SELECT = "SELECT e.event_id,e.event_code,e.event_name,e.description,e.owner_user_id,"
            + "e.participation_mode,e.event_status,e.registration_opens,e.registration_closes,e.starts_at,e.ends_at,"
            + "e.capacity,e.location,e.created_at,"
            + "(SELECT COUNT(*) FROM cc_registrations r WHERE r.event_id=e.event_id) registration_count,"
            + "(SELECT COUNT(*) FROM cc_registrations r WHERE r.event_id=e.event_id AND r.registration_status='CONFIRMED') confirmed_count,"
            + "CASE WHEN e.event_status='PUBLISHED' AND e.registration_opens<=SYSTIMESTAMP "
            + "AND e.registration_closes>=SYSTIMESTAMP THEN 1 ELSE 0 END registration_open FROM cc_events e ";
    private static final String PUBLIC = " WHERE e.event_status IN ('PUBLISHED','CANCELLED','COMPLETED') ";

    @Override public EventPage listPublic(EventSearch search) { return list(search, true, 0, false); }
    @Override public EventData findPublic(long id) {
        try (Connection c = DBConnection.getConnection(); PreparedStatement s = c.prepareStatement(BASE_SELECT + PUBLIC + " AND e.event_id=?")) {
            s.setLong(1,id); try(ResultSet r=s.executeQuery()){return r.next()?map(r):null;}
        } catch(IOException|SQLException e){throw dbError("Public event lookup failed",e);}
    }
    @Override public EventPage listManaged(long actorId, boolean admin, EventSearch search) { return list(search,false,actorId,admin); }
    @Override public EventData findManaged(long actorId, boolean admin, long eventId) {
        try (Connection c=DBConnection.getConnection()) {
            return findManaged(c,eventId,actorId,admin);
        } catch(IOException|SQLException e) { throw dbError("Managed event lookup failed",e); }
    }

    private EventPage list(EventSearch q, boolean publicOnly, long actorId, boolean admin) {
        StringBuilder where = new StringBuilder(publicOnly ? PUBLIC : " WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (!publicOnly && !admin) { where.append(" AND e.owner_user_id=?"); args.add(actorId); }
        if (q.query()!=null) { where.append(" AND LOWER(e.event_name) LIKE ? ESCAPE '\\'"); args.add("%"+escapeLike(q.query().toLowerCase())+"%"); }
        if (q.participationMode()!=null) { where.append(" AND e.participation_mode=?"); args.add(q.participationMode()); }
        if (q.startsAfter()!=null) { where.append(" AND e.starts_at>=?"); args.add(q.startsAfter()); }
        if (q.startsBefore()!=null) { where.append(" AND e.starts_at<=?"); args.add(q.startsBefore()); }
        if (q.registrationOpen()!=null) {
            where.append(q.registrationOpen()
                    ? " AND e.event_status='PUBLISHED' AND e.registration_opens<=SYSTIMESTAMP AND e.registration_closes>=SYSTIMESTAMP"
                    : " AND (e.event_status<>'PUBLISHED' OR e.registration_opens>SYSTIMESTAMP OR e.registration_closes<SYSTIMESTAMP)");
        }
        String order = switch(q.sort()) { case "eventName" -> "e.event_name"; case "createdAt" -> "e.created_at"; default -> "e.starts_at"; };
        String direction = "desc".equals(q.direction()) ? "DESC" : "ASC";
        String from = " FROM cc_events e " + where;
        try(Connection c=DBConnection.getConnection()) {
            long total;
            try(PreparedStatement count=c.prepareStatement("SELECT COUNT(*)"+from)) {
                bind(count,args); try(ResultSet r=count.executeQuery()){r.next();total=r.getLong(1);}
            }
            List<EventData> events=new ArrayList<>();
            String sql=BASE_SELECT+where+" ORDER BY "+order+" "+direction+", e.event_id ASC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
            try(PreparedStatement s=c.prepareStatement(sql)) {
                int ix=bind(s,args); long offset=(long)(q.page()-1)*q.pageSize(); s.setLong(ix++,offset);s.setInt(ix,q.pageSize());
                try(ResultSet r=s.executeQuery()){while(r.next()) events.add(map(r));}
            }
            return new EventPage(List.copyOf(events),q.page(),q.pageSize(),total);
        } catch(IOException|SQLException e){throw dbError("Event listing failed",e);}
    }
    private static String escapeLike(String text){return text.replace("\\","\\\\").replace("%","\\%").replace("_","\\_");}
    private static int bind(PreparedStatement s,List<Object> values)throws SQLException {int i=1;for(Object value:values){if(value instanceof OffsetDateTime d)s.setObject(i++,d);else s.setObject(i++,value);}return i;}

    @Override public EventData create(long actorId, ValidEvent v) {
        String sql="INSERT INTO cc_events(event_code,event_name,description,owner_user_id,participation_mode,event_status,"
                +"registration_opens,registration_closes,starts_at,ends_at,capacity,location) VALUES(?,?,?,?,?,'DRAFT',?,?,?,?,?,?)";
        try(Connection c=DBConnection.getConnection();PreparedStatement s=c.prepareStatement(sql,new String[]{"EVENT_ID"})) {
            bindEvent(s,actorId,v);s.executeUpdate();try(ResultSet keys=s.getGeneratedKeys()){if(!keys.next())throw new SQLException("No event identity generated");return findManaged(c,keys.getLong(1),actorId,true);}
        }catch(SQLException|IOException e){if(isUnique(e))throw new EventConflictException("Event code already exists");throw dbError("Event creation failed",e);}
    }
    @Override public EventData update(long actorId,boolean admin,long eventId,ValidEvent v) {
        try(Connection c=DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                lockOwner(c,eventId,actorId,admin);
                EventData old=findManaged(c,eventId,actorId,admin);
                if(old==null){c.rollback();throw new ApiException(404,"Event not found");}
                int registrations=scalar(c,"SELECT COUNT(*) FROM cc_registrations WHERE event_id=?",eventId);
                int confirmed=scalar(c,"SELECT COUNT(*) FROM cc_registrations WHERE event_id=? AND registration_status='CONFIRMED'",eventId);
                int scores=scalar(c,"SELECT COUNT(*) FROM cc_scores WHERE event_id=?",eventId);
                int outsideWindow=0;
                if(registrations>0) {
                    try(PreparedStatement s=c.prepareStatement("SELECT COUNT(*) FROM cc_registrations WHERE event_id=? AND (registered_at<? OR registered_at>? OR registered_at>=?)")) {
                        s.setLong(1,eventId);s.setObject(2,v.registrationOpens());s.setObject(3,v.registrationCloses());s.setObject(4,v.startsAt());
                        try(ResultSet r=s.executeQuery()){r.next();outsideWindow=r.getInt(1);}
                    }
                }
                EventEditPolicy.validate(old,v.eventCode(),v.mode(),v.registrationOpens(),v.registrationCloses(),v.startsAt(),v.endsAt(),
                        v.capacity(),registrations,confirmed,scores,outsideWindow);
                String sql="UPDATE cc_events SET event_name=?,description=?,participation_mode=?,registration_opens=?,registration_closes=?,starts_at=?,ends_at=?,capacity=?,location=? WHERE event_id=?";
                try(PreparedStatement s=c.prepareStatement(sql)){s.setString(1,v.eventName());s.setString(2,v.description());s.setString(3,v.mode());s.setObject(4,v.registrationOpens());s.setObject(5,v.registrationCloses());s.setObject(6,v.startsAt());s.setObject(7,v.endsAt());if(v.capacity()==null)s.setNull(8,java.sql.Types.NUMERIC);else s.setInt(8,v.capacity());s.setString(9,v.location());s.setLong(10,eventId);s.executeUpdate();}
                EventData result=findManaged(c,eventId,actorId,true);c.commit();return result;
            }catch(RuntimeException|SQLException e){rollback(c,e);throw e;}
        }catch(ApiException|EventConflictException e){throw e;}catch(SQLException|IOException e){throw dbError("Event update failed",e);}
    }

    @Override public EventData transition(long actorId,boolean admin,long eventId,String action) {
        try(Connection c=DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                lockOwner(c,eventId,actorId,admin);
                EventData event=findManaged(c,eventId,actorId,admin);
                if(event==null){c.rollback();throw new ApiException(404,"Event not found");}
                String status=event.eventStatus();
                String sql;
                if("publish".equals(action)) {
                    EventLifecycle.nextStatus(status,action);
                    sql="UPDATE cc_events SET event_status='PUBLISHED' WHERE event_id=?";
                } else if("cancel".equals(action)) {
                    EventLifecycle.nextStatus(status,action);
                    sql="UPDATE cc_events SET event_status='CANCELLED' WHERE event_id=?";
                } else if("close-registration".equals(action)) {
                    EventLifecycle.nextStatus(status,action);
                    if(!event.registrationOpen())throw new EventConflictException("Registration is not currently open");
                    sql="UPDATE cc_events SET registration_closes=SYSTIMESTAMP WHERE event_id=? AND registration_opens<=SYSTIMESTAMP AND registration_closes>SYSTIMESTAMP AND starts_at>=SYSTIMESTAMP";
                } else throw new ApiException(404,"Event action not found");
                try(PreparedStatement s=c.prepareStatement(sql)){s.setLong(1,eventId);if(s.executeUpdate()!=1)throw new EventConflictException("Event transition could not be applied");}
                EventData result=findManaged(c,eventId,actorId,true);c.commit();return result;
            }catch(RuntimeException|SQLException e){rollback(c,e);throw e;}
        }catch(ApiException|EventConflictException e){throw e;}catch(SQLException|IOException e){throw dbError("Event status change failed",e);}
    }

    private static EventData findManaged(Connection c,long id,long actorId,boolean admin)throws SQLException {
        String sql=BASE_SELECT+" WHERE e.event_id=?";
        try(PreparedStatement s=c.prepareStatement(sql)){s.setLong(1,id);try(ResultSet r=s.executeQuery()){if(!r.next())return null;EventData d=map(r);if(!admin&&d.ownerUserId()!=actorId)throw new ApiException(403,"You may manage only your own events");return d;}}
    }
    private static void lockOwner(Connection c,long id,long actorId,boolean admin)throws SQLException {
        try(PreparedStatement s=c.prepareStatement("SELECT owner_user_id FROM cc_events WHERE event_id=? FOR UPDATE")) {
            s.setLong(1,id);try(ResultSet r=s.executeQuery()) {
                if(!r.next())throw new ApiException(404,"Event not found");
                if(!EventAccessPolicy.canManage(actorId,admin,r.getLong(1)))throw new ApiException(403,"You may manage only your own events");
            }
        }
    }
    private static int scalar(Connection c,String sql,long id)throws SQLException{try(PreparedStatement s=c.prepareStatement(sql)){s.setLong(1,id);try(ResultSet r=s.executeQuery()){r.next();return r.getInt(1);}}}
    private static void bindEvent(PreparedStatement s,long actorId,ValidEvent v)throws SQLException {
        s.setString(1,v.eventCode());s.setString(2,v.eventName());s.setString(3,v.description());s.setLong(4,actorId);s.setString(5,v.mode());
        s.setObject(6,v.registrationOpens());s.setObject(7,v.registrationCloses());s.setObject(8,v.startsAt());s.setObject(9,v.endsAt());
        if(v.capacity()==null)s.setNull(10,java.sql.Types.NUMERIC);else s.setInt(10,v.capacity());s.setString(11,v.location());
    }
    private static EventData map(ResultSet r)throws SQLException {
        return new EventData(r.getLong("event_id"),r.getString("event_code"),r.getString("event_name"),r.getString("description"),
                r.getLong("owner_user_id"),r.getString("participation_mode"),r.getString("event_status"),date(r,"registration_opens"),
                date(r,"registration_closes"),date(r,"starts_at"),date(r,"ends_at"),nullableInt(r,"capacity"),r.getString("location"),
                date(r,"created_at"),r.getInt("registration_count"),r.getInt("confirmed_count"),r.getInt("registration_open")==1);
    }
    private static OffsetDateTime date(ResultSet r,String name)throws SQLException {var t=r.getTimestamp(name);return t==null?null:t.toInstant().atOffset(ZoneOffset.UTC);}
    private static Integer nullableInt(ResultSet r,String name)throws SQLException {int v=r.getInt(name);return r.wasNull()?null:v;}
    private static void rollback(Connection c,Throwable e){try{c.rollback();}catch(SQLException x){e.addSuppressed(x);}}
    private static boolean isUnique(Throwable e){for(Throwable t=e;t!=null;t=t.getCause())if(t instanceof SQLException s&&s.getErrorCode()==1)return true;return false;}
    private static IllegalStateException dbError(String message,Throwable e){return new IllegalStateException(message,e);}
}
