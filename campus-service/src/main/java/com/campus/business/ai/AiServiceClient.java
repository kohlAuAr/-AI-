package com.campus.business.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class AiServiceClient {
    private final RestClient client;

    public AiServiceClient(RestClient.Builder builder, @Value("${campus.ai-base-url}") String baseUrl) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(90));
        client = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    public JsonNode get(String path) {
        return client.get().uri("/internal/" + path).retrieve().body(JsonNode.class);
    }

    public JsonNode chat(Map<String, Object> body) {
        return client.post().uri("/internal/chat").body(body).retrieve().body(JsonNode.class);
    }

    public JsonNode recommend(Map<String, Object> body) {
        return client.post().uri("/internal/recommendations").body(body).retrieve().body(JsonNode.class);
    }

    public JsonNode upload(String filename, byte[] bytes) {
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new ByteArrayResource(bytes) {
            @Override public String getFilename() { return filename; }
        });
        return client.post().uri("/internal/knowledge").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body).retrieve().body(JsonNode.class);
    }
}
