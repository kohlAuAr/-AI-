package com.campus.business;

import com.campus.business.ai.AiServiceClient;
import com.campus.business.identity.AccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:redis_gateway;DB_CLOSE_DELAY=-1", "campus.redis-enabled=true"})
@AutoConfigureMockMvc
class RedisGatewayTest {
    @Autowired MockMvc mvc; @Autowired AccountRepository accounts; @Autowired ObjectMapper mapper;
    @MockitoBean StringRedisTemplate redis; @MockitoBean AiServiceClient ai;
    MockHttpSession student;
    @BeforeEach void setup() throws Exception {
        student = (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).param("username", "student").param("password", "CampusDemo123!"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    @Test void sessionIdentityIsUsedAndRejectedCallsNeverReachModel() throws Exception {
        long id = accounts.findByUsername("student").orElseThrow().getId();
        when(redis.execute(any(RedisScript.class), eq(List.of("campus:business:ai-rate:v1:" + id)), any(Object[].class))).thenReturn(37L);
        for (String path : List.of("chat", "assistant")) {
            mvc.perform(post("/api/ai/" + path).session(student).with(csrf()).header("X-Actor-Id", "99999")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"测试\",\"userId\":99999}"))
                    .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "37"));
        }
        verifyNoInteractions(ai);
        mvc.perform(post("/api/ai/chat").session(student).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/ai/chat").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verify(redis, times(2)).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }
    @Test void redisOutageBlocksNewAiButNotBusinessOrHistoryReads() throws Exception {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenThrow(new RedisConnectionFailureException("offline"));
        ValueOperations<String, String> values = mock(ValueOperations.class); when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("offline"));
        mvc.perform(post("/api/ai/chat").session(student).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"测试\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("访问控制")));
        verifyNoInteractions(ai);
        mvc.perform(get("/api/clubs")).andExpect(status().isOk());
        mvc.perform(get("/api/activities")).andExpect(status().isOk());
        mvc.perform(get("/api/profile").session(student)).andExpect(status().isOk());
        when(ai.get(eq("conversations"), any())).thenReturn(mapper.readTree("[]"));
        mvc.perform(get("/api/ai/conversations").session(student)).andExpect(status().isOk());
    }
}
