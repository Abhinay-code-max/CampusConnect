package com.campusconnect.controller;

import com.campusconnect.exception.ApiException;
import com.campusconnect.filter.RequireRoles;
import com.campusconnect.util.Json;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;

@WebServlet("/api/team-invitations/*")
@RequireRoles({"STUDENT"})
public final class TeamInvitationServlet extends HttpServlet {
    private final com.campusconnect.service.ParticipationService service=new com.campusconnect.service.ParticipationService();
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws IOException {try{if(!TeamServlet.path(req).isEmpty())throw new ApiException(404,"Not found");Json.write(resp,200,Map.of("success",true,"invitations",service.invitations(TeamServlet.principal(req))));}catch(Exception e){TeamServlet.fail(resp,e);}}
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse resp)throws IOException {try{String[] p=TeamServlet.path(req).split("/");if(p.length!=2)throw new ApiException(404,"Not found");long id=TeamServlet.id(p[0]);if(p[1].equals("accept"))Json.write(resp,200,service.respond(TeamServlet.principal(req),id,true));else if(p[1].equals("decline"))Json.write(resp,200,service.respond(TeamServlet.principal(req),id,false));else throw new ApiException(404,"Not found");}catch(Exception e){TeamServlet.fail(resp,e);}}
}
