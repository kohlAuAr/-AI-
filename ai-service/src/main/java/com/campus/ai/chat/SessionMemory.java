package com.campus.ai.chat;

import com.campus.ai.config.AiSettings;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Durable history is authoritative; cached messages are scoped to account and current club access. */
@Component
public class SessionMemory {
    private final StringRedisTemplate redis; private final AiSettings settings; private final ObjectMapper mapper;
    public SessionMemory(StringRedisTemplate redis, AiSettings settings, ObjectMapper mapper) { this.redis = redis; this.settings = settings; this.mapper = mapper; }
    private String key(String scope, String id) { return "campus:ai:v2:" + scope + ":" + id; }
    public List<Map<String, String>> recent(String scope, String id, List<Map<String, String>> authorized) {
        if (settings.redisEnabled()) {
            try {
                String cached = redis.opsForValue().get(key(scope, id));
                if (cached != null) {
                    List<Map<String, String>> messages = mapper.readValue(cached, new TypeReference<>() {});
                    if (messages.equals(authorized)) return messages;
                }
            } catch (org.springframework.dao.DataAccessException | java.io.IOException ignored) { /* Read authorized durable history. */ }
        }
        return authorized;
    }
    public void refresh(String scope, String id, List<Map<String, String>> messages) {
        if (!settings.redisEnabled()) return;
        try { redis.opsForValue().set(key(scope, id), mapper.writeValueAsString(messages), Duration.ofHours(2)); }
        catch (org.springframework.dao.DataAccessException | java.io.IOException ignored) { /* Durable turn is already committed. */ }
    }
}
