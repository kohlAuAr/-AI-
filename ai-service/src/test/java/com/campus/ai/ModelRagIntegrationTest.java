package com.campus.ai;

import com.campus.ai.knowledge.*;
import com.campus.ai.chat.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import static org.assertj.core.api.Assertions.*;

/** Local protocol fixture, not a real LLM or a recommendation quality experiment. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:model_rag;DB_CLOSE_DELAY=-1", "ai.mode=openai", "ai.redis-enabled=false"})
@ActiveProfiles("test")
class ModelRagIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicBoolean rejectEmbedding = new AtomicBoolean(false);
    private static String lastMessages = "";
    private static final HttpServer provider = startProvider();
    @Autowired KnowledgeService knowledge;
    @Autowired ChatService chat;
    @Autowired ChunkRepository chunks;
    @Autowired DocumentRepository documents;
    @Autowired ConversationRepository conversations;

    private static HttpServer startProvider() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/embeddings", exchange -> {
                var request = JSON.readTree(exchange.getRequestBody());
                if (rejectEmbedding.get()) { exchange.sendResponseHeaders(401, -1); exchange.close(); return; }
                List<Map<String, Object>> data = new ArrayList<>();
                int index = 0;
                for (var text : request.path("input")) {
                    boolean image = text.asText().contains("影像") || text.asText().contains("镜头");
                    data.add(Map.of("index", index++, "embedding", image ? List.of(1, 0) : List.of(0, 1)));
                }
                Collections.reverse(data);
                byte[] body = JSON.writeValueAsBytes(Map.of("data", data));
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
            });
            server.createContext("/v1/chat/completions", exchange -> {
                lastMessages = JSON.readTree(exchange.getRequestBody()).path("messages").toString();
                byte[] body = JSON.writeValueAsBytes(Map.of("choices", List.of(Map.of("message", Map.of("content", "可参加影像交流 [1]")))));
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    @DynamicPropertySource
    static void modelConfiguration(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + provider.getAddress().getPort() + "/v1";
        registry.add("ai.chat.base-url", () -> url);
        registry.add("ai.embedding.base-url", () -> url);
        registry.add("ai.chat.model", () -> "protocol-fixture");
        registry.add("ai.embedding.model", () -> "protocol-fixture");
    }

    @BeforeEach
    void reset() {
        rejectEmbedding.set(false); lastMessages = "";
        conversations.deleteAll(); chunks.deleteAll(); documents.deleteAll();
    }

    @AfterAll
    static void stopProvider() { provider.stop(0); }

    @Test
    void vectorRetrievalFeedsGenerationAndFollowUpHistory() {
        var imageDoc = knowledge.ingest("image.md", "影像实践交流欢迎新手。".getBytes(StandardCharsets.UTF_8));
        knowledge.ingest("sports.md", "篮球训练欢迎新手。".getBytes(StandardCharsets.UTF_8));
        var reply = chat.ask("镜头构图如何学习", null);
        assertThat(reply.mode()).isEqualTo("OPENAI");
        assertThat(reply.retrieval()).isEqualTo("KEYWORD_VECTOR");
        assertThat(reply.references()).hasSize(1);
        assertThat(reply.references().get(0).documentId()).isEqualTo(imageDoc.id());
        assertThat(reply.answer()).contains("[1]");
        chat.ask("镜头学习需要什么", reply.conversationId());
        assertThat(lastMessages).contains("镜头构图如何学习", "可参加影像交流 [1]");
        assertThat(chat.history(reply.conversationId())).hasSize(2);
    }

    @Test
    void providerFailureDoesNotLeavePartialDocuments() {
        rejectEmbedding.set(true);
        assertThatThrownBy(() -> knowledge.ingest("image.md", "影像实践资料".getBytes(StandardCharsets.UTF_8))).hasMessageContaining("401");
        assertThat(documents.count()).isZero();
        assertThat(chunks.count()).isZero();
    }
}
