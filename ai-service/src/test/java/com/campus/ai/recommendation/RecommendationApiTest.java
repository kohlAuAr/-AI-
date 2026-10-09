package com.campus.ai.recommendation;

import com.campus.ai.client.ModelClient;
import com.campus.ai.config.AiSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Protocol fixture vectors test wiring and cache behavior, not real recommendation quality. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:recommendations;DB_CLOSE_DELAY=-1", "ai.mode=openai", "ai.redis-enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecommendationApiTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static int calls, inputCount, chatCalls, failure;
    static boolean wrongDimension;
    static final HttpServer provider = provider();
    @Autowired RecommendationService recommendations;
    @Autowired InterestVectorRepository cache;
    @Autowired MockMvc mvc;
    @Autowired AiSettings settings;
    @MockitoSpyBean ModelClient models;

    static HttpServer provider() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/embeddings", exchange -> {
                calls++;
                var request = JSON.readTree(exchange.getRequestBody());
                assertThat(request.path("model").asText()).isEqualTo("fixture-embedding");
                if (failure != 0) { exchange.sendResponseHeaders(failure, -1); exchange.close(); return; }
                List<Map<String, Object>> data = new ArrayList<>();
                int index = 0;
                for (var text : request.path("input")) {
                    inputCount++;
                    List<Integer> vector = text.asText().contains("摄影") || text.asText().contains("镜头") ? List.of(1, 0) : List.of(0, 1);
                    data.add(Map.of("index", index++, "embedding", wrongDimension ? List.of(1, 0, 0) : vector));
                }
                Collections.reverse(data);
                byte[] body = JSON.writeValueAsBytes(Map.of("data", data));
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
            });
            server.createContext("/v1/chat/completions", exchange -> { chatCalls++; exchange.sendResponseHeaders(500, -1); exchange.close(); });
            server.start(); return server;
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("ai.embedding.base-url", () -> "http://127.0.0.1:" + provider.getAddress().getPort() + "/v1");
        registry.add("ai.embedding.model", () -> "fixture-embedding");
    }
    @BeforeEach void reset() { cache.deleteAll(); calls = inputCount = chatCalls = failure = 0; wrongDimension = false; }
    @AfterAll static void stop() { provider.stop(0); }
    RecommendationController.Request request(String interest, String photo) {
        return new RecommendationController.Request(interest, List.of(new RecommendationController.Candidate(20L, "编程竞赛"), new RecommendationController.Candidate(10L, photo)));
    }
    @Test void semanticRankingWithoutChatOrKeywordOverlap() {
        var result = recommendations.recommend(request("想学习镜头构图", "摄影入门"));
        assertThat(result.method()).isEqualTo("SEMANTIC_COSINE");
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).clubId()).isEqualTo(10L);
        assertThat(result.items().get(0).score()).isEqualTo(1);
        assertThat(chatCalls).isZero();
    }
    @Test void unchangedInputsReusePersistentVectorsAndEditsOnlyEncodeChangedText() {
        recommendations.recommend(request("镜头构图", "摄影入门"));
        assertThat(inputCount).isEqualTo(3);
        recommendations.recommend(request("镜头构图", "摄影入门"));
        assertThat(calls).isEqualTo(1);
        new RecommendationService(models, settings, cache, JSON).recommend(request("镜头构图", "摄影入门"));
        assertThat(calls).isEqualTo(1);
        recommendations.recommend(request("喜欢编程", "摄影入门"));
        assertThat(inputCount).isEqualTo(4);
        recommendations.recommend(request("喜欢编程", "摄影校园采风"));
        assertThat(inputCount).isEqualTo(5);
        assertThat(cache.count()).isEqualTo(5);
    }
    @Test void modelVersionChangeDoesNotReuseOldVectorSpace() {
        recommendations.recommend(request("镜头构图", "摄影入门"));
        doReturn("fixture-version-2").when(models).embeddingVersion();
        recommendations.recommend(request("镜头构图", "摄影入门"));
        assertThat(inputCount).isEqualTo(6);
    }
    @Test void changedDimensionFailsInsteadOfInventingZeroSimilarity() {
        recommendations.recommend(request("镜头构图", "摄影入门"));
        wrongDimension = true;
        assertThatThrownBy(() -> recommendations.recommend(request("镜头新兴趣", "摄影入门"))).hasMessageContaining("维度");
        assertThat(cache.count()).isEqualTo(3);
    }
    @Test void providerErrorLeavesNoPartialCache() throws Exception {
        failure = 401;
        mvc.perform(post("/internal/recommendations").contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(request("镜头构图", "摄影入门"))))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.detail").value("模型接口返回 HTTP 401，请检查模型配置与额度"));
        assertThat(cache.count()).isZero();
        failure = 0;
        assertThat(recommendations.recommend(request("镜头构图", "摄影入门")).items()).hasSize(1);
    }
    @Test void invalidInterestIsRejectedBeforeModelCall() throws Exception {
        for (String interest : List.of("   ", "长".repeat(1001))) {
            mvc.perform(post("/internal/recommendations").contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(request(interest, "摄影入门"))))
                    .andExpect(status().isBadRequest());
        }
        assertThat(calls).isZero();
    }
    @Test void localModeNeverPretendsToGenerateVectors() {
        var local = new AiSettings(AiSettings.Mode.LOCAL, false, settings.chat(), settings.embedding(), AiSettings.EmbeddingProvider.AUTO);
        var service = new RecommendationService(models, local, cache, JSON);
        assertThatThrownBy(() -> service.recommend(request("镜头构图", "摄影入门"))).hasMessageContaining("尚未配置");
        assertThat(calls).isZero();
    }
}
