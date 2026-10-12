package com.campusconnect.util;

import java.sql.Connection;
import java.sql.SQLException;

/** Small transaction boundary shared by JDBC DAOs. */
public final class JdbcTransactionRunner {
    private JdbcTransactionRunner() { }
    @FunctionalInterface public interface Work<T> { T run(Connection connection) throws SQLException; }

    public static <T> T run(Connection connection, Work<T> work) throws SQLException {
        connection.setAutoCommit(false);
        try {
            T result=work.run(connection);
            connection.commit();
            return result;
        } catch(SQLException|RuntimeException error) {
            try { connection.rollback(); }
            catch(SQLException rollbackError) { error.addSuppressed(rollbackError); }
            throw error;
        }
    }
}
