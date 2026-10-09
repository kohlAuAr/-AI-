package com.campus.ai.recommendation;

import com.campus.ai.client.ModelClient;
import com.campus.ai.config.AiSettings;
import com.campus.ai.knowledge.HybridSearchService;
import com.campus.ai.knowledge.KnowledgeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Native protocol fixture proves wiring/cache, not real Chinese recommendation quality. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:ollama-recommendations;DB_CLOSE_DELAY=-1", "ai.mode=local", "ai.embedding-provider=ollama", "ai.redis-enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OllamaRecommendationTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static int calls, inputs, failure;
    static final HttpServer provider = provider();
    @Autowired RecommendationService recommendations;
    @Autowired InterestVectorRepository cache;
    @Autowired AiSettings settings;
    @Autowired ModelClient models;
    @Autowired KnowledgeService knowledge;
    @Autowired HybridSearchService search;
    @Autowired MockMvc mvc;

    static HttpServer provider() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/embed", exchange -> {
                calls++;
                var request = JSON.readTree(exchange.getRequestBody());
                if (failure != 0) { exchange.sendResponseHeaders(failure, -1); exchange.close(); return; }
                List<List<Integer>> vectors = new ArrayList<>();
                for (var text : request.path("input")) {
                    inputs++;
                    vectors.add(text.asText().contains("摄影") || text.asText().contains("镜头") ? List.of(1, 0) : List.of(0, 1));
                }
                byte[] body = JSON.writeValueAsBytes(Map.of("embeddings", vectors));
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
            });
            server.start(); return server;
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) {
        registry.add("ai.embedding.base-url", () -> "http://127.0.0.1:" + provider.getAddress().getPort());
        registry.add("ai.embedding.model", () -> "native-fixture");
    }
    @BeforeEach void reset() { cache.deleteAll(); calls = inputs = failure = 0; }
    @AfterAll static void stop() { provider.stop(0); }
    RecommendationController.Request request(String interest) {
        return new RecommendationController.Request(interest, List.of(new RecommendationController.Candidate(1L, "摄影入门"), new RecommendationController.Candidate(2L, "编程算法")));
    }
    @Test void localChatCanUseNativeRecommendationAndPersistentCache() throws Exception {
        assertThat(settings.modelEnabled()).isFalse();
        var result = recommendations.recommend(request("镜头构图"));
        assertThat(result.items().get(0).clubId()).isEqualTo(1L);
        assertThat(inputs).isEqualTo(3);
        assertThat(new RecommendationService(models, settings, cache, JSON).recommend(request("镜头构图"))).isEqualTo(result);
        assertThat(calls).isEqualTo(1);
        recommendations.recommend(request("编程兴趣"));
        assertThat(inputs).isEqualTo(4);
        mvc.perform(get("/internal/status")).andExpect(jsonPath("$.mode").value("LOCAL"))
                .andExpect(jsonPath("$.embeddingProvider").value("OLLAMA")).andExpect(jsonPath("$.retrieval").value("KEYWORD"));
    }
    @Test void localKnowledgeRemainsKeywordOnlyWithoutModelCalls() {
        var doc = knowledge.ingest("native-local.md", "摄影报名须知：请填写入社申请。".getBytes(StandardCharsets.UTF_8));
        assertThat(doc.embeddingVersion()).isEqualTo("local-keyword");
        assertThat(search.search("摄影报名").retrieval()).isEqualTo("KEYWORD");
        assertThat(calls).isZero();
    }
    @Test void nativeHttpFailureIsSanitizedAndNeverCached() throws Exception {
        failure = 401;
        mvc.perform(post("/internal/recommendations").contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(request("镜头构图"))))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.detail").value("模型接口返回 HTTP 401，请检查模型配置与额度"));
        assertThat(cache.count()).isZero();
    }
}
