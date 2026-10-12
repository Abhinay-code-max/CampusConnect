package com.campusconnect.controller;

import com.campusconnect.exception.ApiException;
import com.campusconnect.exception.EventConflictException;
import com.campusconnect.exception.ValidationException;
import com.campusconnect.filter.ApiAuthenticationFilter;
import com.campusconnect.filter.RequireRoles;
import com.campusconnect.model.AuthPrincipal;
import com.campusconnect.model.EventRequest;
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

@WebServlet("/api/manage/events/*")
@RequireRoles({"ORGANIZER","ADMIN"})
public final class ManagedEventServlet extends HttpServlet {
    private final EventService service=new EventService();
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws IOException {
        try {
            if(!root(req.getPathInfo())) {
                String path=normalize(req.getPathInfo());
                if(!path.matches("[1-9][0-9]{0,17}"))throw new ApiException(404,"Not found");
                var event=service.findManaged(principal(req),Long.parseLong(path));
                if(event==null)throw new ApiException(404,"Event not found");
                Json.write(resp,200,EventJson.success(event));
                return;
            }
            EventSearch search=EventInputValidator.search(req.getParameter("page"),req.getParameter("pageSize"),req.getParameter("q"),
                    req.getParameter("participationMode"),req.getParameter("startsAfter"),req.getParameter("startsBefore"),
                    req.getParameter("registrationOpen"),req.getParameter("sort"),req.getParameter("direction"));
            Json.write(resp,200,EventJson.page(service.listManaged(principal(req),search)));
        } catch(ValidationException|ApiException e){ApiResponses.error(resp,e instanceof ApiException a?a.getStatus():400,e.getMessage());}
        catch(Exception e){failure(resp,e);}
    }
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse resp)throws IOException {
        try {
            String path=normalize(req.getPathInfo());
            if(path.isEmpty()) {var event=service.create(principal(req),Json.read(req,EventRequest.class));Json.write(resp,201,EventJson.success(event));return;}
            String[] p=path.split("/");if(p.length!=2||!p[0].matches("[1-9][0-9]{0,17}"))throw new ApiException(404,"Not found");
            var event=service.transition(principal(req),Long.parseLong(p[0]),p[1]);Json.write(resp,200,EventJson.success(event));
        } catch(ValidationException e){ApiResponses.error(resp,400,e.getMessage());}
        catch(ApiException|EventConflictException e){ApiResponses.error(resp,e instanceof ApiException a?a.getStatus():409,e.getMessage());}
        catch(IOException e){ApiResponses.error(resp,400,"Invalid JSON request body");}
        catch(Exception e){failure(resp,e);}
    }
    @Override protected void doPut(HttpServletRequest req,HttpServletResponse resp)throws IOException {
        try {
            String path=normalize(req.getPathInfo());if(!path.matches("[1-9][0-9]{0,17}"))throw new ApiException(404,"Not found");
            var event=service.update(principal(req),Long.parseLong(path),Json.read(req,EventRequest.class));Json.write(resp,200,EventJson.success(event));
        } catch(ValidationException e){ApiResponses.error(resp,400,e.getMessage());}
        catch(ApiException|EventConflictException e){ApiResponses.error(resp,e instanceof ApiException a?a.getStatus():409,e.getMessage());}
        catch(IOException e){ApiResponses.error(resp,400,"Invalid JSON request body");}
        catch(Exception e){failure(resp,e);}
    }
    private void failure(HttpServletResponse resp,Exception e)throws IOException {log("Event management request failed ("+SafeDiagnostics.describe(e)+")");ApiResponses.error(resp,500,"Request could not be processed");}
    private static AuthPrincipal principal(HttpServletRequest r){return (AuthPrincipal)r.getAttribute(ApiAuthenticationFilter.PRINCIPAL_ATTRIBUTE);}
    private static boolean root(String path){return path==null||path.isBlank()||"/".equals(path);}
    private static String normalize(String path){if(path==null||"/".equals(path))return "";return path.startsWith("/")?path.substring(1):path;}
}
