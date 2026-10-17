package com.campus.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties("ai")
public final class AiSettings {
    public enum Mode { LOCAL, OPENAI, OLLAMA }
    public enum EmbeddingProvider { AUTO, NONE, OPENAI, OLLAMA }
    public record Provider(String baseUrl, String model, String apiKey) {
        @Override public String toString() { return "Provider[model=" + model + ", apiKey=REDACTED]"; }
    }
    private final Mode mode;
    private final boolean redisEnabled;
    private final Provider chat, embedding;
    private final EmbeddingProvider embeddingProvider;
    private volatile AiSettings active;
    private final ThreadLocal<AiSettings> requestSnapshot = new ThreadLocal<>();
    @ConstructorBinding
    public AiSettings(Mode mode, boolean redisEnabled, Provider chat, Provider embedding, EmbeddingProvider embeddingProvider) {
        this.mode = mode; this.redisEnabled = redisEnabled; this.chat = chat; this.embedding = embedding; this.embeddingProvider = embeddingProvider;
    }
    private AiSettings source() { var pinned = requestSnapshot.get(); return pinned == null ? latest() : pinned; }
    public AiSettings latest() { var value = active; return value == null ? this : value; }
    void activate(AiSettings value) { active = value; }
    void pin() { requestSnapshot.set(latest()); }
    void unpin() { requestSnapshot.remove(); }
    public Mode mode() { return source().mode; }
    public boolean redisEnabled() { return redisEnabled; }
    public Provider chat() { return source().chat; }
    public Provider embedding() { return source().embedding; }
    public EmbeddingProvider embeddingProvider() { return source().embeddingProvider; }
    public boolean modelEnabled() { return mode() == Mode.OPENAI || mode() == Mode.OLLAMA; }
    public EmbeddingProvider effectiveEmbeddingProvider() {
        var provider = embeddingProvider();
        return provider == null || provider == EmbeddingProvider.AUTO
                ? switch (mode()) {
                    case LOCAL -> EmbeddingProvider.NONE;
                    case OPENAI -> EmbeddingProvider.OPENAI;
                    case OLLAMA -> EmbeddingProvider.OLLAMA;
                } : provider;
    }
    public boolean embeddingEnabled() {
        return effectiveEmbeddingProvider() != EmbeddingProvider.NONE && embedding().model() != null && !embedding().model().isBlank();
    }
}
