package com.campusconnect.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.campusconnect.exception.ApiException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Json {
    public static final int MAX_REQUEST_BYTES = 1_048_576;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private Json() { }

    public static <T> T read(jakarta.servlet.http.HttpServletRequest request, Class<T> type) throws IOException {
        if (request.getContentLengthLong() > MAX_REQUEST_BYTES) {
            throw new ApiException(413, "Request body is too large");
        }
        InputStream input = request.getInputStream();
        byte[] body = input.readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) {
            throw new ApiException(413, "Request body is too large");
        }
        return MAPPER.readValue(body, type);
    }

    public static void write(HttpServletResponse response, int status, Object value) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        MAPPER.writeValue(response.getOutputStream(), value);
    }

    public static Map<String, Object> object(String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(key, value);
        return result;
    }

    public static Map<String, Object> error(String message) {
        Map<String, Object> result = object("success", false);
        result.put("message", message);
        return result;
    }
    public static String safeMessage(Exception exception) {
        return exception instanceof JsonProcessingException ? "Invalid JSON request body" : "Request could not be processed";
    }
}
