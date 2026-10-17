package com.campus.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/** Synthetic HTTPS transport, never calls a cloud account. Exercises the real Spring AI adapters. */
class CloudModelSettingsTest {
    @Test void cloudChatAndEmbeddingAreSavedTestedConfirmedAndRestoredWithoutLeakingKeys() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        // Keep the mock transport when ModelClient installs its production timeout factory.
        var http = spy(builder);
        doReturn(http).when(http).clone();
        doReturn(http).when(http).requestFactory(any(ClientHttpRequestFactory.class));
        var provider = new AiSettings.Provider("http://127.0.0.1:11434", "", "");
        var settings = new AiSettings(AiSettings.Mode.LOCAL, false, provider, provider, AiSettings.EmbeddingProvider.NONE);
        var repository = mock(ModelProfileRepository.class);
        Map<String, ModelProfile> rows = new HashMap<>();
        when(repository.findById(anyString())).thenAnswer(call -> Optional.ofNullable(rows.get(call.getArgument(0))));
        when(repository.saveAndFlush(any(ModelProfile.class))).thenAnswer(call -> { ModelProfile row = call.getArgument(0); rows.put(row.purpose, row); return row; });
        var secrets = new ModelSecrets(Base64.getEncoder().encodeToString(new byte[32]));
        String base = "https://provider.example.invalid/v1";
        var policy = new ModelEndpointPolicy(base, settings);
        var models = new PlatformModels(settings, repository, secrets, policy, new ObjectMapper(), http);
        server.expect(times(2), requestTo(base + "/chat/completions"))
                .andExpect(header("Authorization", "Bearer synthetic-chat-key"))
                .andExpect(jsonPath("$.model").value("fixture-cloud-chat"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"ok\\\":true}\"}}]}", APPLICATION_JSON));
        server.expect(times(2), requestTo(base + "/embeddings"))
                .andExpect(header("Authorization", "Bearer synthetic-embedding-key"))
                .andExpect(jsonPath("$.model").value("fixture-cloud-embedding"))
                .andRespond(withSuccess("{\"data\":[{\"index\":1,\"embedding\":[0.5,1]},{\"index\":0,\"embedding\":[1,0.5]}]}", APPLICATION_JSON));
        models.save(PlatformModels.Purpose.CHAT, new PlatformModels.Input(0L, "OPENAI", "fixture", base, "fixture-cloud-chat", "synthetic-chat-key"));
        assertThat(settings.mode()).isEqualTo(AiSettings.Mode.LOCAL);
        models.test(PlatformModels.Purpose.CHAT, new PlatformModels.Revision(1L, false, false));
        assertThatThrownBy(() -> models.activate(PlatformModels.Purpose.CHAT, new PlatformModels.Revision(1L, false, false), 3)).hasMessageContaining("云端");
        models.activate(PlatformModels.Purpose.CHAT, new PlatformModels.Revision(1L, false, true), 3);
        assertThat(settings.mode()).isEqualTo(AiSettings.Mode.OPENAI);
        assertThat(settings.chat().apiKey()).isEqualTo("synthetic-chat-key");
        assertThat(settings.effectiveEmbeddingProvider()).isEqualTo(AiSettings.EmbeddingProvider.NONE);
        models.save(PlatformModels.Purpose.EMBEDDING, new PlatformModels.Input(0L, "OPENAI", "fixture", base, "fixture-cloud-embedding", "synthetic-embedding-key"));
        models.test(PlatformModels.Purpose.EMBEDDING, new PlatformModels.Revision(1L, false, false));
        models.activate(PlatformModels.Purpose.EMBEDDING, new PlatformModels.Revision(1L, true, true), 3);
        assertThat(settings.embedding().apiKey()).isEqualTo("synthetic-embedding-key");
        assertThat(rows.get("CHAT").activeJson).doesNotContain("synthetic-chat-key");
        assertThat(rows.get("EMBEDDING").activeJson).doesNotContain("synthetic-embedding-key");
        assertThat(models.view().toString()).doesNotContain("synthetic-chat-key", "synthetic-embedding-key", "encryptedKey");
        var restored = new AiSettings(AiSettings.Mode.LOCAL, false, provider, provider, AiSettings.EmbeddingProvider.NONE);
        new PlatformModels(restored, repository, secrets, policy, new ObjectMapper(), http).restore();
        assertThat(restored.mode()).isEqualTo(AiSettings.Mode.OPENAI);
        assertThat(restored.effectiveEmbeddingProvider()).isEqualTo(AiSettings.EmbeddingProvider.OPENAI);
        assertThat(restored.chat().apiKey()).isEqualTo("synthetic-chat-key");
        server.verify();
    }
}
