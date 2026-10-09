package com.campus.ai;

import com.campus.ai.chat.*;
import com.campus.ai.knowledge.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Native protocol fixture with synthetic vectors, not real model quality evidence. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:ollama_rag;DB_CLOSE_DELAY=-1", "ai.mode=ollama", "ai.redis-enabled=false"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class OllamaRagIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> paths = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean rejectEmbedding = new AtomicBoolean(false);
    private static final AtomicInteger chatStatus = new AtomicInteger(200);
    private static final AtomicReference<String> chatPayload = new AtomicReference<>();
    private static String lastMessages = "";
    private static final HttpServer provider = startProvider();
    @Autowired KnowledgeService knowledge;
    @Autowired ChatService chat;
    @Autowired ChunkRepository chunks;
    @Autowired DocumentRepository documents;
    @Autowired ConversationRepository conversations;
    @Autowired MockMvc mvc;

    private static HttpServer startProvider() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                paths.add(path);
                assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isNull();
                var request = JSON.readTree(exchange.getRequestBody());
                byte[] body;
                int status = 200;
                if (path.equals("/api/embed")) {
                    assertThat(request.path("model").asText()).isEqualTo("fixture-embedding");
                    if (rejectEmbedding.get()) status = 401;
                    List<List<Integer>> vectors = new ArrayList<>();
                    for (var text : request.path("input")) {
                        boolean image = text.asText().contains("影像") || text.asText().contains("镜头");
                        vectors.add(image ? List.of(1, 0) : List.of(0, 1));
                    }
                    body = JSON.writeValueAsBytes(Map.of("model", "fixture-embedding", "embeddings", vectors));
                } else if (path.equals("/api/chat")) {
                    assertThat(request.path("model").asText()).isEqualTo("fixture-chat");
                    assertThat(request.path("stream").asBoolean(true)).isFalse();
                    assertThat(request.path("tools").size()).isZero();
                    assertThat(request.path("options").path("num_predict").asInt()).isEqualTo(600);
                    lastMessages = request.path("messages").toString();
                    status = chatStatus.get();
                    body = chatPayload.get().getBytes(StandardCharsets.UTF_8);
                } else {
                    status = 404; body = new byte[0];
                }
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body); exchange.close();
            });
            server.start(); return server;
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + provider.getAddress().getPort();
        registry.add("ai.chat.base-url", () -> url);
        registry.add("ai.embedding.base-url", () -> url);
        registry.add("ai.chat.model", () -> "fixture-chat");
        registry.add("ai.embedding.model", () -> "fixture-embedding");
    }

    @BeforeEach
    void reset() {
        rejectEmbedding.set(false); chatStatus.set(200);
        chatPayload.set("{\"model\":\"fixture-chat\",\"message\":{\"role\":\"assistant\",\"content\":\"欢迎影像新手，可参加交流 [1]\"},\"done\":true}");
        lastMessages = ""; paths.clear();
        conversations.deleteAll(); chunks.deleteAll(); documents.deleteAll();
    }

    @AfterAll
    static void stopProvider() { provider.stop(0); }

    private KnowledgeService.DocumentView imageDocument() {
        return knowledge.ingest("image.md", "影像实践交流欢迎新手。可以使用手机记录生活。".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void nativeIndexGenerationReferencesAndExplicitFollowUpArePersisted() {
        var doc = imageDocument();
        assertThat(doc.embeddingVersion()).contains("OLLAMA", "fixture-embedding");
        assertThat(imageDocument().id()).isEqualTo(doc.id());
        assertThat(paths).containsExactly("/api/embed");
        var reply = chat.ask("镜头构图如何学习", null);
        assertThat(reply.mode()).isEqualTo("OLLAMA");
        assertThat(reply.retrieval()).isEqualTo("KEYWORD_VECTOR");
        assertThat(reply.references()).hasSize(1);
        assertThat(reply.references().get(0).documentId()).isEqualTo(doc.id());
        assertThat(reply.answer()).contains("[1]");
        assertThat(lastMessages).contains("参考资料", "image.md", "影像实践交流", "资料未说明");
        chat.ask("镜头学习能用手机吗", reply.conversationId());
        assertThat(lastMessages).contains("镜头构图如何学习", "欢迎影像新手");
        assertThat(chat.history(reply.conversationId())).hasSize(2);
        assertThat(chat.history(reply.conversationId()).get(0).references()).isEqualTo(reply.references());
        assertThat(paths).containsExactly("/api/embed", "/api/embed", "/api/chat", "/api/embed", "/api/chat");
    }

    @Test
    void noEvidenceSkipsGenerationEvenWithConversationHistory() {
        imageDocument();
        var first = chat.ask("镜头构图如何学习", null);
        paths.clear();
        var reply = chat.ask("abcdef不存在的信息", first.conversationId());
        assertThat(reply.references()).isEmpty();
        assertThat(reply.answer()).contains("没有找到足够相关的依据").doesNotContain("[1]");
        assertThat(paths).containsExactly("/api/embed");
        assertThat(chat.history(first.conversationId())).hasSize(2);
    }

    @Test
    void embeddingFailureLeavesNoPartialDocumentOrConversation() {
        rejectEmbedding.set(true);
        assertThatThrownBy(this::imageDocument).hasMessageContaining("401");
        assertThat(documents.count()).isZero();
        assertThat(chunks.count()).isZero();
        rejectEmbedding.set(false); imageDocument(); rejectEmbedding.set(true);
        assertThatThrownBy(() -> chat.ask("镜头构图", null)).hasMessageContaining("401");
        assertThat(conversations.count()).isZero();
        assertThat(paths).doesNotContain("/api/chat");
    }

    @Test
    void chatFailureAndMalformedOrBlankOutputAreSanitizedAndNotSaved() throws Exception {
        imageDocument();
        String request = "{\"question\":\"镜头构图\"}";
        chatStatus.set(503); chatPayload.set("private-upstream-error-secret");
        mvc.perform(post("/internal/chat").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.detail").value("模型接口返回 HTTP 503，请检查模型配置与额度"));
        chatStatus.set(200);
        for (String payload : List.of("not-json", "{\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"done\":true}")) {
            chatPayload.set(payload);
            mvc.perform(post("/internal/chat").contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isBadGateway());
        }
        assertThat(conversations.count()).isZero();
        assertThat(paths.stream().filter("/api/chat"::equals).count()).isEqualTo(3);
    }

    @Test
    void oldKeywordDocumentsArePreservedWhenReuploadedForVectorIndexing() {
        String content = "影像实践交流欢迎新手。可以使用手机记录生活。";
        var old = documents.save(new KnowledgeDocument("old-image.md", "old-fixture", content, "local-keyword", 1));
        chunks.save(new KnowledgeChunk(old, 0, content, null));
        var indexed = imageDocument();
        assertThat(indexed.id()).isNotEqualTo(old.getId());
        assertThat(documents.findById(old.getId()).orElseThrow().getEmbeddingVersion()).isEqualTo("local-keyword");
        assertThat(documents.count()).isEqualTo(2);
        assertThat(imageDocument().id()).isEqualTo(indexed.id());
    }
}
