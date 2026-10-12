package com.campusconnect.controller;

import com.campusconnect.exception.ApiException;
import com.campusconnect.exception.ValidationException;
import com.campusconnect.util.ApiResponses;
import com.campusconnect.util.SafeDiagnostics;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

final class ScoringServletSupport {
    private ScoringServletSupport() { }
    static long id(String raw) {
        if(raw==null||!raw.matches("[1-9][0-9]{0,17}"))throw new ApiException(404,"Not found");
        try{return Long.parseLong(raw);}catch(NumberFormatException e){throw new ApiException(404,"Not found");}
    }
    static String path(String pathInfo) { return pathInfo==null||pathInfo.equals("/")?"":pathInfo.substring(1); }
    static void fail(HttpServletResponse response,Exception error)throws IOException {
        if(error instanceof ApiException api){ApiResponses.error(response,api.getStatus(),api.getMessage());return;}
        if(error instanceof ValidationException validation){ApiResponses.error(response,400,validation.getMessage());return;}
        if(error instanceof IOException){ApiResponses.error(response,400,"Invalid JSON request body");return;}
        java.util.logging.Logger.getLogger("CampusConnect").warning("Phase 7 request failed ("+SafeDiagnostics.describe(error)+")");
        ApiResponses.error(response,500,"Request could not be processed");
    }
}
