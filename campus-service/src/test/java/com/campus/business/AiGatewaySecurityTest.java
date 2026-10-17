package com.campus.business;

import com.campus.business.club.ClubRepository;
import com.campus.business.identity.AccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:gateway_security;DB_CLOSE_DELAY=-1", "campus.ai-base-url=http://127.0.0.1:1", "service.internal-token=gateway-test-token"})
@AutoConfigureMockMvc
class AiGatewaySecurityTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper; @Autowired AccountRepository accounts; @Autowired ClubRepository clubs;
    MockHttpSession student, manager; long photo, code;
    @BeforeEach void setup() throws Exception {
        student = login("student"); manager = login("photo_manager");
        photo = clubs.findBySlug("photo").orElseThrow().getId(); code = clubs.findBySlug("code").orElseThrow().getId();
    }
    MockHttpSession login(String name) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).param("username", name).param("password", "CampusDemo123!"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
    @Test void browserCannotForgeServiceIdentityOrReadAnonymousHistory() throws Exception {
        mvc.perform(get("/api/ai/knowledge").header("X-Actor-Id", "1").header("X-Actor-Role", "PLATFORM_ADMIN")).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/ai/tools").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/ai/tools").header("X-Internal-Token", "wrong").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/ai/conversations")).andExpect(status().isUnauthorized());
    }
    @Test void internalFilterUsesCanonicalServletPath() throws Exception {
        var request = new MockHttpServletRequest("POST", "/other/../internal/ai/tools"); request.setServletPath("/internal/ai/tools");
        var response = new MockHttpServletResponse();
        new com.campus.business.ai.InternalAiAuthentication("test-token").doFilter(request, response, (req, res) -> { throw new AssertionError("Unauthorized request reached handler"); });
        org.junit.jupiter.api.Assertions.assertEquals(401, response.getStatus());
    }
    @Test void uploadAndPlanningRolesAreCheckedEvenWhileAiIsOffline() throws Exception {
        var file = new MockMultipartFile("file", "rules.md", "text/markdown", "测试资料".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/ai/knowledge").file(file).session(student).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/ai/knowledge").file(file).param("visibility", "CLUB").param("clubId", Long.toString(code)).session(manager).with(csrf())).andExpect(status().isForbidden());
        for (var session : new MockHttpSession[]{student, manager}) {
            mvc.perform(post("/api/ai/plans").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("clubId", code, "requirement", "新手活动")))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/ai/plans").session(manager).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("clubId", photo, "requirement", "新手活动")))).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/manage/clubs/" + photo + "/activities").session(manager)).andExpect(status().isOk());
    }
    @Test void toolAllowlistIsReadOnlyAndResolvesActorFromDatabase() throws Exception {
        long user = accounts.findByUsername("student").orElseThrow().getId();
        mvc.perform(post("/internal/ai/tools").header("X-Internal-Token", "gateway-test-token").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("actorId", user, "tool", "search_clubs", "query", "摄影"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].name").value("光影摄影社"));
        mvc.perform(post("/internal/ai/tools").header("X-Internal-Token", "gateway-test-token").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("actorId", user, "tool", "publish_activity")))).andExpect(status().isBadRequest());
        mvc.perform(post("/internal/ai/tools").header("X-Internal-Token", "gateway-test-token").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("actorId", 999999, "tool", "search_clubs")))).andExpect(status().isUnauthorized());
    }
    @Test void modelConfigurationIsPlatformOnlyWithCsrfEvenWhileAiIsOffline() throws Exception {
        mvc.perform(get("/api/platform/models")).andExpect(status().isUnauthorized());
        for (var session : new MockHttpSession[]{student, manager}) {
            mvc.perform(get("/api/platform/models").session(session).header("X-Actor-Role", "PLATFORM_ADMIN")).andExpect(status().isForbidden());
            mvc.perform(post("/api/platform/models/CHAT/save").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        }
        var admin = login("platform_admin");
        mvc.perform(post("/api/platform/models/CHAT/save").session(admin).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/platform/models/CHAT/delete").session(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/platform/models").session(admin)).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/clubs")).andExpect(status().isOk());
    }
}
