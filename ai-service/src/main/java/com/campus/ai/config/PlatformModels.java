package com.campus.ai.config;

import com.campus.ai.client.ModelClient;
import com.campus.ai.client.ModelCallGate;
import com.fasterxml.jackson.databind.*;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.*;
import java.net.http.HttpClient;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

/** Single AI instance administration. Durable save first, then atomically publish a complete snapshot. */
@Service
class PlatformModels {
    enum Purpose { CHAT, EMBEDDING }
    record Stored(String protocol, String vendor, String baseUrl, String model, String encryptedKey) {}
    record Input(@NotNull Long revision, @NotBlank @Pattern(regexp = "NONE|OLLAMA|OPENAI") String protocol,
                 @NotBlank @Size(max = 40) String vendor, @NotNull @Size(max = 400) String baseUrl,
                 @NotNull @Size(max = 120) String model, @Size(max = 4096) String apiKey) {
        @Override public String toString() { return "Input[credentials=REDACTED]"; }
    }
    record Revision(@NotNull Long revision, boolean acknowledgeReindex, boolean acknowledgeCloud) {}
    private final AiSettings settings, bootstrap;
    private final ModelProfileRepository profiles;
    private final ModelSecrets secrets;
    private final ModelEndpointPolicy endpoints;
    private final ObjectMapper mapper;
    private final RestClient.Builder http;
    private final ModelCallGate gate;

