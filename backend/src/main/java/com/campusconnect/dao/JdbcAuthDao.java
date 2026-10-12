package com.campusconnect.dao;

import com.campusconnect.exception.DuplicateEmailException;
import com.campusconnect.model.UserAccount;
import com.campusconnect.util.DBConnection;
import com.campusconnect.util.PasswordHasher;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class JdbcAuthDao implements AuthDao {
    private static final String ACCOUNT_COLUMNS = "user_id, display_name, email, role_code, active_flag, "
            + "password_salt, password_hash, password_iterations";

    @Override
    public UserAccount register(String displayName, String email, PasswordHasher.Hash credentials) {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (find(connection, email) != null) throw new DuplicateEmailException();
                String sql = "INSERT INTO cc_users (display_name, email, role_code, active_flag, "
                        + "password_salt, password_hash, password_iterations) VALUES (?, ?, 'STUDENT', 'Y', ?, ?, ?)";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, displayName);
                    statement.setString(2, email);
                    statement.setBytes(3, credentials.salt());
                    statement.setBytes(4, credentials.value());
                    statement.setInt(5, credentials.iterations());
                    statement.executeUpdate();
                }
                UserAccount created = find(connection, email);
                connection.commit();
                return created;
            } catch (SQLException e) {
                rollback(connection, e);
                if (isUniqueViolation(e)) throw new DuplicateEmailException();
                throw new IllegalStateException("Account registration failed (SQLState=" + e.getSQLState()
                        + ", vendorCode=" + e.getErrorCode() + ")", e);
            } catch (RuntimeException e) {
                rollback(connection, e);
                throw e;
            }
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Account registration database operation failed", e);
        }
    }

    @Override
    public UserAccount findByNormalizedEmail(String email) {
        try (Connection connection = DBConnection.getConnection()) {
            return find(connection, email);
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Account lookup database operation failed", e);
        }
    }

    @Override
    public UserAccount findById(long userId) {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT " + ACCOUNT_COLUMNS + " FROM cc_users WHERE user_id = ?")) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                return new UserAccount(result.getLong("user_id"), result.getString("display_name"),
                        result.getString("email"), result.getString("role_code"),
                        "Y".equals(result.getString("active_flag")), result.getBytes("password_salt"),
                        result.getBytes("password_hash"), result.getInt("password_iterations"));
            }
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Account session refresh failed", e);
        }
    }

    private UserAccount find(Connection connection, String email) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + ACCOUNT_COLUMNS + " FROM cc_users WHERE LOWER(TRIM(email)) = ?")) {
            statement.setString(1, email);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                return new UserAccount(result.getLong("user_id"), result.getString("display_name"),
                        result.getString("email"), result.getString("role_code"),
                        "Y".equals(result.getString("active_flag")), result.getBytes("password_salt"),
                        result.getBytes("password_hash"), result.getInt("password_iterations"));
            }
        }
    }

    private static boolean isUniqueViolation(SQLException e) {
        for (SQLException current = e; current != null; current = current.getNextException()) {
            if (current.getErrorCode() == 1) return true;
        }
        return false;
    }

    private static void rollback(Connection connection, Exception original) {
        try { connection.rollback(); }
        catch (SQLException rollbackFailure) { original.addSuppressed(rollbackFailure); }
    }
}
