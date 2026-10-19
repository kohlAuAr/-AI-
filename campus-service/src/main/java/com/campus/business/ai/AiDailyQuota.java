package com.campus.business.ai;

import java.time.*;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Conservative admitted-request budget, not token accounting. UTC daily buckets, no refunds. */
@Component
public class AiDailyQuota {
    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>("""
            local user = tonumber(redis.call('GET', KEYS[1]) or '0')
            local total = tonumber(redis.call('GET', KEYS[2]) or '0')
            if user >= tonumber(ARGV[1]) or total >= tonumber(ARGV[2]) then return 1 end
            redis.call('INCR', KEYS[1]); redis.call('EXPIREAT', KEYS[1], ARGV[3])
            redis.call('INCR', KEYS[2]); redis.call('EXPIREAT', KEYS[2], ARGV[3])
            return 0
            """, Long.class);
    private final StringRedisTemplate redis;
    private final boolean enabled;
    private final int perAccount, platform;
    public AiDailyQuota(StringRedisTemplate redis, @Value("${campus.ai-daily-quota-enabled:false}") boolean enabled,
                        @Value("${campus.ai-daily-account-limit:50}") int perAccount, @Value("${campus.ai-daily-platform-limit:1000}") int platform) {
        if (perAccount < 1 || platform < 1) throw new IllegalArgumentException("Daily AI limits must be positive");
        this.redis = redis; this.enabled = enabled; this.perAccount = perAccount; this.platform = platform;
    }
    public void check(long accountId) {
        if (!enabled) return;
        var now = Instant.now(); var date = now.atZone(ZoneOffset.UTC).toLocalDate();
        var reset = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        long seconds = Math.max(1, (Duration.between(now, reset).toMillis() + 999) / 1000);
        Long rejected;
        try { rejected = redis.execute(RESERVE, List.of("campus:ai-daily:v1:" + date + ":user:" + accountId, "campus:ai-daily:v1:" + date + ":total"), Integer.toString(perAccount), Integer.toString(platform), Long.toString(reset.getEpochSecond() + 3600)); }
        catch (DataAccessException error) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 每日额度暂不可用，请稍后重试；校园业务仍可使用"); }
        if (rejected == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 每日额度暂不可用");
        if (rejected != 0) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "当前账号或平台的 AI 今日额度已用完，UTC 次日恢复；校园业务仍可使用") {
            @Override public HttpHeaders getHeaders() { var headers = new HttpHeaders(); headers.set("Retry-After", Long.toString(seconds)); return headers; }
        };
    }
}
