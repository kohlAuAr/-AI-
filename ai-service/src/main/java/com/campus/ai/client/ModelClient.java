package com.campus.ai.client;

import com.campus.ai.config.AiSettings;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/** Adapted from PaiSmart EmbeddingClient / DeepSeekClient. See THIRD-PARTY-NOTICES.md. */
@Component
public class ModelClient {
    private final AiSettings settings;
    private final RestClient.Builder builder;

    public ModelClient(AiSettings settings, RestClient.Builder builder) {
        this.settings = settings;
        this.builder = builder;
    }

    public String embeddingVersion() {
        return settings.modelEnabled() ? settings.embedding().baseUrl() + "/" + settings.embedding().model() : "local-keyword";
    }

    public List<double[]> embed(List<String> texts) {
        if (!settings.modelEnabled()) throw new IllegalStateException("本地模式不产生语义向量");
        List<double[]> vectors = new ArrayList<>();
        // Keep PaiSmart's bounded batch submission; parse by index rather than response order.
        for (int start = 0; start < texts.size(); start += 32) {
            List<String> batch = texts.subList(start, Math.min(start + 32, texts.size()));
            JsonNode response = client(settings.embedding()).post().uri("/embeddings")
                    .body(Map.of("model", settings.embedding().model(), "input", batch, "encoding_format", "float"))
                    .retrieve().body(JsonNode.class);
            vectors.addAll(parseEmbeddings(response, batch.size()));
        }
        return vectors;
    }

    public String chat(List<Map<String, String>> messages) {
        JsonNode response = client(settings.chat()).post().uri("/chat/completions")
                .body(Map.of("model", settings.chat().model(), "messages", messages, "temperature", 0.2, "max_tokens", 1800))
                .retrieve().body(JsonNode.class);
        String answer = response == null ? "" : response.path("choices").path(0).path("message").path("content").asText("");
        if (answer.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "模型没有返回有效回答");
        return answer;
    }

    private RestClient client(AiSettings.Provider provider) {
        if (provider.model() == null || provider.model().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "请先配置模型名称与模型接口，再启用 openai 模式");
        }
        String baseUrl = provider.baseUrl().replaceAll("/+$", "");
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(60));
        RestClient.Builder configured = builder.clone().baseUrl(baseUrl).requestFactory(factory);
        if (provider.apiKey() != null && !provider.apiKey().isBlank()) configured.defaultHeader("Authorization", "Bearer " + provider.apiKey());
        return configured.build();
    }

    static List<double[]> parseEmbeddings(JsonNode response, int expected) {
        JsonNode data = response == null ? null : response.get("data");
        if (data == null || !data.isArray() || data.size() != expected) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Embedding 返回的向量数量不正确");
        }
        double[][] ordered = new double[expected][];
        int dimension = -1;
        for (JsonNode item : data) {
            int index = item.path("index").asInt(-1);
            JsonNode embedding = item.path("embedding");
            if (index < 0 || index >= expected || ordered[index] != null || !embedding.isArray() || embedding.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Embedding 返回的索引或向量格式不正确");
            }
            if (dimension != -1 && dimension != embedding.size()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Embedding 返回的向量维度不一致");
            }
            dimension = embedding.size();
            double[] vector = new double[dimension];
            double norm = 0;
            for (int i = 0; i < dimension; i++) {
                if (!embedding.get(i).isNumber()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Embedding 返回了非数字向量");
                vector[i] = embedding.get(i).asDouble();
                if (!Double.isFinite(vector[i])) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Embedding 返回了无效向量");
                norm += vector[i] * vector[i];
            }
            if (norm == 0) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Embedding 返回了零向量");
            ordered[index] = vector;
        }
        return Arrays.asList(ordered);
    }
}
