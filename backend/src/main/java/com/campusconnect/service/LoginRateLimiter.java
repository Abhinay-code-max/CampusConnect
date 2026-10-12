package com.campusconnect.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** Process-local failed-login throttling. Production deployments should also rate-limit at the edge. */
public final class LoginRateLimiter {
    static final int MAX_EMAIL_FAILURES = 10;
    static final int MAX_IP_FAILURES = 40;
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_BUCKETS = 20_000;

    private static final LoginRateLimiter INSTANCE = new LoginRateLimiter(Clock.systemUTC());
    private final Clock clock;
    private final Map<String, Bucket> failures = new HashMap<>();
    private int operations;

    public static LoginRateLimiter instance() {
        return INSTANCE;
    }

    LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Returns zero when allowed; otherwise returns a Retry-After delay in seconds. */
    public synchronized long retryAfterSeconds(String remoteAddress, String normalizedEmail) {
        Instant now = clock.instant();
        cleanupOccasionally(now);
        long ipWait = retryAfter(ipKey(remoteAddress), MAX_IP_FAILURES, now);
        long emailWait = retryAfter(emailKey(normalizedEmail), MAX_EMAIL_FAILURES, now);
        return Math.max(ipWait, emailWait);
    }

    public synchronized void recordFailure(String remoteAddress, String normalizedEmail) {
        Instant now = clock.instant();
        cleanupOccasionally(now);
        failures.computeIfAbsent(ipKey(remoteAddress), ignored -> new Bucket()).add(now);
        failures.computeIfAbsent(emailKey(normalizedEmail), ignored -> new Bucket()).add(now);
        enforceBucketLimit();
    }

    /** A successful authentication clears the account bucket, not failures from other users at the same IP. */
    public synchronized void recordSuccess(String normalizedEmail) {
        failures.remove(emailKey(normalizedEmail));
    }

    private long retryAfter(String key, int limit, Instant now) {
        Bucket bucket = failures.get(key);
        if (bucket == null) return 0;
        bucket.removeExpired(now);
        if (bucket.times.size() < limit) return 0;
        Instant retryAt = bucket.times.peekFirst().plus(WINDOW);
        return Math.max(1, (Duration.between(now, retryAt).toMillis() + 999) / 1000);
    }

    private void cleanupOccasionally(Instant now) {
        if ((++operations & 63) != 0) return;
        Iterator<Map.Entry<String, Bucket>> iterator = failures.entrySet().iterator();
        while (iterator.hasNext()) {
            Bucket bucket = iterator.next().getValue();
            bucket.removeExpired(now);
            if (bucket.times.isEmpty()) iterator.remove();
        }
    }

    private void enforceBucketLimit() {
        if (failures.size() <= MAX_BUCKETS) return;
        List<Map.Entry<String, Bucket>> entries = new ArrayList<>(failures.entrySet());
        entries.sort((left, right) -> {
            Instant leftLast = left.getValue().last();
            Instant rightLast = right.getValue().last();
            return (leftLast == null ? Instant.MIN : leftLast)
                    .compareTo(rightLast == null ? Instant.MIN : rightLast);
        });
        int remove = failures.size() - MAX_BUCKETS;
        for (int i = 0; i < remove; i++) failures.remove(entries.get(i).getKey());
    }

    private static String ipKey(String remoteAddress) {
        return digestKey("ip:", remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress);
    }

    private static String emailKey(String email) {
        return digestKey("email:", email == null ? "" : email);
    }

    private static String digestKey(String prefix, String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return prefix + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static final class Bucket {
        private final ArrayDeque<Instant> times = new ArrayDeque<>();
        private void add(Instant time) { times.addLast(time); }
        private void removeExpired(Instant now) {
            Instant cutoff = now.minus(WINDOW);
            while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) times.removeFirst();
        }
        private Instant last() { return times.peekLast(); }
    }
}
