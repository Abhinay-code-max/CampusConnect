package com.campusconnect.controller;

import com.campusconnect.exception.ApiException;
import com.campusconnect.exception.ValidationException;
import com.campusconnect.filter.ApiAuthenticationFilter;
import com.campusconnect.filter.RequireRoles;
import com.campusconnect.model.AuthPrincipal;
import com.campusconnect.model.TeamRequest;
import com.campusconnect.service.ParticipationInputValidator;
import com.campusconnect.service.ParticipationService;
import com.campusconnect.util.ApiResponses;
import com.campusconnect.util.Json;
import com.campusconnect.util.SafeDiagnostics;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;

@WebServlet("/api/teams/*")
@RequireRoles({"STUDENT"})
public final class TeamServlet extends HttpServlet {
    private final ParticipationService service=new ParticipationService();
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws IOException {try{String p=path(req);if(p.isEmpty())Json.write(resp,200,Map.of("success",true,"teams",service.teams(principal(req))));else Json.write(resp,200,Map.of("success",true,"team",service.team(principal(req),id(p))));}catch(Exception e){fail(resp,e);}}
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse resp)throws IOException {try{String p=path(req);if(p.isEmpty()){TeamRequest b=Json.read(req,TeamRequest.class);Json.write(resp,201,Map.of("success",true,"team",service.create(principal(req),ParticipationInputValidator.teamName(b))));return;}String[] a=p.split("/");if(a.length==2&&a[1].equals("invitations")){TeamRequest b=Json.read(req,TeamRequest.class);Json.write(resp,201,Map.of("success",true,"invitation",service.invite(principal(req),id(a[0]),ParticipationInputValidator.invitee(b))));return;}if(a.length==2&&a[1].equals("leave")){Json.write(resp,200,service.leave(principal(req),id(a[0])));return;}throw new ApiException(404,"Not found");}catch(Exception e){fail(resp,e);}}
    @Override protected void doPut(HttpServletRequest req,HttpServletResponse resp)throws IOException {try{String p=path(req);TeamRequest b=Json.read(req,TeamRequest.class);Json.write(resp,200,Map.of("success",true,"team",service.rename(principal(req),id(p),ParticipationInputValidator.teamName(b))));}catch(Exception e){fail(resp,e);}}
    @Override protected void doDelete(HttpServletRequest req,HttpServletResponse resp)throws IOException {try{String[] p=path(req).split("/");if(p.length==3&&p[1].equals("members")){Json.write(resp,200,Map.of("success",true,"team",service.remove(principal(req),id(p[0]),id(p[2]))));return;}if(p.length==3&&p[1].equals("invitations")){Json.write(resp,200,service.revoke(principal(req),id(p[0]),id(p[2])));return;}throw new ApiException(404,"Not found");}catch(Exception e){fail(resp,e);}}
    static AuthPrincipal principal(HttpServletRequest r){return (AuthPrincipal)r.getAttribute(ApiAuthenticationFilter.PRINCIPAL_ATTRIBUTE);}
    static String path(HttpServletRequest r){String p=r.getPathInfo();return p==null||p.equals("/")?"":p.substring(1);}
    static long id(String s){if(s==null||!s.matches("[1-9][0-9]{0,17}"))throw new ApiException(404,"Not found");try{return Long.parseLong(s);}catch(NumberFormatException e){throw new ApiException(404,"Not found");}}
    static void fail(HttpServletResponse resp,Exception e)throws IOException {if(e instanceof ApiException a){ApiResponses.error(resp,a.getStatus(),a.getMessage());return;}if(e instanceof ValidationException v){ApiResponses.error(resp,400,v.getMessage());return;}if(e instanceof IOException){ApiResponses.error(resp,400,"Invalid JSON request body");return;}java.util.logging.Logger.getLogger("CampusConnect").warning("Phase 6 request failed ("+SafeDiagnostics.describe(e)+")");ApiResponses.error(resp,500,"Request could not be processed"); }
}
