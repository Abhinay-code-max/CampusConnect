package com.campusconnect.util;

import java.sql.SQLException;

public final class SafeDiagnostics {
    private SafeDiagnostics() { }
    public static String describe(Throwable throwable) {
        SQLException sql = findSqlException(throwable);
        if (sql != null) return throwable.getClass().getSimpleName() + " (SQLState=" + sql.getSQLState()
                + ", vendorCode=" + sql.getErrorCode() + ")";
        return throwable.getClass().getSimpleName();
    }
    private static SQLException findSqlException(Throwable current) {
        for (Throwable cursor = current; cursor != null; cursor = cursor.getCause())
            if (cursor instanceof SQLException sql) return sql;
        return null;
    }
}
