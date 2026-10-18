package com.campus.business;

import com.campus.business.ai.AiRateLimiter;
import com.campus.business.club.ClubCatalogCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

/** Opt in against isolated project Redis only: -Dredis.test.port=16379. Uses DB 15, never FLUSHDB. */
@EnabledIfSystemProperty(named = "redis.test.port", matches = "[0-9]+")
class RedisLiveTest {
    LettuceConnectionFactory factory; StringRedisTemplate redis;
    @BeforeEach void connect() {
        var config = new RedisStandaloneConfiguration("127.0.0.1", Integer.parseInt(System.getProperty("redis.test.port")));
        config.setDatabase(15); factory = new LettuceConnectionFactory(config); factory.afterPropertiesSet(); factory.start();
        redis = new StringRedisTemplate(factory);
    }
    @AfterEach void disconnect() { factory.destroy(); }
    @Test void realCacheHitExpiryAndConcurrentInvalidation() throws Exception {
        String key = "campus:business:club:v1:42"; redis.delete(key);
        try {
            var cache = new ClubCatalogCache(redis, new ObjectMapper(), true); var reads = new AtomicInteger();
            for (int i = 0; i < 2; i++) cache.get(42L, () -> { reads.incrementAndGet(); return RedisFeatureTest.club("旧资料"); });
            assertThat(reads.get()).isEqualTo(1);
            assertThat(redis.getExpire(key)).isBetween(1L, 300L);
            cache.evictAfterCommit(42L);
            assertThat(cache.get(42L, () -> RedisFeatureTest.club("新资料")).description()).isEqualTo("新资料");
            cache.evictAfterCommit(42L);
            cache.get(42L, () -> { cache.evictAfterCommit(42L); return RedisFeatureTest.club("迟到的旧读取"); });
            assertThat(redis.opsForValue().get(key)).startsWith("!");
            cache.get(42L, () -> RedisFeatureTest.club("最终资料"));
            assertThat(redis.opsForValue().get(key)).contains("最终资料");
            redis.expire(key, Duration.ofMillis(50)); Thread.sleep(100);
            assertThat(redis.hasKey(key)).isFalse();
            assertThat(cache.get(42L, () -> RedisFeatureTest.club("过期后读取")).description()).isEqualTo("过期后读取");
        } finally { redis.delete(key); }
    }
    @Test void realLuaCounterIsAtomicAcrossConcurrentCallsAndAccounts() throws Exception {
        long account = 900000000L + Math.abs(System.nanoTime() % 100000000);
        String key = "campus:business:ai-rate:v1:" + account;
        String other = "campus:business:ai-rate:v1:" + (account + 1);
        var executor = Executors.newFixedThreadPool(12);
        try {
            var limiter = new AiRateLimiter(redis, true, 5); var accepted = new AtomicInteger(); var rejected = new AtomicInteger();
            List<Callable<Void>> calls = new ArrayList<>();
            for (int i = 0; i < 30; i++) calls.add(() -> {
                try { limiter.check(account); accepted.incrementAndGet(); }
                catch (ResponseStatusException error) { assertThat(error.getStatusCode().value()).isEqualTo(429); rejected.incrementAndGet(); }
                return null;
            });
            for (var future : executor.invokeAll(calls)) future.get();
            assertThat(accepted.get()).isEqualTo(5); assertThat(rejected.get()).isEqualTo(25);
            assertThat(redis.opsForValue().get(key)).isEqualTo("5"); assertThat(redis.getExpire(key)).isBetween(1L, 60L);
            limiter.check(account + 1);
            redis.expire(key, Duration.ofMillis(50)); Thread.sleep(100); limiter.check(account);
            assertThat(redis.opsForValue().get(key)).isEqualTo("1");
        } finally { executor.shutdownNow(); redis.delete(List.of(key, other)); }
    }
}
