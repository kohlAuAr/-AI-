package com.campus.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ai")
public record AiSettings(Mode mode, boolean redisEnabled, Provider chat, Provider embedding, EmbeddingProvider embeddingProvider) {
    public enum Mode { LOCAL, OPENAI }
    public enum EmbeddingProvider { AUTO, NONE, OPENAI, OLLAMA }
    public record Provider(String baseUrl, String model, String apiKey) {}
    public boolean modelEnabled() { return mode == Mode.OPENAI; }
    public EmbeddingProvider effectiveEmbeddingProvider() {
        return embeddingProvider == null || embeddingProvider == EmbeddingProvider.AUTO
                ? (modelEnabled() ? EmbeddingProvider.OPENAI : EmbeddingProvider.NONE) : embeddingProvider;
    }
    public boolean embeddingEnabled() {
        return effectiveEmbeddingProvider() != EmbeddingProvider.NONE && embedding.model() != null && !embedding.model().isBlank();
    }
}
