package com.campus.business.identity;

import java.time.Clock;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AuthRateLimiter {
    private record Window(int count, long expiresAt) {}
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    public AuthRateLimiter() { this(Clock.systemUTC()); }
    AuthRateLimiter(Clock clock) { this.clock = clock; }

    // Single-instance, bounded memory. Reserve before password verification to cover parallel attempts.
    public synchronized long acquire(String operation, String address, String username) {
        long now = clock.millis();
        windows.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        if (windows.size() >= 10000) return 60;
        long retry = reserve(operation + ":ip:" + address, operation.equals("login") ? 120 : 10, 60000, now);
        if (retry > 0 || !operation.equals("login")) return retry;
        return reserve(accountKey(username), 5, 300000, now);
    }

    public synchronized void succeeded(String username) { windows.remove(accountKey(username)); }

    private long reserve(String key, int maximum, long duration, long now) {
        Window window = windows.get(key);
        if (window != null && window.count() >= maximum) return Math.max(1, (window.expiresAt() - now + 999) / 1000);
        windows.put(key, window == null ? new Window(1, now + duration) : new Window(window.count() + 1, window.expiresAt()));
        return 0;
    }

    private String accountKey(String username) {
        String value = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return "login:account:" + (value.length() <= 64 ? value : "invalid");
    }
}
