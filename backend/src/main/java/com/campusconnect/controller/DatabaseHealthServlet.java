package com.campusconnect.controller;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.campusconnect.util.DBConnection;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/api/health/db")
public class DatabaseHealthServlet extends HttpServlet {

    private static final String FAILURE_RESPONSE =
            "{\"success\":false,\"message\":\"Database health check failed\"}";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        int result;
        try (Connection connection = DBConnection.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1 FROM dual")) {
            result = resultSet.next() ? resultSet.getInt(1) : 0;
            if (result != 1) {
                throw new SQLException("Health query returned no expected result");
            }
        } catch (Exception exception) {
            logFailure(exception);
            writeJson(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, FAILURE_RESPONSE);
            return;
        }

        writeJson(response, HttpServletResponse.SC_OK,
                "{\"success\":true,\"message\":\"Oracle connection successful\",\"result\":" + result + "}");
    }

    private void logFailure(Exception exception) {
        if (exception instanceof SQLException sqlException) {
            log("Oracle database health check failed (" + exception.getClass().getSimpleName()
                    + ", SQLState=" + sqlException.getSQLState()
                    + ", vendorCode=" + sqlException.getErrorCode()
                    + ", detail=" + sqlException.getMessage() + ")");
        } else {
            log("Oracle database health check failed (" + exception.getClass().getSimpleName() + ")");
        }
    }

    private void writeJson(HttpServletResponse response, int status, String json) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json);
    }
}
