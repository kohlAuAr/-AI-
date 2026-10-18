package com.campus.business;

import com.campus.business.ai.AiRateLimiter;
import com.campus.business.club.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.transaction.support.*;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisFeatureTest {
    static ClubController.ClubView club(String text) {
        return new ClubController.ClubView(42L, "test", "测试社", "文化", text, "摄影", "主校区", false, true, "不限", "周末", "校园", 0);
    }
    @Test void cacheHitAndRedisFailureKeepDatabaseAuthoritative() throws Exception {
        var redis = mock(StringRedisTemplate.class); ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values); var mapper = new ObjectMapper();
        var cache = new ClubCatalogCache(redis, mapper, true); var reads = new AtomicInteger();
        when(values.get(anyString())).thenReturn(mapper.writeValueAsString(club("缓存")));
        assertThat(cache.get(42L, () -> { reads.incrementAndGet(); return club("数据库"); }).description()).isEqualTo("缓存");
        assertThat(reads.get()).isZero();
        doThrow(new RedisConnectionFailureException("offline")).when(values).set(anyString(), anyString(), any(Duration.class));
        cache.evictAfterCommit(42L);
        assertThat(cache.get(42L, () -> club("更新数据库")).description()).isEqualTo("更新数据库");
        assertThat(cache.enabled()).isFalse();
    }
    @Test void rollbackDoesNotInvalidateAndCommitInvalidatesAfterCommitOnly() {
        var redis = mock(StringRedisTemplate.class); ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values); var cache = new ClubCatalogCache(redis, new ObjectMapper(), true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.evictAfterCommit(42L); verifyNoInteractions(values);
            for (var hook : TransactionSynchronizationManager.getSynchronizations()) hook.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            verifyNoInteractions(values);
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.evictAfterCommit(42L); verifyNoInteractions(values);
            for (var hook : TransactionSynchronizationManager.getSynchronizations()) hook.afterCommit();
            verify(values).set(eq("campus:business:club:v1:42"), startsWith("!"), eq(Duration.ofMinutes(5)));
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
    @Test void disabledRedisDoesNotConnectAndRateFailureDoesNotAllowCalls() {
        var redis = mock(StringRedisTemplate.class);
        new AiRateLimiter(redis, false, 5).check(42);
        assertThat(new ClubCatalogCache(redis, new ObjectMapper(), false).get(42L, () -> club("数据库")).description()).isEqualTo("数据库");
        verifyNoInteractions(redis);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenThrow(new RedisConnectionFailureException("offline"));
        assertThatThrownBy(() -> new AiRateLimiter(redis, true, 5).check(42)).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(503));
    }
    @Test void rateLimitReturnsRetryAfter() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), eq(List.of("campus:business:ai-rate:v1:42")), any(Object[].class))).thenReturn(37L);
        assertThatThrownBy(() -> new AiRateLimiter(redis, true, 5).check(42)).isInstanceOfSatisfying(ResponseStatusException.class, error -> {
            assertThat(error.getStatusCode().value()).isEqualTo(429);
            assertThat(error.getHeaders().getFirst("Retry-After")).isEqualTo("37");
        });
    }
}
