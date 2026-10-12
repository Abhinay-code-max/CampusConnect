package com.campusconnect.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Properties;
import java.util.Set;

/** Creates Oracle connections using credentials from an external UTF-8 properties file. */
public final class DBConnection {

    private static final String CONFIG_PROPERTY = "campusconnect.db.config";

    private DBConnection() {
    }

    public static Connection getConnection() throws IOException, SQLException {
        String configPath = System.getProperty(CONFIG_PROPERTY);
        if (configPath == null || configPath.isBlank()) {
            throw new IllegalStateException("JVM property " + CONFIG_PROPERTY + " is not set");
        }

        Properties properties = new Properties();
        Path path = Path.of(configPath);
        try (InputStream input = Files.newInputStream(path);
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        String url = required(properties, "db.url");
        String username = required(properties, "db.username");
        String password = required(properties, "db.password");
        try {
            // Explicit loading makes the driver visible even when Tomcat's webapp class loader
            // is not included in DriverManager's automatic service-provider scan.
            Class.forName("oracle.jdbc.OracleDriver");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("Oracle JDBC driver is not available to the web application", "08001", 0,
                    exception);
        }

        try {
            return DriverManager.getConnection(url, username, password);
        } catch (SQLException exception) {
            throw sanitizedSQLException(exception, username, password);
        }
    }

    private static SQLException sanitizedSQLException(SQLException exception, String username, String password) {
        StringBuilder details = new StringBuilder();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        appendDetails(exception, details, visited, username, password);
        return new SQLException(details.toString(), exception.getSQLState(), exception.getErrorCode());
    }

    private static void appendDetails(Throwable throwable, StringBuilder details, Set<Throwable> visited,
                                      String username, String password) {
        if (throwable == null || !visited.add(throwable)) {
            return;
        }
        if (!details.isEmpty()) {
            details.append("; ");
        }
        details.append(throwable.getClass().getSimpleName());
        String message = throwable.getMessage();
        if (message != null && !message.isBlank()) {
            details.append(": ").append(redact(message, username, password));
        }
        if (throwable instanceof SQLException sqlException) {
            appendDetails(sqlException.getNextException(), details, visited, username, password);
        }
        appendDetails(throwable.getCause(), details, visited, username, password);
    }

    private static String redact(String message, String username, String password) {
        String sanitized = message.replace(username, "[REDACTED]").replace(password, "[REDACTED]");
        return sanitized.replace('\r', ' ').replace('\n', ' ');
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required database property: " + key);
        }
        return value;
    }
}
