package com.campus.business.ai;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Shared per-account window for expensive AI operations, not a billing ledger. */
@Component
public class AiRateLimiter {
    private static final DefaultRedisScript<Long> LIMIT = new DefaultRedisScript<>("""
            local count = tonumber(redis.call('GET', KEYS[1]) or '0')
            if count >= tonumber(ARGV[1]) then return math.max(1, redis.call('TTL', KEYS[1])) end
            count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], 60) end
            return 0
            """, Long.class);
    private final StringRedisTemplate redis;
    private final boolean enabled;
    private final int limit;
    private final AiDailyQuota daily;
    public AiRateLimiter(StringRedisTemplate redis, @Value("${campus.redis-enabled:false}") boolean enabled,
                         @Value("${campus.ai-requests-per-minute:5}") int limit) {
        this(redis, enabled, limit, null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public AiRateLimiter(StringRedisTemplate redis, @Value("${campus.redis-enabled:false}") boolean enabled,
                         @Value("${campus.ai-requests-per-minute:5}") int limit, AiDailyQuota daily) {
        if (limit < 1) throw new IllegalArgumentException("AI request limit must be positive");
        this.redis = redis; this.enabled = enabled; this.limit = limit;
        this.daily = daily;
    }
    public void check(long accountId) {
        if (!enabled) { if (daily != null) daily.check(accountId); return; }
        Long retry;
        try { retry = redis.execute(LIMIT, List.of("campus:business:ai-rate:v1:" + accountId), Integer.toString(limit)); }
        catch (DataAccessException error) { throw unavailable(); }
        if (retry == null) throw unavailable();
        if (retry > 0) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "AI 请求较频繁，请在 " + retry + " 秒后重试；社团和活动功能仍可使用。") {
                @Override public HttpHeaders getHeaders() {
                    var headers = new HttpHeaders(); headers.set("Retry-After", retry.toString()); return headers;
                }
            };
        }
        if (daily != null) daily.check(accountId);
    }
    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 访问控制暂不可用，请稍后再试；社团、活动和报名仍可使用。");
    }
}
