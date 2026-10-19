package com.campus.business;

import com.campus.business.ai.AiDailyQuota;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.RedisConnectionFailureException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiDailyQuotaTest {
    @Test void quotaAdmitsRejectsAndFailsClosedWithoutAffectingOrdinaryQueries() {
        var redis = mock(StringRedisTemplate.class);
        new AiDailyQuota(redis, false, 2, 5).check(42); verifyNoInteractions(redis);
        var quota = new AiDailyQuota(redis, true, 2, 5);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L, 1L, null);
        quota.check(42);
        assertThatThrownBy(() -> quota.check(42)).hasMessageContaining("429");
        assertThatThrownBy(() -> quota.check(42)).hasMessageContaining("503");
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenThrow(new RedisConnectionFailureException("fixture offline"));
        assertThatThrownBy(() -> quota.check(42)).hasMessageContaining("503");
    }
}
