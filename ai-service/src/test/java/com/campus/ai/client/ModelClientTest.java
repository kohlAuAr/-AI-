package com.campus.ai.client;

import com.campus.ai.config.AiSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;

class ModelClientTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void embeddingIndicesAreRespectedAndBadResponsesRejected() throws Exception {
        var result = ModelClient.parseEmbeddings(mapper.readTree("{\"data\":[{\"index\":1,\"embedding\":[0,1]},{\"index\":0,\"embedding\":[1,0]}]}"), 2);
        assertThat(result.get(0)).containsExactly(1, 0);
        assertThat(result.get(1)).containsExactly(0, 1);
        assertThatThrownBy(() -> ModelClient.parseEmbeddings(mapper.readTree("{\"data\":[]}"), 1)).hasMessageContaining("数量");
        assertThatThrownBy(() -> ModelClient.parseEmbeddings(mapper.readTree("{\"data\":[{\"index\":0,\"embedding\":[0,0]}]}"), 1)).hasMessageContaining("零向量");
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
            var client = new ModelClient(new AiSettings(AiSettings.Mode.OPENAI, false, provider, provider), RestClient.builder());
            assertThat(client.embed(List.of("社团介绍")).get(0)).containsExactly(1, 0);
            assertThat(client.chat(List.of(Map.of("role", "user", "content", "测试")))).contains("[1]");
        } finally { server.stop(0); }
    }
}