    PlatformModels(AiSettings settings, ModelProfileRepository profiles, ModelSecrets secrets, ModelEndpointPolicy endpoints, ObjectMapper mapper, RestClient.Builder http, ModelCallGate gate) {
        this.settings = settings;
        this.bootstrap = new AiSettings(settings.mode(), settings.redisEnabled(), settings.chat(), settings.embedding(), settings.effectiveEmbeddingProvider());
        this.profiles = profiles; this.secrets = secrets; this.endpoints = endpoints; this.mapper = mapper; this.http = http;
        this.gate = gate;
    }
    @PostConstruct void restore() { publish(); }
    synchronized Map<String, Object> view() {
        return Map.of("profiles", Arrays.stream(Purpose.values()).map(this::view).toList(), "allowedBaseUrls", endpoints.allowedUrls(), "secretStorageReady", secrets.configured());
    }
    private Map<String, Object> view(Purpose purpose) {
        ModelProfile row = profiles.findById(purpose.name()).orElse(new ModelProfile(purpose.name()));
        var result = new LinkedHashMap<String, Object>();
        result.put("purpose", purpose); result.put("revision", row.revision); result.put("saved", row.draftJson != null);
        result.put("tested", row.testedRevision != null && row.testedRevision == row.revision);
        result.put("draft", safe(draft(row, purpose), purpose)); result.put("active", safe(active(row, purpose), purpose));
        result.put("activatedAt", row.activatedAt); result.put("activatedBy", row.activatedBy);
        return result;
    }
    synchronized Map<String, Object> save(Purpose purpose, Input input) {
        ModelProfile row = profiles.findById(purpose.name()).orElse(new ModelProfile(purpose.name()));
        revision(row, input.revision());
        String protocol = input.protocol(), base = "", model = input.model().trim(), encrypted = "";
        if (!protocol.equals("NONE")) {
            base = endpoints.require(input.baseUrl(), protocol);
            if (!model.matches("[A-Za-z0-9][A-Za-z0-9._:/@+\\-]{0,119}")) throw bad("请填写服务提供的模型 ID，不能包含空白或控制字符");
            Stored previous = draft(row, purpose);
            if (input.apiKey() != null && !input.apiKey().isBlank()) encrypted = secrets.encrypt(input.apiKey().trim());
            else if (previous.protocol().equals(protocol) && previous.baseUrl().equals(base)) {
                encrypted = previous.encryptedKey();
                if (encrypted.isBlank()) encrypted = secrets.encrypt(provider(previous, purpose).apiKey());
            }
            if (protocol.equals("OPENAI") && encrypted.isBlank()) throw bad("云端 API 需要填写密钥；更换服务地址后必须重新填写");
        } else model = "";
        row.draftJson = json(new Stored(protocol, input.vendor().trim(), base, model, encrypted));
        row.revision++; row.testedRevision = null;
        profiles.saveAndFlush(row);
        return view(purpose);
    }
    synchronized Map<String, Object> test(Purpose purpose, Revision input) {
        ModelProfile row = saved(purpose); revision(row, input.revision());
        // Invalidate an older successful test before attempting again, including when upstream is now offline.
        row.testedRevision = null; profiles.saveAndFlush(row);
        var result = probe(purpose, draft(row, purpose));
        row.testedRevision = row.revision; profiles.saveAndFlush(row);
        return result;
    }
    synchronized Map<String, Object> activate(Purpose purpose, Revision input, long actor) {
        ModelProfile row = saved(purpose); revision(row, input.revision());
        if (row.testedRevision == null || row.testedRevision != row.revision) throw bad("请先测试当前已保存的配置");
        Stored next = draft(row, purpose);
        if (next.protocol().equals("OPENAI") && !input.acknowledgeCloud()) throw bad("请确认启用后相关问题和检索内容会发送给云端服务商");
        if (purpose == Purpose.EMBEDDING && !sameVectorSpace(active(row, purpose), next) && !input.acknowledgeReindex()) throw bad("请确认旧资料向量不会自动重建，旧模型向量不能混用");
        // Re-check before publication: failed activation never replaces the working configuration.
        row.testedRevision = null; profiles.saveAndFlush(row);
        probe(purpose, next);
        row.activeJson = row.draftJson; row.testedRevision = row.revision; row.activatedAt = Instant.now(); row.activatedBy = actor;
        profiles.saveAndFlush(row); publish();
        return view(purpose);
    }
    synchronized Map<String, Object> discover(String baseUrl) {
        String base = endpoints.require(baseUrl, "OLLAMA");
        try {
            JsonNode response = client().get().uri(base + "/api/tags").retrieve().body(JsonNode.class);
            if (response == null || !response.path("models").isArray()) throw new IllegalArgumentException();
            List<String> names = new ArrayList<>();
            for (JsonNode model : response.path("models")) {
                String name = model.path("name").asText();
                if (!name.isBlank() && name.length() <= 120 && names.size() < 100) names.add(name);
            }
            return Map.of("models", names, "notice", "这是已安装模型列表，不代表已加载或通过推理测试；平台不会自动下载模型。");
        } catch (Exception error) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "无法读取 Ollama 模型列表，请检查服务地址、运行状态和内网访问权限"); }
    }
    private Map<String, Object> probe(Purpose purpose, Stored stored) {
        if (stored.protocol().equals("NONE")) return Map.of("ok", true, "notice", "关闭配置有效，启用后将关闭该模型能力；不是本地大模型。");
        try {
            var provider = provider(stored, purpose);
            var candidate = new AiSettings(stored.protocol().equals("OLLAMA") ? AiSettings.Mode.OLLAMA : AiSettings.Mode.OPENAI, false, provider, provider,
                    stored.protocol().equals("OLLAMA") ? AiSettings.EmbeddingProvider.OLLAMA : AiSettings.EmbeddingProvider.OPENAI);
            var model = new ModelClient(candidate, http, gate);
            if (purpose == Purpose.EMBEDDING) {
                var vectors = model.embed(List.of("校园社团连接测试", "摄影与编程兴趣"));
                return Map.of("ok", true, "dimension", vectors.get(0).length, "notice", "向量接口测试通过；尚未代表推荐质量验收。");
            }
            String reply = model.chat(List.of(Map.of("role", "system", "content", "只返回 JSON：{\"ok\":true}，不要解释。"), Map.of("role", "user", "content", "连接测试")));
            JsonNode result = mapper.readTree(reply.trim().replaceAll("^```(?:json)?\\s*|\\s*```$", ""));
            if (!result.path("ok").asBoolean()) throw new IllegalArgumentException();
            return Map.of("ok", true, "notice", "聊天与基础 JSON 响应测试通过；不代表完整 Agent 质量验收。");
        } catch (Exception error) {
            // Never echo provider error bodies, URLs with credentials, API keys or model output to the browser.
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "模型测试失败，请检查地址、密钥、模型能力或运行资源；当前生效配置未改变");
        }
    }
    private RestClient client() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        return http.clone().requestFactory(factory).build();
    }
    private void publish() {
        var chat = active(profiles.findById("CHAT").orElse(new ModelProfile("CHAT")), Purpose.CHAT);
        var embedding = active(profiles.findById("EMBEDDING").orElse(new ModelProfile("EMBEDDING")), Purpose.EMBEDDING);
        settings.activate(new AiSettings(chat.protocol().equals("NONE") ? AiSettings.Mode.LOCAL : AiSettings.Mode.valueOf(chat.protocol()), bootstrap.redisEnabled(),
                provider(chat, Purpose.CHAT), provider(embedding, Purpose.EMBEDDING), embedding.protocol().equals("NONE") ? AiSettings.EmbeddingProvider.NONE : AiSettings.EmbeddingProvider.valueOf(embedding.protocol())));
    }
    private Stored draft(ModelProfile row, Purpose purpose) { return row.draftJson == null ? active(row, purpose) : read(row.draftJson); }
    private Stored active(ModelProfile row, Purpose purpose) {
        if (row.activeJson != null) return read(row.activeJson);
        var provider = purpose == Purpose.CHAT ? bootstrap.chat() : bootstrap.embedding();
        String protocol = purpose == Purpose.CHAT ? bootstrap.mode().name() : bootstrap.effectiveEmbeddingProvider().name();
        if (protocol.equals("LOCAL")) protocol = "NONE";
        // Environment credentials remain only in environment; never imported into database by merely opening the page.
        return new Stored(protocol, "部署配置", Objects.toString(provider.baseUrl(), ""), Objects.toString(provider.model(), ""), "");
    }
    private AiSettings.Provider provider(Stored stored, Purpose purpose) {
        if (stored.vendor().equals("部署配置") && stored.encryptedKey().isBlank()) {
            var value = purpose == Purpose.CHAT ? bootstrap.chat() : bootstrap.embedding();
            if (Objects.equals(value.baseUrl(), stored.baseUrl()) && Objects.equals(value.model(), stored.model())) return value;
        }
        return new AiSettings.Provider(stored.baseUrl(), stored.model(), secrets.decrypt(stored.encryptedKey()));
    }
    private Map<String, Object> safe(Stored stored, Purpose purpose) {
        boolean configured = !stored.encryptedKey().isBlank() || !Objects.toString(provider(stored, purpose).apiKey(), "").isBlank();
        return Map.of("protocol", stored.protocol(), "vendor", stored.vendor(), "baseUrl", stored.baseUrl(), "model", stored.model(), "keyConfigured", configured);
    }
    private boolean sameVectorSpace(Stored left, Stored right) { return left.protocol().equals(right.protocol()) && left.baseUrl().equals(right.baseUrl()) && left.model().equals(right.model()); }
    private ModelProfile saved(Purpose purpose) { return profiles.findById(purpose.name()).filter(row -> row.draftJson != null).orElseThrow(() -> bad("请先保存待启用配置")); }
    private void revision(ModelProfile row, Long expected) { if (expected == null || row.revision != expected) throw new ResponseStatusException(HttpStatus.CONFLICT, "配置已被更新，请重新读取后操作"); }
    private Stored read(String value) { try { return mapper.readValue(value, Stored.class); } catch (Exception error) { throw new IllegalStateException("模型配置无法读取"); } }
    private String json(Stored value) { try { return mapper.writeValueAsString(value); } catch (Exception error) { throw new IllegalStateException("模型配置无法保存"); } }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
