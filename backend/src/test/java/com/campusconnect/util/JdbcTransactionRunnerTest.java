package com.campusconnect.util;

import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

final class JdbcTransactionRunnerTest {
    @Test void commitsCompletedBatch() throws Exception {
        Connection connection=mock(Connection.class);AtomicInteger written=new AtomicInteger();
        int rows=JdbcTransactionRunner.run(connection,c->{written.incrementAndGet();return written.get();});
        assertEquals(1,rows);assertEquals(1,written.get());verify(connection).commit();verify(connection,never()).rollback();
    }
    @Test void rollsBackEntireBatchWhenLaterEntryFails() throws Exception {
        Connection connection=mock(Connection.class);AtomicInteger staged=new AtomicInteger();
        assertThrows(IllegalArgumentException.class,()->JdbcTransactionRunner.run(connection,c->{
            staged.incrementAndGet();
            staged.incrementAndGet();
            throw new IllegalArgumentException("invalid second score");
        }));
        verify(connection).rollback();verify(connection,never()).commit();
        assertEquals(2,staged.get());
    }
    @Test void rollsBackAfterJdbcFailure() throws Exception {
        Connection connection=mock(Connection.class);
        assertThrows(SQLException.class,()->JdbcTransactionRunner.run(connection,c->{throw new SQLException("write failed");}));
        verify(connection).rollback();verify(connection,never()).commit();
    }
}
