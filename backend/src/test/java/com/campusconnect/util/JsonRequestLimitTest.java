package com.campusconnect.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.campusconnect.exception.ApiException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonRequestLimitTest {
    @Test void acceptsJsonWithinTheRequestLimit() throws Exception {
        HttpServletRequest request = requestWithBody("{\"value\":1}", 11);
        Map<?, ?> body = Json.read(request, Map.class);
        assertEquals(1, body.get("value"));
    }

    @Test void rejectsOversizedDeclaredBodyBeforeReadingIt() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentLengthLong()).thenReturn((long) Json.MAX_REQUEST_BYTES + 1);
        ApiException error = assertThrows(ApiException.class, () -> Json.read(request, Map.class));
        assertEquals(413, error.getStatus());
        verify(request, never()).getInputStream();
    }

    @Test void rejectsOversizedChunkedBodyWhileReading() throws Exception {
        String body = " ".repeat(Json.MAX_REQUEST_BYTES + 1);
        HttpServletRequest request = requestWithBody(body, -1);
        ApiException error = assertThrows(ApiException.class, () -> Json.read(request, Map.class));
        assertEquals(413, error.getStatus());
    }

    private static HttpServletRequest requestWithBody(String body, long length) throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentLengthLong()).thenReturn(length);
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ByteArrayInputStream input = new ByteArrayInputStream(bytes);
        when(request.getInputStream()).thenReturn(new ServletInputStream() {
            @Override public int read() { return input.read(); }
            @Override public boolean isFinished() { return input.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) { }
        });
        return request;
    }
}
