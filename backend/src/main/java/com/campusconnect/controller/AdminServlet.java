package com.campusconnect.controller;

import com.campusconnect.exception.ApiException;
import com.campusconnect.exception.ValidationException;
import com.campusconnect.filter.ApiAuthenticationFilter;
import com.campusconnect.filter.RequireRoles;
import com.campusconnect.model.AdminRoleRequest;
import com.campusconnect.model.AdminStatusRequest;
import com.campusconnect.model.AuthPrincipal;
import com.campusconnect.model.EventPage;
import com.campusconnect.service.AdminService;
import com.campusconnect.util.ApiResponses;
import com.campusconnect.util.Json;
import com.campusconnect.util.SafeDiagnostics;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

@WebServlet("/api/admin/*")
@RequireRoles({"ADMIN"})
public final class AdminServlet extends HttpServlet {
    private static final Logger LOG=Logger.getLogger("CampusConnect");
    private final AdminService service=new AdminService();

    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws IOException {
        try {
            String path=path(req);
            if("users".equals(path)){Json.write(resp,200,withItems("users",service.users(actor(req),req.getParameter("q"),req.getParameter("role"),req.getParameter("active"),req.getParameter("sort"),req.getParameter("direction"),req.getParameter("page"),req.getParameter("pageSize"))));return;}
            if("dashboard".equals(path)){Json.write(resp,200,Map.of("success",true,"dashboard",service.dashboard(actor(req))));return;}
            if("events".equals(path)){EventPage p=service.events(actor(req),req.getParameter("q"),req.getParameter("mode"),req.getParameter("startsAfter"),req.getParameter("startsBefore"),req.getParameter("registrationOpen"),req.getParameter("sort"),req.getParameter("direction"),req.getParameter("page"),req.getParameter("pageSize"));Json.write(resp,200,EventJson.page(p));return;}
            if("registrations".equals(path)){Json.write(resp,200,withItems("registrations",service.registrations(actor(req),req.getParameter("eventId"),req.getParameter("status"),req.getParameter("page"),req.getParameter("pageSize"))));return;}
            if("audit".equals(path)){Json.write(resp,200,withItems("audit",service.audit(actor(req),req.getParameter("actorUserId"),req.getParameter("targetUserId"),req.getParameter("action"),req.getParameter("page"),req.getParameter("pageSize"))));return;}
            String[] parts=path.split("/");
            if(parts.length==2&&"users".equals(parts[0])){Json.write(resp,200,Map.of("success",true,"user",service.user(actor(req),TeamServlet.id(parts[1]))));return;}
            throw new ApiException(404,"Not found");
        }catch(Exception e){fail(resp,e);}
    }

    @Override protected void doPut(HttpServletRequest req,HttpServletResponse resp)throws IOException {
        try {
            String[] p=path(req).split("/");
            if(p.length==3&&"users".equals(p[0])&&"role".equals(p[2])){AdminRoleRequest body=Json.read(req,AdminRoleRequest.class);Json.write(resp,200,Map.of("success",true,"user",service.role(actor(req),TeamServlet.id(p[1]),body.getRole())));return;}
            if(p.length==3&&"users".equals(p[0])&&"status".equals(p[2])){AdminStatusRequest body=Json.read(req,AdminStatusRequest.class);Json.write(resp,200,Map.of("success",true,"user",service.active(actor(req),TeamServlet.id(p[1]),body.getActive())));return;}
            throw new ApiException(404,"Not found");
        }catch(Exception e){fail(resp,e);}
    }

    private static String path(HttpServletRequest req){String value=req.getPathInfo();return value==null||value.equals("/")?"":value.substring(1);}
    private static AuthPrincipal actor(HttpServletRequest req){return (AuthPrincipal)req.getAttribute(ApiAuthenticationFilter.PRINCIPAL_ATTRIBUTE);}
    private static Map<String,Object> withItems(String key,Map<String,Object> page){Map<String,Object> body=new LinkedHashMap<>();body.put("success",true);body.put(key,page.get("items"));body.put("page",page.get("page"));body.put("pageSize",page.get("pageSize"));body.put("total",page.get("total"));body.put("totalPages",page.get("totalPages"));return body;}
    private static void fail(HttpServletResponse response,Exception error)throws IOException {
        if(error instanceof ApiException api){ApiResponses.error(response,api.getStatus(),api.getMessage());return;}
        if(error instanceof ValidationException validation){ApiResponses.error(response,400,validation.getMessage());return;}
        if(error instanceof IOException){ApiResponses.error(response,400,"Invalid JSON request body");return;}
        LOG.warning("Phase 8 admin request failed ("+SafeDiagnostics.describe(error)+")");
        ApiResponses.error(response,500,"Request could not be processed");
    }
}
