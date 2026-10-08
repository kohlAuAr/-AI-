package com.campus.ai.chat;

import com.campus.ai.config.AiSettings;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** PaiSmart's durable history + short Redis context split, without billing/session-generation state. */
@Component
public class SessionMemory {
    private static final Logger log = LoggerFactory.getLogger(SessionMemory.class);
    private final ConversationRepository conversations;
    private final StringRedisTemplate redis;
    private final AiSettings settings;
    private final ObjectMapper mapper;

    public SessionMemory(ConversationRepository conversations, StringRedisTemplate redis, AiSettings settings, ObjectMapper mapper) {
        this.conversations = conversations;
        this.redis = redis;
        this.settings = settings;
        this.mapper = mapper;
    }

    public List<Map<String, String>> recent(String id) {
        if (settings.redisEnabled()) {
            try {
                String cached = redis.opsForValue().get("campus:ai:session:" + id);
                if (cached != null) return mapper.readValue(cached, new TypeReference<>() {});
            } catch (org.springframework.dao.DataAccessException | java.io.IOException e) {
                log.warn("Redis 短期上下文不可用，读取数据库历史");
            }
        }
        return databaseMessages(id);
    }

    public void refresh(String id) {
        if (!settings.redisEnabled()) return;
        try {
            redis.opsForValue().set("campus:ai:session:" + id, mapper.writeValueAsString(databaseMessages(id)), Duration.ofHours(2));
        } catch (org.springframework.dao.DataAccessException | java.io.IOException e) {
            log.warn("Redis 上下文更新失败；数据库历史已经保存");
        }
    }

    private List<Map<String, String>> databaseMessages(String id) {
        List<ConversationTurn> turns = new ArrayList<>(conversations.findTop6ByConversationIdOrderByIdDesc(id));
        Collections.reverse(turns);
        List<Map<String, String>> messages = new ArrayList<>();
        for (ConversationTurn turn : turns) {
            messages.add(Map.of("role", "user", "content", turn.getQuestion()));
            messages.add(Map.of("role", "assistant", "content", turn.getAnswer()));
        }
        return messages;
    }
}
