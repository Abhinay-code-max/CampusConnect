package com.campusconnect.service;

import com.campusconnect.dao.JdbcParticipationDao;
import com.campusconnect.exception.ApiException;
import com.campusconnect.model.AuthPrincipal;
import java.util.List;
import java.util.Map;

public final class ParticipationService {
    private final JdbcParticipationDao dao;
    public ParticipationService(){this(new JdbcParticipationDao());}
    public ParticipationService(JdbcParticipationDao dao){this.dao=dao;}
    public List<Map<String,Object>> teams(AuthPrincipal user){return dao.teams(student(user));}
    public Map<String,Object> team(AuthPrincipal user,long id){return dao.team(student(user),id);}
    public Map<String,Object> create(AuthPrincipal user,String name){return dao.createTeam(student(user),name);}
    public Map<String,Object> rename(AuthPrincipal user,long id,String name){return dao.rename(student(user),id,name);}
    public List<Map<String,Object>> invitations(AuthPrincipal user){return dao.invitations(student(user));}
    public Map<String,Object> invite(AuthPrincipal user,long team,long invitee){return dao.invite(student(user),team,invitee);}
    public Map<String,Object> respond(AuthPrincipal user,long invite,boolean accept){return dao.respond(student(user),invite,accept);}
    public Map<String,Object> revoke(AuthPrincipal user,long team,long invite){return dao.revoke(student(user),team,invite);}
    public Map<String,Object> remove(AuthPrincipal user,long team,long member){return dao.removeMember(student(user),team,member);}
    public Map<String,Object> leave(AuthPrincipal user,long team){return dao.leave(student(user),team);}
    public Map<String,Object> registerIndividual(AuthPrincipal user,long event){return dao.registerIndividual(student(user),event);}
    public Map<String,Object> registerTeam(AuthPrincipal user,long event,long team){return dao.registerTeam(student(user),event,team);}
    public List<Map<String,Object>> registrations(AuthPrincipal user,int page,int size){return dao.myRegistrations(student(user),page,size);}
    public Map<String,Object> registration(AuthPrincipal user,long id){return dao.myRegistration(student(user),id);}
    public Map<String,Object> cancel(AuthPrincipal user,long id){return dao.cancel(student(user),id);}
    public List<Map<String,Object>> eventRegistrations(AuthPrincipal user,long event,int page,int size){boolean admin="ADMIN".equals(user.role());if(!admin&&!"ORGANIZER".equals(user.role()))throw new ApiException(403,"Forbidden");return dao.eventRegistrations(user.userId(),admin,event,page,size);}
    public static int page(String value,int fallback){return bounded(value,fallback,1,1000000,"page");}
    public static int pageSize(String value,int fallback){return bounded(value,fallback,1,100,"pageSize");}
    private static int bounded(String raw,int fallback,int min,int max,String name){if(raw==null)return fallback;try{int n=Integer.parseInt(raw);if(n<min||n>max)throw new NumberFormatException();return n;}catch(NumberFormatException e){throw new com.campusconnect.exception.ValidationException(name+" must be between "+min+" and "+max);}}
    private static long student(AuthPrincipal user){if(user==null||!"STUDENT".equals(user.role()))throw new ApiException(403,"Student account required");return user.userId();}
}
