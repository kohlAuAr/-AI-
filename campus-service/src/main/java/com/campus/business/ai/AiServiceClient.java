package com.campus.business.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class AiServiceClient {
    private final RestClient client;

    public AiServiceClient(RestClient.Builder builder, @Value("${campus.ai-base-url}") String baseUrl, @Value("${service.internal-token:}") String token) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(300));
        client = builder.baseUrl(baseUrl).defaultHeader("X-Internal-Token", token).requestFactory(factory).build();
    }

    public JsonNode get(String path) {
        return client.get().uri("/internal/" + path).retrieve().body(JsonNode.class);
    }
    public JsonNode get(String path, AiIdentity.Actor actor) {
        return client.get().uri("/internal/" + path).headers(headers -> actorHeaders(headers, actor)).retrieve().body(JsonNode.class);
    }
    public JsonNode ask(String path, Map<String, Object> body, AiIdentity.Actor actor) {
        return client.post().uri("/internal/" + path).headers(headers -> actorHeaders(headers, actor)).body(body).retrieve().body(JsonNode.class);
    }
    private void actorHeaders(HttpHeaders headers, AiIdentity.Actor actor) {
        headers.set("X-Actor-Id", actor.id().toString()); headers.set("X-Actor-Role", actor.role());
        headers.set("X-Actor-Clubs", actor.clubs().stream().map(Object::toString).collect(java.util.stream.Collectors.joining(",")));
        headers.set("X-Actor-Managed-Clubs", actor.managedClubs().stream().map(Object::toString).collect(java.util.stream.Collectors.joining(",")));
    }

    public JsonNode recommend(Map<String, Object> body) {
        return client.post().uri("/internal/recommendations").body(body).retrieve().body(JsonNode.class);
    }

    public JsonNode upload(String filename, byte[] bytes, String visibility, Long clubId, AiIdentity.Actor actor) {
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new ByteArrayResource(bytes) {
            @Override public String getFilename() { return filename; }
        });
        body.add("visibility", visibility); if (clubId != null) body.add("clubId", clubId.toString());
        return client.post().uri("/internal/knowledge").contentType(MediaType.MULTIPART_FORM_DATA)
                .headers(headers -> actorHeaders(headers, actor)).body(body).retrieve().body(JsonNode.class);
    }
}
