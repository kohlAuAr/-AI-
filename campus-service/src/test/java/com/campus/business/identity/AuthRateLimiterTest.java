package com.campus.business.identity;

import java.time.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthRateLimiterTest {
    static final class TestClock extends Clock {
        long milliseconds;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(milliseconds); }
    }

    @Test void accountWindowExpiresAndSuccessfulLoginResetsIt() {
        TestClock clock = new TestClock(); AuthRateLimiter limiter = new AuthRateLimiter(clock);
        for (int i = 0; i < 5; i++) assertEquals(0, limiter.acquire("login", "a", "student"));
        assertEquals(300, limiter.acquire("login", "b", " STUDENT "));
        clock.milliseconds = 300000;
        assertEquals(0, limiter.acquire("login", "a", "student"));
        limiter.succeeded("student");
        for (int i = 0; i < 5; i++) assertEquals(0, limiter.acquire("login", "a", "student"));
    }

    @Test void sourceLimitsApplyAcrossAccountsAndRegistrationHasItsOwnWindow() {
        TestClock clock = new TestClock(); AuthRateLimiter limiter = new AuthRateLimiter(clock);
        for (int i = 0; i < 120; i++) assertEquals(0, limiter.acquire("login", "a", "user" + i));
        assertEquals(60, limiter.acquire("login", "a", "another"));
        assertEquals(0, limiter.acquire("login", "b", "another"));
        for (int i = 0; i < 10; i++) assertEquals(0, limiter.acquire("register", "a", null));
        assertEquals(60, limiter.acquire("register", "a", null));
        clock.milliseconds = 60000;
        assertEquals(0, limiter.acquire("register", "a", null));
    }

    @Test void concurrentRequestsCannotReserveMoreThanFiveAccountAttempts() throws Exception {
        AuthRateLimiter limiter = new AuthRateLimiter(new TestClock());
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            var requests = IntStream.range(0, 20).mapToObj(i -> (Callable<Long>) () -> limiter.acquire("login", "a", "student")).toList();
            int allowed = 0;
            for (Future<Long> result : pool.invokeAll(requests)) if (result.get() == 0) allowed++;
            assertEquals(5, allowed);
        } finally { pool.shutdownNow(); }
    }
}
