package com.campusconnect.controller;

import com.campusconnect.exception.ApiException;
import com.campusconnect.exception.ValidationException;
import com.campusconnect.model.EventSearch;
import com.campusconnect.service.EventInputValidator;
import com.campusconnect.service.EventService;
import com.campusconnect.util.ApiResponses;
import com.campusconnect.util.Json;
import com.campusconnect.util.SafeDiagnostics;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/api/events/*")
public final class PublicEventServlet extends HttpServlet {
    private final EventService service=new EventService();
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws IOException {
        try {
            String path=request.getPathInfo();
            if(path==null||path.isBlank()||"/".equals(path)) {
                EventSearch search=EventInputValidator.search(request.getParameter("page"),request.getParameter("pageSize"),
                        request.getParameter("q"),request.getParameter("participationMode"),request.getParameter("startsAfter"),
                        request.getParameter("startsBefore"),request.getParameter("registrationOpen"),request.getParameter("sort"),request.getParameter("direction"));
                Json.write(response,200,EventJson.page(service.listPublic(search)));
            } else {
                String raw=path.substring(1);
                if(!raw.matches("[1-9][0-9]{0,17}"))throw new ApiException(404,"Event not found");
                Json.write(response,200,EventJson.success(service.findPublic(Long.parseLong(raw))));
            }
        } catch(ValidationException|ApiException e){ApiResponses.error(response,e instanceof ApiException a?a.getStatus():400,e.getMessage());}
        catch(Exception e){log("Public event request failed ("+SafeDiagnostics.describe(e)+")");ApiResponses.error(response,500,"Request could not be processed");}
    }
}
