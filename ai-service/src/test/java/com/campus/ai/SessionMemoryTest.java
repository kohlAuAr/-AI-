package com.campus.ai;

import com.campus.ai.chat.SessionMemory;
import com.campus.ai.config.AiSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SessionMemoryTest {
    @Test void unavailableOrStaleRedisCannotReplaceAuthorizedDurableMessages() throws Exception {
        var redis = mock(StringRedisTemplate.class); ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var settings = new AiSettings(AiSettings.Mode.LOCAL, true, null, null, AiSettings.EmbeddingProvider.NONE);
        var memory = new SessionMemory(redis, settings, new ObjectMapper());
        List<Map<String, String>> safe = List.of(Map.of("role", "assistant", "content", "权限撤销，请重新提问"));
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("offline"));
        assertThat(memory.recent("12:STUDENT:[]", "session", safe)).isEqualTo(safe);
        doThrow(new RedisConnectionFailureException("offline")).when(values).set(anyString(), anyString(), any(Duration.class));
        assertThatCode(() -> memory.refresh("12:STUDENT:[]", "session", safe)).doesNotThrowAnyException();
        doReturn("[{\"role\":\"assistant\",\"content\":\"已撤销的私密资料\"}]").when(values).get(anyString());
        assertThat(memory.recent("12:STUDENT:[]", "session", safe)).isEqualTo(safe);
        verify(values, atLeastOnce()).get("campus:ai:v2:12:STUDENT:[]:session");
    }
}
