package com.campusconnect.service;

import com.campusconnect.dao.AdminDao;
import com.campusconnect.dao.JdbcAdminDao;
import com.campusconnect.exception.ApiException;
import com.campusconnect.exception.ValidationException;
import com.campusconnect.model.AuthPrincipal;
import com.campusconnect.model.EventPage;
import com.campusconnect.model.EventSearch;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class AdminService {
    private static final Set<String> ROLES=Set.of("STUDENT","ORGANIZER","JUDGE","ADMIN");
    private static final Set<String> SORTS=Set.of("displayName","email","role","active","createdAt");
    private static final Set<String> STATUSES=Set.of("CONFIRMED","WAITLISTED","CANCELLED");
    private final AdminDao dao;
    private final EventService events;
    public AdminService(){this(new JdbcAdminDao(),new EventService());}
    public AdminService(AdminDao dao,EventService events){this.dao=dao;this.events=events;}

    public Map<String,Object> users(AuthPrincipal actor,String query,String role,String active,String sort,String direction,String page,String size){admin(actor);
        String q=trim(query,100,"q");String r=role==null?null:role.strip().toUpperCase(Locale.ROOT);if(r!=null&&!ROLES.contains(r))throw new ValidationException("role must be STUDENT, ORGANIZER, JUDGE, or ADMIN");
        Boolean a=null;if(active!=null){if("true".equalsIgnoreCase(active))a=true;else if("false".equalsIgnoreCase(active))a=false;else throw new ValidationException("active must be true or false");}
        String sortBy=sort==null?"displayName":sort;if(!SORTS.contains(sortBy))throw new ValidationException("sort must be displayName, email, role, active, or createdAt");String dir=direction==null?"desc":direction.toLowerCase(Locale.ROOT);if(!Set.of("asc","desc").contains(dir))throw new ValidationException("direction must be asc or desc");
        return dao.listUsers(q,r,a,sortBy,dir,page(page,1,"page"),pageSize(size,25));}
    public Map<String,Object> user(AuthPrincipal actor,long id){admin(actor);return dao.findUser(id);}
    public Map<String,Object> role(AuthPrincipal actor,long id,String role){admin(actor);if(role==null)throw new ValidationException("role is required");String value=role.strip().toUpperCase(Locale.ROOT);if(!ROLES.contains(value))throw new ValidationException("role must be STUDENT, ORGANIZER, JUDGE, or ADMIN");return dao.changeRole(actor.userId(),id,value);}
    public Map<String,Object> active(AuthPrincipal actor,long id,Boolean active){admin(actor);if(active==null)throw new ValidationException("active is required");return dao.changeActive(actor.userId(),id,active);}
    public EventPage events(AuthPrincipal actor,String q,String mode,String after,String before,String open,String sort,String direction,String page,String size){admin(actor);String query=trim(q,160,"q");String participation=mode==null?null:mode.strip().toUpperCase(Locale.ROOT);if(participation!=null&&!Set.of("INDIVIDUAL","TEAM").contains(participation))throw new ValidationException("mode must be INDIVIDUAL or TEAM");Boolean registrationOpen=null;if(open!=null){if("true".equalsIgnoreCase(open))registrationOpen=true;else if("false".equalsIgnoreCase(open))registrationOpen=false;else throw new ValidationException("registrationOpen must be true or false");}
        String sortBy=sort==null?"startsAt":sort;if(!Set.of("startsAt","eventName","createdAt").contains(sortBy))throw new ValidationException("sort must be startsAt, eventName, or createdAt");String dir=direction==null?"asc":direction.toLowerCase(Locale.ROOT);if(!Set.of("asc","desc").contains(dir))throw new ValidationException("direction must be asc or desc");
        return events.listManaged(actor,new EventSearch(page(page,1,"page"),pageSize(size,25),query,participation,date(after,"startsAfter"),date(before,"startsBefore"),registrationOpen,sortBy,dir));}
    public Map<String,Object> registrations(AuthPrincipal actor,String eventId,String status,String page,String size){admin(actor);Long eid=eventId==null?null:positiveLong(eventId,"eventId");String state=status==null?null:status.strip().toUpperCase(Locale.ROOT);if(state!=null&&!STATUSES.contains(state))throw new ValidationException("status must be CONFIRMED, WAITLISTED, or CANCELLED");return dao.listRegistrations(eid,state,page(page,1,"page"),pageSize(size,25));}
    public Map<String,Object> dashboard(AuthPrincipal actor){admin(actor);return dao.dashboard();}
    public Map<String,Object> audit(AuthPrincipal actor,String actorId,String targetId,String action,String page,String size){admin(actor);Long a=actorId==null?null:positiveLong(actorId,"actorUserId");Long t=targetId==null?null:positiveLong(targetId,"targetUserId");String code=action==null?null:action.strip().toUpperCase(Locale.ROOT);if(code!=null&&!Set.of("ROLE_CHANGE","ACTIVATE","DEACTIVATE").contains(code))throw new ValidationException("action must be ROLE_CHANGE, ACTIVATE, or DEACTIVATE");return dao.listAudit(a,t,code,page(page,1,"page"),pageSize(size,50));}
    private static void admin(AuthPrincipal actor){if(actor==null)throw new ApiException(401,"Authentication required");if(!"ADMIN".equals(actor.role()))throw new ApiException(403,"Administrator account required");}
    private static int page(String raw,int fallback,String name){if(raw==null)return fallback;try{int value=Integer.parseInt(raw);if(value<1||value>1_000_000)throw new NumberFormatException();return value;}catch(NumberFormatException e){throw new ValidationException(name+" must be between 1 and 1000000");}}
    private static int pageSize(String raw,int fallback){return raw==null?fallback:boundedSize(raw);}
    private static int boundedSize(String raw){try{int value=Integer.parseInt(raw);if(value<1||value>100)throw new NumberFormatException();return value;}catch(NumberFormatException e){throw new ValidationException("pageSize must be between 1 and 100");}}
    private static String trim(String raw,int max,String name){if(raw==null||raw.isBlank())return null;String value=raw.strip();if(value.length()>max)throw new ValidationException(name+" must be at most "+max+" characters");return value;}
    private static OffsetDateTime date(String raw,String name){if(raw==null)return null;try{return OffsetDateTime.parse(raw);}catch(DateTimeParseException e){throw new ValidationException(name+" must be an ISO-8601 date-time with offset");}}
    private static long positiveLong(String raw,String name){try{if(raw==null||!raw.matches("[1-9][0-9]{0,17}"))throw new NumberFormatException();return Long.parseLong(raw);}catch(NumberFormatException e){throw new ValidationException(name+" must be a positive integer");}}
}
