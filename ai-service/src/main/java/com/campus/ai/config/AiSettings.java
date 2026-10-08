package com.campus.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ai")
public record AiSettings(Mode mode, boolean redisEnabled, Provider chat, Provider embedding) {
    public enum Mode { LOCAL, OPENAI }
    public record Provider(String baseUrl, String model, String apiKey) {}
    public boolean modelEnabled() { return mode == Mode.OPENAI; }
}
