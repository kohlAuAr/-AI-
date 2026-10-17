package com.campus.ai.config;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:platform_models;DB_CLOSE_DELAY=-1", "ai.mode=local", "ai.embedding-provider=none", "ai.redis-enabled=false",
        "ai-admin.master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext
class PlatformModelsTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final AtomicBoolean fail = new AtomicBoolean();
    static final HttpServer upstream = server();
    static String base() { return "http://127.0.0.1:" + upstream.getAddress().getPort(); }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("ai-admin.allowed-base-urls", () -> base() + ",https://api.deepseek.com/v1");
    }
    static HttpServer server() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                byte[] body;
                if (path.equals("/api/tags")) body = JSON.writeValueAsBytes(Map.of("models", List.of(Map.of("name", "fixture-chat"), Map.of("name", "fixture-embed"))));
                else if (path.equals("/api/embed")) {
                    var input = JSON.readTree(exchange.getRequestBody()).path("input");
                    body = JSON.writeValueAsBytes(Map.of("embeddings", java.util.stream.IntStream.range(0, input.size()).mapToObj(i -> List.of(1.0, .5)).toList()));
                } else body = "{\"model\":\"fixture-chat\",\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"ok\\\":true}\"},\"done\":true}".getBytes(StandardCharsets.UTF_8);
                if (fail.get()) body = "upstream error fixture-key-do-not-use".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(fail.get() ? 503 : 200, body.length); exchange.getResponseBody().write(body); exchange.close();
            });
            server.start(); return server;
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
    @AfterAll static void stop() { upstream.stop(0); }
    @Autowired MockMvc mvc;
    @Autowired AiSettings settings;
    @Autowired ModelProfileRepository profiles;
    @Autowired PlatformModels service;
    @Autowired ModelSecrets secrets;
    @Autowired ModelEndpointPolicy endpoints;
    MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.header("X-Internal-Token", "protocol-fixture-internal-token").header("X-Actor-Id", "3").header("X-Actor-Role", "PLATFORM_ADMIN");
    }
    MockHttpServletRequestBuilder operation(String purpose, String action, Object body) throws Exception {
        return admin(post("/internal/platform/models/" + purpose + "/" + action)).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsBytes(body));
    }
    Map<String, Object> input(long revision, String protocol, String url, String model, String key) {
        return Map.of("revision", revision, "protocol", protocol, "vendor", "fixture", "baseUrl", url, "model", model, "apiKey", key);
    }
    @Test void savedTestedActivatedSettingsAreEncryptedScopedVersionedAndRestorable() throws Exception {
        mvc.perform(get("/internal/platform/models")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/platform/models").header("X-Internal-Token", "protocol-fixture-internal-token")).andExpect(status().isForbidden());
        for (String role : List.of("STUDENT", "MANAGER")) mvc.perform(get("/internal/platform/models").header("X-Internal-Token", "protocol-fixture-internal-token").header("X-Actor-Id", "1").header("X-Actor-Role", role)).andExpect(status().isForbidden());
        mvc.perform(admin(get("/internal/platform/models"))).andExpect(status().isOk()).andExpect(jsonPath("$.secretStorageReady").value(true));
        mvc.perform(admin(post("/internal/platform/models/discover")).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsBytes(Map.of("baseUrl", base())))).andExpect(status().isOk()).andExpect(jsonPath("$.models[0]").value("fixture-chat"));
        mvc.perform(operation("CHAT", "save", input(0, "OLLAMA", "http://169.254.169.254", "fixture-chat", ""))).andExpect(status().isBadRequest());
        mvc.perform(operation("CHAT", "save", input(0, "OLLAMA", base() + "?token=secret", "fixture-chat", ""))).andExpect(status().isBadRequest());
        mvc.perform(operation("CHAT", "save", input(0, "OPENAI", base(), "fixture-chat", "fixture-key-do-not-use"))).andExpect(status().isBadRequest());
        mvc.perform(operation("CHAT", "save", input(0, "OLLAMA", base(), "fixture-chat", "fixture-key-do-not-use"))).andExpect(status().isOk()).andExpect(jsonPath("$.draft.keyConfigured").value(true)).andExpect(jsonPath("$.active.protocol").value("NONE"));
        assertThat(settings.mode()).isEqualTo(AiSettings.Mode.LOCAL);
        assertThat(profiles.findById("CHAT").orElseThrow().draftJson).doesNotContain("fixture-key-do-not-use");
        mvc.perform(operation("CHAT", "save", input(0, "OLLAMA", base(), "fixture-chat", ""))).andExpect(status().isConflict());
        mvc.perform(operation("CHAT", "activate", Map.of("revision", 1))).andExpect(status().isBadRequest());
        mvc.perform(operation("CHAT", "test", Map.of("revision", 1))).andExpect(status().isOk());
        mvc.perform(operation("CHAT", "activate", Map.of("revision", 1))).andExpect(status().isOk());
        assertThat(settings.mode()).isEqualTo(AiSettings.Mode.OLLAMA);
        assertThat(settings.effectiveEmbeddingProvider()).isEqualTo(AiSettings.EmbeddingProvider.NONE);
        assertThat(settings.chat().apiKey()).isEqualTo("fixture-key-do-not-use");
        mvc.perform(operation("CHAT", "save", input(1, "OLLAMA", base(), "fixture-chat-v2", ""))).andExpect(status().isOk());
        mvc.perform(operation("CHAT", "test", Map.of("revision", 2))).andExpect(status().isOk());
        fail.set(true);
        String failure = mvc.perform(operation("CHAT", "activate", Map.of("revision", 2))).andExpect(status().isBadGateway()).andReturn().getResponse().getContentAsString();
        assertThat(failure).doesNotContain("fixture-key-do-not-use"); assertThat(settings.chat().model()).isEqualTo("fixture-chat");
        fail.set(false);
        mvc.perform(operation("EMBEDDING", "save", input(0, "OLLAMA", base(), "fixture-embed", ""))).andExpect(status().isOk());
        mvc.perform(operation("EMBEDDING", "test", Map.of("revision", 1))).andExpect(status().isOk()).andExpect(jsonPath("$.dimension").value(2));
        mvc.perform(operation("EMBEDDING", "activate", Map.of("revision", 1))).andExpect(status().isBadRequest());
        mvc.perform(operation("EMBEDDING", "activate", Map.of("revision", 1, "acknowledgeReindex", true))).andExpect(status().isOk());
        assertThat(settings.embedding().model()).isEqualTo("fixture-embed");
        mvc.perform(operation("CHAT", "save", input(2, "OPENAI", "https://api.deepseek.com/v1", "fixture-cloud", ""))).andExpect(status().isBadRequest());
        mvc.perform(operation("CHAT", "save", input(2, "OPENAI", "https://api.deepseek.com/v1", "fixture-cloud", "fixture-new-cloud-key"))).andExpect(status().isOk());
        var response = mvc.perform(admin(get("/internal/platform/models"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("fixture-key-do-not-use", "fixture-new-cloud-key", "encryptedKey");
        assertThat(profiles.findById("CHAT").orElseThrow().draftJson).doesNotContain("fixture-new-cloud-key");
        // Recreate the runtime from the same durable rows; unswitched cloud draft is not activated.
        var bootstrap = new AiSettings(AiSettings.Mode.LOCAL, false, new AiSettings.Provider(base(), "", ""), new AiSettings.Provider(base(), "", ""), AiSettings.EmbeddingProvider.NONE);
        new PlatformModels(bootstrap, profiles, secrets, endpoints, JSON, RestClient.builder()).restore();
        assertThat(bootstrap.chat().model()).isEqualTo("fixture-chat"); assertThat(bootstrap.embedding().model()).isEqualTo("fixture-embed");
    }
    @Test void requestSnapshotsAndMasterKeysDoNotLeakOrChangeMidRequest() {
        var provider = new AiSettings.Provider(base(), "old", "fixture-secret");
        var current = new AiSettings(AiSettings.Mode.OLLAMA, false, provider, provider, AiSettings.EmbeddingProvider.OLLAMA);
        current.pin();
        try {
            current.activate(new AiSettings(AiSettings.Mode.OPENAI, false, new AiSettings.Provider("https://example.com/v1", "new", "new-secret"), provider, AiSettings.EmbeddingProvider.OLLAMA));
            assertThat(current.mode()).isEqualTo(AiSettings.Mode.OLLAMA); assertThat(current.chat().model()).isEqualTo("old");
        } finally { current.unpin(); }
        assertThat(current.mode()).isEqualTo(AiSettings.Mode.OPENAI); assertThat(current.chat().model()).isEqualTo("new");
        assertThat(provider.toString()).doesNotContain("fixture-secret");
        String first = secrets.encrypt("fixture-secret"), second = secrets.encrypt("fixture-secret");
        assertThat(first).isNotEqualTo(second); assertThat(secrets.decrypt(first)).isEqualTo("fixture-secret");
        assertThatThrownBy(() -> new ModelSecrets("").encrypt("fixture-secret")).hasMessageContaining("不能明文");
        assertThatThrownBy(() -> new ModelSecrets(Base64.getEncoder().encodeToString(new byte[32])).decrypt(first + "tampered")).hasMessageContaining("解密");
    }
}
