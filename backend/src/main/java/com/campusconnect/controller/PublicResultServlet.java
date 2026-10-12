package com.campusconnect.controller;

import com.campusconnect.exception.ApiException;
import com.campusconnect.service.ScoringService;
import com.campusconnect.util.ApiResponses;
import com.campusconnect.util.Json;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;

@WebServlet("/api/results/events/*")
public final class PublicResultServlet extends HttpServlet {
    private final ScoringService service=new ScoringService();
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws IOException {
        try {
            String path=req.getPathInfo();
            if(path==null||path.length()<2||path.substring(1).contains("/"))throw new ApiException(404,"Not found");
            long id=ScoringServletSupport.id(path.substring(1));
            Map<String,Object> result=new java.util.LinkedHashMap<>();result.put("success",true);result.putAll(service.publicResults(id,req.getParameter("page"),req.getParameter("pageSize")));Json.write(resp,200,result);
        }catch(Exception e){ScoringServletSupport.fail(resp,e);}
    }
}
