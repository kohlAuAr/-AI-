package com.campus.ai.client;

import com.campus.ai.config.AiSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingResponse;
import static org.assertj.core.api.Assertions.*;

class ModelClientTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void embeddingIndicesAreRespectedAndBadResponsesRejected() throws Exception {
        var result = ModelClient.parseEmbeddings(new EmbeddingResponse(List.of(new Embedding(new float[]{0, 1}, 1), new Embedding(new float[]{1, 0}, 0))), 2);
        assertThat(result.get(0)).containsExactly(1, 0);
        assertThat(result.get(1)).containsExactly(0, 1);
        assertThatThrownBy(() -> ModelClient.parseEmbeddings(new EmbeddingResponse(List.of()), 1)).hasMessageContaining("数量");
        assertThatThrownBy(() -> ModelClient.parseEmbeddings(new EmbeddingResponse(List.of(new Embedding(new float[]{0, 0}, 0))), 1)).hasMessageContaining("零向量");
        assertThatThrownBy(() -> ModelClient.parseEmbeddings(new EmbeddingResponse(List.of(new Embedding(new float[]{1, 0}, 0), new Embedding(new float[]{1, 0}, 0))), 2)).hasMessageContaining("索引");
        assertThatThrownBy(() -> ModelClient.parseEmbeddings(new EmbeddingResponse(List.of(new Embedding(new float[]{Float.NaN, 0}, 0))), 1)).hasMessageContaining("无效向量");
    }

    @Test
    void compatibleProviderEndpointsAreActuallyCalled() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            var request = mapper.readTree(exchange.getRequestBody());
            byte[] body = mapper.writeValueAsBytes(Map.of("data", List.of(Map.of("index", 0, "embedding", List.of(1.0, 0.0)))));
            if (!request.path("model").asText().equals("test-model")) { exchange.sendResponseHeaders(400, -1); exchange.close(); return; }
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"依据资料 [1]\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            var provider = new AiSettings.Provider(url, "test-model", "");
            var client = new ModelClient(new AiSettings(AiSettings.Mode.OPENAI, false, provider, provider, AiSettings.EmbeddingProvider.AUTO), RestClient.builder());
            assertThat(client.embed(List.of("社团介绍")).get(0)).containsExactly(1, 0);
            assertThat(client.chat(List.of(Map.of("role", "user", "content", "测试")))).contains("[1]");
        } finally { server.stop(0); }
    }

    @Test
    void nativeOllamaUsesEmbedWithoutKeysOrPullAndKeepsBatchLimit() throws Exception {
        List<Integer> batches = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isNull();
            var request = mapper.readTree(exchange.getRequestBody());
            assertThat(request.path("model").asText()).isEqualTo("fixture-embedding");
            assertThat(request.path("truncate").asBoolean(true)).isFalse();
            batches.add(request.path("input").size());
            var vectors = IntStream.range(0, request.path("input").size()).mapToObj(i -> List.of(1, 0)).toList();
            byte[] body = mapper.writeValueAsBytes(Map.of("model", "fixture-embedding", "embeddings", vectors));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var client = ollamaClient(server);
            assertThat(paths).isEmpty();
            assertThat(client.embed(IntStream.range(0, 33).mapToObj(i -> "中文兴趣" + i).toList())).hasSize(33);
            assertThat(batches).containsExactly(32, 1);
            assertThat(paths).containsExactly("/api/embed", "/api/embed");
        } finally { server.stop(0); }
    }

    @Test
    void nativeMalformedAndInvalidVectorsAreRejected() throws Exception {
        var payload = new AtomicReference<>("{}");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/embed", exchange -> {
            byte[] body = payload.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var client = ollamaClient(server);
            for (String response : List.of("{}", "not-json", "{\"embeddings\":[]}", "{\"embeddings\":[[0,0]]}")) {
                payload.set(response);
                assertThatThrownBy(() -> client.embed(List.of("中文兴趣")))
                        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                        .satisfies(e -> assertThat(((org.springframework.web.server.ResponseStatusException) e).getStatusCode().value()).isEqualTo(502));
            }
            payload.set("{\"embeddings\":[[1,0],[1,0,0]]}");
            assertThatThrownBy(() -> client.embed(List.of("兴趣", "社团"))).hasMessageContaining("维度");
        } finally { server.stop(0); }
    }

    @Test
    void embeddingSelectionIsIndependentOfChatAndAutoPreservesLegacyBehavior() {
        var provider = new AiSettings.Provider("http://127.0.0.1:11434", "fixture", "");
        assertThat(new AiSettings(AiSettings.Mode.LOCAL, false, provider, provider, AiSettings.EmbeddingProvider.AUTO).embeddingEnabled()).isFalse();
        assertThat(new AiSettings(AiSettings.Mode.OPENAI, false, provider, provider, AiSettings.EmbeddingProvider.AUTO).effectiveEmbeddingProvider()).isEqualTo(AiSettings.EmbeddingProvider.OPENAI);
        assertThat(new AiSettings(AiSettings.Mode.OPENAI, false, provider, provider, AiSettings.EmbeddingProvider.NONE).embeddingEnabled()).isFalse();
        var nativeSettings = new AiSettings(AiSettings.Mode.LOCAL, false, provider, provider, AiSettings.EmbeddingProvider.OLLAMA);
        assertThat(nativeSettings.embeddingEnabled()).isTrue();
        assertThat(nativeSettings.modelEnabled()).isFalse();
    }

    private ModelClient ollamaClient(HttpServer server) {
        var provider = new AiSettings.Provider("http://127.0.0.1:" + server.getAddress().getPort(), "fixture-embedding", "");
        return new ModelClient(new AiSettings(AiSettings.Mode.LOCAL, false, provider, provider, AiSettings.EmbeddingProvider.OLLAMA), RestClient.builder());
    }
}
