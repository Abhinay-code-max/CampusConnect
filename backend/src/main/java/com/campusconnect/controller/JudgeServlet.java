package com.campusconnect.controller;

import com.campusconnect.exception.ApiException;
import com.campusconnect.filter.ApiAuthenticationFilter;
import com.campusconnect.filter.RequireRoles;
import com.campusconnect.model.AuthPrincipal;
import com.campusconnect.model.ScoreBatchRequest;
import com.campusconnect.service.ScoringService;
import com.campusconnect.util.Json;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;

@WebServlet("/api/judging/*")
@RequireRoles({"JUDGE"})
public final class JudgeServlet extends HttpServlet {
    private final ScoringService service=new ScoringService();
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws IOException {
        try {
            String[] p=ScoringServletSupport.path(req.getPathInfo()).split("/");
            if(p.length==1&&p[0].equals("events")){Json.write(resp,200,Map.of("success",true,"events",service.assignedEvents(principal(req))));return;}
            if(p.length==2&&p[0].equals("events")){Json.write(resp,200,Map.of("success",true,"assignment",service.judgingEvent(principal(req),ScoringServletSupport.id(p[1]))));return;}
            if(p.length==5&&p[0].equals("events")&&p[2].equals("registrations")&&p[4].equals("scores")){
                long event=ScoringServletSupport.id(p[1]),registration=ScoringServletSupport.id(p[3]);
                Json.write(resp,200,Map.of("success",true,"scores",service.judgeScores(principal(req),event,registration)));return;
            }
            throw new ApiException(404,"Not found");
        }catch(Exception e){ScoringServletSupport.fail(resp,e);}
    }
    @Override protected void doPut(HttpServletRequest req,HttpServletResponse resp)throws IOException {
        try {
            String[] p=ScoringServletSupport.path(req.getPathInfo()).split("/");
            if(p.length!=5||!p[0].equals("events")||!p[2].equals("registrations")||!p[4].equals("scores"))throw new ApiException(404,"Not found");
            var result=service.submitScores(principal(req),ScoringServletSupport.id(p[1]),ScoringServletSupport.id(p[3]),Json.read(req,ScoreBatchRequest.class));
            Json.write(resp,200,Map.of("success",true,"registrationScores",result));
        }catch(Exception e){ScoringServletSupport.fail(resp,e);}
    }
    private static AuthPrincipal principal(HttpServletRequest req){return (AuthPrincipal)req.getAttribute(ApiAuthenticationFilter.PRINCIPAL_ATTRIBUTE);}
}
