package com.campus.ai.config;

import java.net.URI;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Deployment-owned exact base URL allowlist. Admin forms cannot grant arbitrary network access. */
@Component
class ModelEndpointPolicy {
    private final Set<String> allowed = new LinkedHashSet<>();
    ModelEndpointPolicy(@Value("${ai-admin.allowed-base-urls:http://127.0.0.1:11434}") String configured, AiSettings bootstrap) {
        for (String value : configured.split(",")) if (!value.isBlank()) allowed.add(normalize(value));
        // Existing deployment environment is trusted, unlike browser input.
        for (var provider : List.of(bootstrap.chat(), bootstrap.embedding())) if (provider.baseUrl() != null && !provider.baseUrl().isBlank()) allowed.add(normalize(provider.baseUrl()));
    }
    List<String> allowedUrls() { return List.copyOf(allowed); }
    String require(String value, String protocol) {
        String normalized = normalize(value);
        if (!allowed.contains(normalized)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该服务地址未获部署配置允许，请部署人员设置 AI_MODEL_ALLOWED_URLS");
        if (protocol.equals("OPENAI") && !normalized.startsWith("https://")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "云端 API 必须使用 HTTPS");
        return normalized;
    }
    private String normalize(String value) {
        try {
            URI uri = URI.create(value.trim().replaceAll("/+$", ""));
            if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null || !uri.normalize().equals(uri)) throw new IllegalArgumentException();
            return uri.toASCIIString();
        } catch (Exception error) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "服务地址必须是无账号、查询参数和片段的 HTTP(S) 基础地址"); }
    }
}
