package com.campusconnect.controller;

import com.campusconnect.exception.ApiException;
import com.campusconnect.filter.ApiAuthenticationFilter;
import com.campusconnect.filter.RequireRoles;
import com.campusconnect.model.*;
import com.campusconnect.service.ScoringService;
import com.campusconnect.util.Json;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;

@WebServlet("/api/manage/scoring/*")
@RequireRoles({"ORGANIZER","ADMIN"})
public final class ScoringManagementServlet extends HttpServlet {
    private final ScoringService service=new ScoringService();
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws IOException {
        try {
            String[] p=ScoringServletSupport.path(req.getPathInfo()).split("/");
            if(p.length==3&&p[0].equals("events")&&p[2].equals("judges")){Json.write(resp,200,Map.of("success",true,"judges",service.managersJudges(principal(req),ScoringServletSupport.id(p[1]))));return;}
            if(p.length==3&&p[0].equals("events")&&p[2].equals("criteria")){Json.write(resp,200,Map.of("success",true,"criteria",service.criteria(principal(req),ScoringServletSupport.id(p[1]))));return;}
            if(p.length==3&&p[0].equals("events")&&p[2].equals("results")){Map<String,Object> result=new java.util.LinkedHashMap<>();result.put("success",true);result.putAll(service.managerResults(principal(req),ScoringServletSupport.id(p[1]),req.getParameter("page"),req.getParameter("pageSize")));Json.write(resp,200,result);return;}
            throw new ApiException(404,"Not found");
        }catch(Exception e){ScoringServletSupport.fail(resp,e);}
    }
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse resp)throws IOException {
        try {
            String[] p=ScoringServletSupport.path(req.getPathInfo()).split("/");
            if(p.length==3&&p[0].equals("events")&&p[2].equals("judges")){
                JudgeAssignmentRequest body=Json.read(req,JudgeAssignmentRequest.class);
                Json.write(resp,201,Map.of("success",true,"judge",service.assignJudge(principal(req),ScoringServletSupport.id(p[1]),body.getJudgeUserId())));return;
            }
            if(p.length==3&&p[0].equals("events")&&p[2].equals("criteria")){
                Json.write(resp,201,Map.of("success",true,"criterion",service.createCriterion(principal(req),ScoringServletSupport.id(p[1]),Json.read(req,ScoringCriterionRequest.class))));return;
            }
            if(p.length==3&&p[0].equals("events")&&p[2].equals("close-scoring")){
                Json.write(resp,200,Map.of("success",true,"event",service.closeScoring(principal(req),ScoringServletSupport.id(p[1]))));return;
            }
            if(p.length==3&&p[0].equals("events")&&p[2].equals("finalize-results")){
                Json.write(resp,200,Map.of("success",true,"event",service.finalizeResults(principal(req),ScoringServletSupport.id(p[1]))));return;
            }
            if(p.length==3&&p[0].equals("events")&&p[2].equals("publish-results")){
                Json.write(resp,200,Map.of("success",true,"event",service.publishResults(principal(req),ScoringServletSupport.id(p[1]))));return;
            }
            throw new ApiException(404,"Not found");
        }catch(Exception e){ScoringServletSupport.fail(resp,e);}
    }
    @Override protected void doPut(HttpServletRequest req,HttpServletResponse resp)throws IOException {
        try {
            String[] p=ScoringServletSupport.path(req.getPathInfo()).split("/");
            if(p.length==3&&p[0].equals("events")&&p[2].equals("scoring-window")){
                Json.write(resp,200,Map.of("success",true,"event",service.setWindow(principal(req),ScoringServletSupport.id(p[1]),Json.read(req,ScoringWindowRequest.class))));return;
            }
            if(p.length==4&&p[0].equals("events")&&p[2].equals("criteria")){
                Json.write(resp,200,Map.of("success",true,"criterion",service.updateCriterion(principal(req),ScoringServletSupport.id(p[1]),ScoringServletSupport.id(p[3]),Json.read(req,ScoringCriterionRequest.class))));return;
            }
            throw new ApiException(404,"Not found");
        }catch(Exception e){ScoringServletSupport.fail(resp,e);}
    }
    @Override protected void doDelete(HttpServletRequest req,HttpServletResponse resp)throws IOException {
        try {
            String[] p=ScoringServletSupport.path(req.getPathInfo()).split("/");
            if(p.length==4&&p[0].equals("events")&&p[2].equals("judges")){
                Json.write(resp,200,Map.of("success",true,"assignment",service.unassignJudge(principal(req),ScoringServletSupport.id(p[1]),ScoringServletSupport.id(p[3]))));return;
            }
            throw new ApiException(404,"Not found");
        }catch(Exception e){ScoringServletSupport.fail(resp,e);}
    }
    private static AuthPrincipal principal(HttpServletRequest req){return (AuthPrincipal)req.getAttribute(ApiAuthenticationFilter.PRINCIPAL_ATTRIBUTE);}
}
