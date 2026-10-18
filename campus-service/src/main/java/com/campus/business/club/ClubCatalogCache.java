package com.campus.business.club;

import com.campus.business.club.ClubController.ClubView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Public club fields only. Member counts and admission decisions are never cached. */
@Component
public class ClubCatalogCache {
    private static final Duration TTL = Duration.ofMinutes(5);
    // An update writes a tombstone. An older reader cannot refill over that tombstone.
    private static final DefaultRedisScript<Long> FILL = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if (not current and ARGV[1] == '') or current == ARGV[1] then
              redis.call('SETEX', KEYS[1], ARGV[3], ARGV[2]); return 1
            end
            return 0
            """, Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private volatile long bypassUntil;
    public ClubCatalogCache(StringRedisTemplate redis, ObjectMapper mapper, @Value("${campus.redis-enabled:false}") boolean enabled) {
        this.redis = redis; this.mapper = mapper; this.enabled = enabled;
    }
    private String key(Long id) { return "campus:business:club:v1:" + id; }
    public boolean enabled() { return enabled && System.currentTimeMillis() >= bypassUntil; }
    public ClubView get(Long id, Supplier<ClubView> loader) {
        if (!enabled()) return loader.get();
        String observed;
        try { observed = redis.opsForValue().get(key(id)); }
        catch (DataAccessException error) { bypass(); return loader.get(); }
        if (observed != null && !observed.startsWith("!")) {
            try {
                ClubView cached = mapper.readValue(observed, ClubView.class);
                if (cached != null && id.equals(cached.id())) return cached;
            } catch (JsonProcessingException ignored) { /* Replace malformed derived data. */ }
        }
        ClubView loaded = loader.get();
        try { redis.execute(FILL, List.of(key(id)), observed == null ? "" : observed, mapper.writeValueAsString(loaded), "300"); }
        catch (DataAccessException error) { bypass(); }
        catch (JsonProcessingException ignored) { /* Database result remains usable. */ }
        return loaded;
    }
    public void evictAfterCommit(Long id) {
        if (!enabled) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { evict(id); }
            });
        } else evict(id);
    }
    private void evict(Long id) {
        try { redis.opsForValue().set(key(id), "!" + UUID.randomUUID(), TTL); }
        catch (DataAccessException error) { bypass(); }
    }
    // A failed invalidation must not expose an old entry when Redis reconnects.
    private void bypass() { bypassUntil = System.currentTimeMillis() + TTL.toMillis(); }
}
