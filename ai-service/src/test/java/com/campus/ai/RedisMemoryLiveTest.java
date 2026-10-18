package com.campus.ai;

import com.campus.ai.chat.SessionMemory;
import com.campus.ai.config.AiSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.assertj.core.api.Assertions.*;

@EnabledIfSystemProperty(named = "redis.test.port", matches = "[0-9]+")
class RedisMemoryLiveTest {
    @Test void realCacheTtlExpiryAndRevokedContextRepair() throws Exception {
        var config = new RedisStandaloneConfiguration("127.0.0.1", Integer.parseInt(System.getProperty("redis.test.port")));
        config.setDatabase(14); var factory = new LettuceConnectionFactory(config); factory.afterPropertiesSet(); factory.start();
        var redis = new StringRedisTemplate(factory); String scope = "991:STUDENT:[]", id = UUID.randomUUID().toString();
        String key = "campus:ai:v2:" + scope + ":" + id;
        try {
            var memory = new SessionMemory(redis, new AiSettings(AiSettings.Mode.LOCAL, true, null, null, AiSettings.EmbeddingProvider.NONE), new ObjectMapper());
            var original = List.of(Map.of("role", "assistant", "content", "旧资料"));
            memory.refresh(scope, id, original);
            assertThat(redis.getExpire(key)).isBetween(1L, 7200L); assertThat(memory.recent(scope, id, original)).isEqualTo(original);
            var authorized = List.of(Map.of("role", "assistant", "content", "资料目前不可访问"));
            assertThat(memory.recent(scope, id, authorized)).isEqualTo(authorized);
            assertThat(redis.opsForValue().get(key)).doesNotContain("旧资料").contains("不可访问");
            redis.opsForValue().set(key, "broken-json");
            assertThat(memory.recent(scope, id, authorized)).isEqualTo(authorized);
            redis.expire(key, Duration.ofMillis(50)); Thread.sleep(100);
            assertThat(redis.hasKey(key)).isFalse(); assertThat(memory.recent(scope, id, authorized)).isEqualTo(authorized);
            assertThat(redis.getExpire(key)).isBetween(1L, 7200L);
        } finally { redis.delete(key); factory.destroy(); }
    }
}
