package com.campusconnect.controller;

import com.campusconnect.exception.ApiException;
import com.campusconnect.filter.RequireRoles;
import com.campusconnect.service.ParticipationService;
import com.campusconnect.util.Json;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;

@WebServlet("/api/manage/event-registrations/*")
@RequireRoles({"ORGANIZER","ADMIN"})
public final class ManagedRegistrationServlet extends HttpServlet {
    private final ParticipationService service=new ParticipationService();
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws IOException {try{String[] p=TeamServlet.path(req).split("/");if(p.length!=1)throw new ApiException(404,"Not found");long event=TeamServlet.id(p[0]);int page=ParticipationService.page(req.getParameter("page"),1),size=ParticipationService.pageSize(req.getParameter("pageSize"),50);var records=service.eventRegistrations(TeamServlet.principal(req),event,page,size);Json.write(resp,200,Map.of("success",true,"registrations",records,"page",page,"pageSize",size));}catch(Exception e){TeamServlet.fail(resp,e);}}
}
