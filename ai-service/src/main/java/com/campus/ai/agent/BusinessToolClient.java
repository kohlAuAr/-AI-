package com.campus.ai.agent;

import com.campus.ai.security.AiActor;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class BusinessToolClient {
    private final RestClient client;
    public BusinessToolClient(RestClient.Builder builder, @Value("${ai.campus-base-url:http://127.0.0.1:8090}") String url, @Value("${service.internal-token:}") String token) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()); factory.setReadTimeout(Duration.ofSeconds(8));
        client = builder.clone().baseUrl(url).defaultHeader("X-Internal-Token", token).requestFactory(factory).build();
    }
    public JsonNode call(AiActor actor, String tool, String query, Long id) {
        return client.post().uri("/internal/ai/tools").body(Map.of("actorId", actor.userId(), "tool", tool, "query", query, "id", id == null ? 0 : id)).retrieve().body(JsonNode.class);
    }
}
