package com.campus.business;

import com.campus.business.ai.AiServiceClient;
import com.campus.business.club.*;
import com.campus.business.identity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:recommendation_campus;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class RecommendationApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AccountRepository accounts;
    @Autowired ClubRepository clubs;
    @MockitoBean AiServiceClient ai;
    Account student;
    MockHttpSession session;
    Club photo, closed;
    @BeforeEach void setup() throws Exception {
        student = accounts.save(new Account("recommend-" + UUID.randomUUID(), accounts.findByUsername("student").orElseThrow().getPasswordHash(), "推荐测试学生", "计算机", "STUDENT"));
        session = (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).param("username", student.getUsername()).param("password", "CampusDemo123!"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        photo = clubs.save(new Club("摄影测试-" + UUID.randomUUID(), "艺术", "摄影采风，欢迎零基础", "摄影", "校区"));
        closed = new Club("暂停招新-" + UUID.randomUUID(), "艺术", "摄影", "摄影", "校区");
        closed.configureRecruitment(null, false, "", "", ""); closed = clubs.save(closed);
    }
    Map<String, Object> profile(String text) { return Map.of("name", "推荐测试学生", "major", "计算机", "interests", List.of("摄影"), "availableTime", "周三晚上", "interestDescription", text); }
    void save(String text) throws Exception { mvc.perform(put("/api/profile").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(profile(text)))).andExpect(status().isOk()); }
    @Test void profilePersistsFreeTextAndKeepsLegacyRequestsCompatible() throws Exception {
        save("  喜欢记录校园生活，想学剪视频  ");
        mvc.perform(get("/api/profile").session(session)).andExpect(jsonPath("$.interestDescription").value("喜欢记录校园生活，想学剪视频"));
        Map<String, Object> legacy = new HashMap<>(profile("")); legacy.remove("interestDescription");
        mvc.perform(put("/api/profile").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(legacy))).andExpect(status().isOk());
        assertThat(accounts.findById(student.getId()).orElseThrow().getInterestDescription()).isEqualTo("喜欢记录校园生活，想学剪视频");
        mvc.perform(put("/api/profile").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(profile("字".repeat(1001))))).andExpect(status().isBadRequest());
        save("");
        assertThat(accounts.findById(student.getId()).orElseThrow().getInterestDescription()).isEmpty();
        verifyNoInteractions(ai);
    }
    @Test void recommendationRequiresLoginCsrfAndFreeText() throws Exception {
        mvc.perform(post("/api/ai/recommendations").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/ai/recommendations").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/ai/recommendations").session(session).with(csrf())).andExpect(status().isBadRequest());
        verifyNoInteractions(ai);
    }
    @Test void usesSessionProfileAndCurrentClubsInsteadOfCallerSuppliedIdentity() throws Exception {
        save("喜欢记录校园生活");
        when(ai.recommend(anyMap())).thenAnswer(call -> {
            Map<?, ?> body = call.getArgument(0);
            assertThat(body.get("interest")).isEqualTo("喜欢记录校园生活");
            assertThat(body.toString()).doesNotContain(closed.getName(), "伪造兴趣", "password", "username");
            assertThat(body.toString()).contains(photo.getDescription());
            return mapper.readTree(mapper.writeValueAsString(Map.of("items", List.of(Map.of("clubId", closed.getId(), "score", 1), Map.of("clubId", photo.getId(), "score", 0.9), Map.of("clubId", 999999, "score", 0.8)))));
        });
        mvc.perform(post("/api/ai/recommendations").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":999,\"interest\":\"伪造兴趣\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.method").value("SEMANTIC_COSINE"))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].clubId").value(photo.getId()));
    }
    @Test void recruitmentClosureDuringEmbeddingIsRechecked() throws Exception {
        save("喜欢记录校园生活");
        when(ai.recommend(anyMap())).thenAnswer(call -> {
            photo.configureRecruitment(null, false, "", "", ""); clubs.save(photo);
            return mapper.readTree("{\"items\":[{\"clubId\":" + photo.getId() + ",\"score\":0.9}]}");
        });
        mvc.perform(post("/api/ai/recommendations").session(session).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
    }
    @Test void unavailableAiDoesNotBlockProfileOrClubQueries() throws Exception {
        when(ai.recommend(anyMap())).thenThrow(new ResourceAccessException("offline"));
        save("喜欢记录校园生活");
        mvc.perform(post("/api/ai/recommendations").session(session).with(csrf())).andExpect(status().isServiceUnavailable());
        save("兴趣可以继续修改");
        mvc.perform(get("/api/profile").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.interestDescription").value("兴趣可以继续修改"));
        mvc.perform(get("/api/clubs")).andExpect(status().isOk());
    }
    @Test void clubEditedDuringEmbeddingCannotDisplayObsoleteScoreWithNewDescription() throws Exception {
        save("喜欢记录校园生活");
        when(ai.recommend(anyMap())).thenAnswer(call -> {
            photo.update(new ClubManagementController.ClubRequest(photo.getName(), "科技", "编程实践", "编程", "校区", true, "欢迎", "周三", "学生中心"));
            clubs.save(photo);
            return mapper.readTree("{\"items\":[{\"clubId\":" + photo.getId() + ",\"score\":0.9}]}");
        });
        mvc.perform(post("/api/ai/recommendations").session(session).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
    }
}
