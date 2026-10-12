package com.campusconnect.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {
    private static final Instant START = Instant.parse("2026-10-11T10:00:00Z");

    @Test
    void tenFailuresLockAnAccountAcrossDifferentClientIps() {
        LoginRateLimiter limiter = new LoginRateLimiter(Clock.fixed(START, ZoneOffset.UTC));
        String email = "student@example.edu";

        // Spread failures across addresses so only the account threshold is reached.
        for (int i = 1; i <= LoginRateLimiter.MAX_EMAIL_FAILURES; i++) {
            limiter.recordFailure("192.0.2." + i, email);
        }

        assertEquals(900, limiter.retryAfterSeconds("198.51.100.1", email));
        assertEquals(0, limiter.retryAfterSeconds("198.51.100.1", "other@example.edu"));
    }

    @Test
    void fortyFailuresLockAnIpAcrossDifferentAccountsButNotAnotherIp() {
        LoginRateLimiter limiter = new LoginRateLimiter(Clock.fixed(START, ZoneOffset.UTC));
        String ip = "192.0.2.10";

        for (int i = 0; i < LoginRateLimiter.MAX_IP_FAILURES; i++) {
            limiter.recordFailure(ip, "student" + i + "@example.edu");
        }

        assertEquals(900, limiter.retryAfterSeconds(ip, "new-account@example.edu"));
        assertEquals(0, limiter.retryAfterSeconds("192.0.2.11", "new-account@example.edu"));
    }

    @Test
    void successfulLoginClearsAccountFailuresButPreservesAnIndependentIpLock() {
        LoginRateLimiter limiter = new LoginRateLimiter(Clock.fixed(START, ZoneOffset.UTC));
        String account = "student@example.edu";

        // Reach the IP limit using distinct accounts.
        for (int i = 0; i < LoginRateLimiter.MAX_IP_FAILURES; i++) {
            limiter.recordFailure("192.0.2.10", "other" + i + "@example.edu");
        }
        // Reach the account limit across distinct IPs, without changing any one IP bucket materially.
        for (int i = 1; i <= LoginRateLimiter.MAX_EMAIL_FAILURES; i++) {
            limiter.recordFailure("198.51.100." + i, account);
        }

        limiter.recordSuccess(account);

        assertEquals(0, limiter.retryAfterSeconds("203.0.113.1", account));
        assertEquals(900, limiter.retryAfterSeconds("192.0.2.10", "another-account@example.edu"));
    }

    @Test
    void accountAndIpLockoutsExpireAtTheFifteenMinuteBoundary() {
        MutableClock clock = new MutableClock(START);
        LoginRateLimiter limiter = new LoginRateLimiter(clock);
        String account = "student@example.edu";

        for (int i = 1; i <= LoginRateLimiter.MAX_EMAIL_FAILURES; i++) {
            limiter.recordFailure("198.51.100." + i, account);
        }
        for (int i = 0; i < LoginRateLimiter.MAX_IP_FAILURES; i++) {
            limiter.recordFailure("192.0.2.10", "ip-account" + i + "@example.edu");
        }

        assertEquals(900, limiter.retryAfterSeconds("203.0.113.1", account));
        assertEquals(900, limiter.retryAfterSeconds("192.0.2.10", "new-account@example.edu"));

        clock.advance(LoginRateLimiter.WINDOW.minusSeconds(1));
        assertEquals(1, limiter.retryAfterSeconds("203.0.113.1", account));
        assertEquals(1, limiter.retryAfterSeconds("192.0.2.10", "new-account@example.edu"));

        clock.advance(Duration.ofSeconds(1));
        assertEquals(0, limiter.retryAfterSeconds("203.0.113.1", account));
        assertEquals(0, limiter.retryAfterSeconds("192.0.2.10", "new-account@example.edu"));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant initialTime) {
            now = initialTime;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }
    }
}
