package com.campusconnect.controller;

import com.campusconnect.exception.ApiException;
import com.campusconnect.filter.RequireRoles;
import com.campusconnect.model.RegistrationRequest;
import com.campusconnect.service.ParticipationInputValidator;
import com.campusconnect.service.ParticipationService;
import com.campusconnect.util.Json;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;

@WebServlet("/api/registrations/*")
@RequireRoles({"STUDENT"})
public final class RegistrationServlet extends HttpServlet {
    private final ParticipationService service=new ParticipationService();
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws IOException {try{String p=TeamServlet.path(req);if(p.isEmpty()){int page=ParticipationService.page(req.getParameter("page"),1),size=ParticipationService.pageSize(req.getParameter("pageSize"),20);Json.write(resp,200,Map.of("success",true,"registrations",service.registrations(TeamServlet.principal(req),page,size),"page",page,"pageSize",size));}else Json.write(resp,200,Map.of("success",true,"registration",service.registration(TeamServlet.principal(req),TeamServlet.id(p))));}catch(Exception e){TeamServlet.fail(resp,e);}}
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse resp)throws IOException {try{String p=TeamServlet.path(req);if(p.equals("individual")){long event=ParticipationInputValidator.eventId(Json.read(req,RegistrationRequest.class));Json.write(resp,201,Map.of("success",true,"registration",service.registerIndividual(TeamServlet.principal(req),event)));return;}String[] a=p.split("/");if(a.length==2&&a[0].equals("teams")){long team=TeamServlet.id(a[1]);long event=ParticipationInputValidator.eventId(Json.read(req,RegistrationRequest.class));Json.write(resp,201,Map.of("success",true,"registration",service.registerTeam(TeamServlet.principal(req),event,team)));return;}if(a.length==2&&a[1].equals("cancel")){Json.write(resp,200,Map.of("success",true,"registration",service.cancel(TeamServlet.principal(req),TeamServlet.id(a[0]))));return;}throw new ApiException(404,"Not found");}catch(Exception e){TeamServlet.fail(resp,e);}}
}
