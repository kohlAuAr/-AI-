package com.campus.ai;

import com.campus.ai.chat.*;
import com.campus.ai.knowledge.*;
import com.campus.ai.security.AiActor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Authentication filters are active; no default auth fixture or caller-supplied actor attribute. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:account_isolation;DB_CLOSE_DELAY=-1", "ai.mode=local", "ai.redis-enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountIsolationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper; @Autowired KnowledgeService knowledge;
    @Autowired ChatService chat; @Autowired ConversationRepository turns;
    private MockHttpServletRequestBuilder actor(MockHttpServletRequestBuilder request, long id, String role, String clubs) {
        return request.header("X-Internal-Token", "protocol-fixture-internal-token").header("X-Actor-Id", id)
                .header("X-Actor-Role", role).header("X-Actor-Clubs", clubs).header("X-Actor-Managed-Clubs", role.equals("MANAGER") ? clubs : "");
    }
    @Test void internalSecretAndIdentityAreRequired() throws Exception {
        mvc.perform(get("/internal/knowledge")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/knowledge").header("X-Internal-Token", "wrong").header("X-Actor-Id", "1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/knowledge").header("X-Internal-Token", "protocol-fixture-internal-token")).andExpect(status().isBadRequest());
        mvc.perform(actor(get("/internal/knowledge"), -1, "STUDENT", "")).andExpect(status().isBadRequest());
    }
    @Test void filterUsesCanonicalServletPathWhenRawUriDiffers() throws Exception {
        var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/other/../internal/knowledge");
        request.setServletPath("/internal/knowledge"); var response = new org.springframework.mock.web.MockHttpServletResponse();
        new com.campus.ai.security.InternalAuthentication("test-token").doFilter(request, response, (req, res) -> { throw new AssertionError("Unauthorized request reached handler"); });
        assertThat(response.getStatus()).isEqualTo(401);
    }
    @Test void clubDocumentsAreFilteredBeforeRetrievalAndHistoryIsReauthorized() throws Exception {
        long club = 8001; var manager = new AiActor(11, "MANAGER", Set.of(club), Set.of(club));
        var member = new AiActor(12, "STUDENT", Set.of(club), Set.of());
        var outsider = new AiActor(13, "STUDENT", Set.of(), Set.of());
        var doc = knowledge.ingest("private.md", "紫鹭社内部口令是蓝色风车。".getBytes(StandardCharsets.UTF_8), "CLUB", club, manager);
        mvc.perform(actor(get("/internal/knowledge/" + doc.id()), 13, "STUDENT", "")).andExpect(status().isNotFound());
        mvc.perform(actor(get("/internal/knowledge/" + doc.id()), 99, "PLATFORM_ADMIN", "")).andExpect(status().isNotFound());
        var reply = chat.ask("紫鹭社内部口令", null, member);
        assertThat(reply.references()).extracting(HybridSearchService.Citation::documentId).contains(doc.id());
        assertThat(chat.ask("紫鹭社内部口令", null, outsider).references()).isEmpty();
        var revoked = chat.history(new AiActor(12, "STUDENT", Set.of(), Set.of()), reply.conversationId()).get(0);
        assertThat(revoked.answer()).doesNotContain("蓝色风车").contains("不可访问");
        assertThat(revoked.references()).isEmpty();
        mvc.perform(actor(get("/internal/conversations/" + reply.conversationId()), 13, "STUDENT", "")).andExpect(status().isNotFound());
        mvc.perform(actor(post("/internal/chat"), 13, "STUDENT", "").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("question", "继续回答", "conversationId", reply.conversationId())))).andExpect(status().isNotFound());
        mvc.perform(actor(get("/internal/conversations"), 12, "STUDENT", "8001")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(reply.conversationId()));
    }
    @Test void uploadRightsAndDeduplicationRespectScope() throws Exception {
        byte[] text = "范围隔离测试文档".getBytes(StandardCharsets.UTF_8);
        var file = new MockMultipartFile("file", "scope.md", "text/markdown", text);
        mvc.perform(actor(multipart("/internal/knowledge").file(file), 12, "STUDENT", "8002")).andExpect(status().isForbidden());
        mvc.perform(actor(multipart("/internal/knowledge").file(file).param("visibility", "CLUB").param("clubId", "8003"), 11, "MANAGER", "8002")).andExpect(status().isForbidden());
        var admin = new AiActor(99, "PLATFORM_ADMIN", Set.of(), Set.of());
        var manager = new AiActor(11, "MANAGER", Set.of(8002L), Set.of(8002L));
        long publicId = knowledge.ingest("scope.md", text, "PUBLIC", null, admin).id();
        long privateId = knowledge.ingest("scope.md", text, "CLUB", 8002L, manager).id();
        assertThat(publicId).isNotEqualTo(privateId);
        assertThat(knowledge.ingest("scope.md", text, "CLUB", 8002L, manager).id()).isEqualTo(privateId);
    }
    @Test void legacyUnownedConversationsAreNotClaimed() throws Exception {
        String id = UUID.randomUUID().toString();
        turns.saveAndFlush(new ConversationTurn(id, "旧问题", "旧答案", "[]", "LOCAL", "KEYWORD"));
        mvc.perform(actor(get("/internal/conversations/" + id), 12, "STUDENT", "")).andExpect(status().isNotFound());
        assertThat(turns.findByConversationIdOrderByIdAsc(id)).hasSize(1);
    }
}
