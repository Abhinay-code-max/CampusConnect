package com.campusconnect.util;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public final class ApiResponses {
    private ApiResponses() { }
    public static void error(HttpServletResponse response, int status, String message) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        Json.write(response, status, Json.error(message));
    }
}
